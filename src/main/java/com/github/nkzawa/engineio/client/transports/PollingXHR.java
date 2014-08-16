package com.github.nkzawa.engineio.client.transports;


import com.github.nkzawa.emitter.Emitter;
import com.github.nkzawa.thread.EventThread;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Logger;

public class PollingXHR extends Polling {

    private static final Logger logger = Logger.getLogger(PollingXHR.class.getName());

    private Request sendXhr;
    private Request pollXhr;

    public PollingXHR(Options opts) {
        super(opts);
    }

    protected Request request() {
        return this.request(null);
    }

    protected Request request(Request.Options opts) {
        if (opts == null) {
            opts = new Request.Options();
        }
        opts.uri = this.uri();
        opts.sslContext = this.sslContext;

        Request req = new Request(opts);

        final PollingXHR self = this;
        req.on(Request.EVENT_REQUEST_HEADERS, new Listener() {
            @Override
            public void call(Object... args) {
                // Never execute asynchronously for support to modify headers.
                self.emit(EVENT_REQUEST_HEADERS, args[0]);
            }
        }).on(Request.EVENT_RESPONSE_HEADERS, new Listener() {
            @Override
            public void call(final Object... args) {
                EventThread.exec(new Runnable() {
                    @Override
                    public void run() {
                        self.emit(EVENT_RESPONSE_HEADERS, args[0]);
                    }
                });
            }
        });
        return req;
    }

    @Override
    protected void doWrite(byte[] data, final Runnable fn) {
        Request.Options opts = new Request.Options();
        opts.method = "POST";
        opts.data = data;
        Request req = this.request(opts);
        final PollingXHR self = this;
        req.on(Request.EVENT_SUCCESS, new Listener() {
            @Override
            public void call(Object... args) {
                EventThread.exec(new Runnable() {
                    @Override
                    public void run() {
                        fn.run();
                    }
                });
            }
        });
        req.on(Request.EVENT_ERROR, new Listener() {
            @Override
            public void call(final Object... args) {
                EventThread.exec(new Runnable() {
                    @Override
                    public void run() {
                        Exception err = args.length > 0 && args[0] instanceof Exception ? (Exception)args[0] : null;
                        self.onError("xhr post error", err);
                    }
                });
            }
        });
        req.create();
        this.sendXhr = req;
    }

    @Override
    protected void doPoll() {
        logger.fine("xhr poll");
        Request req = this.request();
        final PollingXHR self = this;
        req.on(Request.EVENT_DATA, new Listener() {
            @Override
            public void call(final Object... args) {
                EventThread.exec(new Runnable() {
                    @Override
                    public void run() {
                        Object arg = args.length > 0 ? args[0] : null;
                        if (arg instanceof String) {
                            self.onData((String)arg);
                        } else if (arg instanceof byte[]) {
                            self.onData((byte[])arg);
                        }
                    }
                });
            }
        });
        req.on(Request.EVENT_ERROR, new Listener() {
            @Override
            public void call(final Object... args) {
                EventThread.exec(new Runnable() {
                    @Override
                    public void run() {
                        Exception err = args.length > 0 && args[0] instanceof Exception ? (Exception) args[0] : null;
                        self.onError("xhr poll error", err);
                    }
                });
            }
        });
        req.create();
        this.pollXhr = req;
    }

    public static class Request extends Emitter {

        public static final String EVENT_SUCCESS = "success";
        public static final String EVENT_DATA = "data";
        public static final String EVENT_ERROR = "error";
        public static final String EVENT_REQUEST_HEADERS = "requestHeaders";
        public static final String EVENT_RESPONSE_HEADERS = "responseHeaders";

        private String method;
        private String uri;
        private byte[] data;
        private SSLContext sslContext;
        private HttpURLConnection xhr;

        public Request(Options opts) {
            this.method = opts.method != null ? opts.method : "GET";
            this.uri = opts.uri;
            this.data = opts.data;
            this.sslContext = opts.sslContext;
        }

        public void create() {
            final Request self = this;
            try {
                logger.fine(String.format("xhr open %s: %s", this.method, this.uri));
                URL url = new URL(this.uri);
                xhr = (HttpURLConnection)url.openConnection();
                xhr.setRequestMethod(this.method);
            } catch (IOException e) {
                this.onError(e);
                return;
            }

            if (xhr instanceof HttpsURLConnection && this.sslContext != null) {
                ((HttpsURLConnection)xhr).setSSLSocketFactory(this.sslContext.getSocketFactory());
            }

            Map<String, String> headers = new TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER);

            if ("POST".equals(this.method)) {
                xhr.setDoOutput(true);
                headers.put("Content-type", "application/octet-stream");
            }

            self.onRequestHeaders(headers);
            for (Map.Entry<String, String> header : headers.entrySet()) {
                xhr.setRequestProperty(header.getKey(), header.getValue());
            }

            logger.fine(String.format("sending xhr with url %s | data %s", this.uri, this.data));
            new Thread(new Runnable() {
                @Override
                public void run() {
                    OutputStream output = null;
                    InputStream input = null;
                    BufferedReader reader = null;
                    try {
                        if (self.data != null) {
                            xhr.setFixedLengthStreamingMode(self.data.length);
                            output = new BufferedOutputStream(xhr.getOutputStream());
                            output.write(self.data);
                            output.flush();
                        }

                        Map<String, String> headers = new TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER);

                        Map<String, List<String>> xhrHeaderFields = xhr.getHeaderFields();
                        if(xhrHeaderFields != null) {
                            for (String key : xhrHeaderFields.keySet()) {
                                if (key == null) continue;
                                headers.put(key, xhr.getHeaderField(key));
                            }
                        }

                        self.onResponseHeaders(headers);

                        final int statusCode = xhr.getResponseCode();
                        if (HttpURLConnection.HTTP_OK == statusCode) {
                            String contentType = xhr.getContentType();
                            if ("application/octet-stream".equalsIgnoreCase(contentType)) {
                                input = new BufferedInputStream(xhr.getInputStream());
                                List<byte[]> buffers = new ArrayList<byte[]>();
                                int capacity = 0;
                                int len = 0;
                                byte[] buffer = new byte[1024];
                                while ((len = input.read(buffer)) > 0) {
                                    byte[] _buffer = new byte[len];
                                    System.arraycopy(buffer, 0, _buffer, 0, len);
                                    buffers.add(_buffer);
                                    capacity += len;
                                }
                                ByteBuffer data = ByteBuffer.allocate(capacity);
                                for (byte[] b : buffers) {
                                    data.put(b);
                                }
                                self.onData(data.array());
                            } else {
                                String line;
                                StringBuilder data = new StringBuilder();
                                reader = new BufferedReader(new InputStreamReader(xhr.getInputStream()));
                                while ((line = reader.readLine()) != null) {
                                    data.append(line);
                                }
                                self.onData(data.toString());
                            }
                        } else {
                            self.onError(new IOException(Integer.toString(statusCode)));
                        }
                    } catch (IOException e) {
                        self.onError(e);
                    } finally {
                        try {
                            if (output != null) output.close();
                        } catch (IOException e) {}
                        try {
                            if (input != null) input.close();
                        } catch (IOException e) {}
                        try {
                            if (reader != null) reader.close();
                        } catch (IOException e) {}
                    }
                }
            }).start();
        }

        private void onSuccess() {
            this.emit(EVENT_SUCCESS);
            this.cleanup();
        }

        private void onData(String data) {
            this.emit(EVENT_DATA, data);
            this.onSuccess();
        }

        private void onData(byte[] data) {
            this.emit(EVENT_DATA, data);
            this.onSuccess();
        }

        private void onError(Exception err) {
            this.emit(EVENT_ERROR, err);
            this.cleanup();
        }

        private void onRequestHeaders(Map<String, String> headers) {
            this.emit(EVENT_REQUEST_HEADERS, headers);
        }

        private void onResponseHeaders(Map<String, String> headers) {
            this.emit(EVENT_RESPONSE_HEADERS, headers);
        }

        private void cleanup() {
            if (xhr == null) {
                return;
            }

            xhr.disconnect();
            xhr = null;
        }

        public void abort() {
            this.cleanup();
        }

        public static class Options {

            public String uri;
            public String method;
            public byte[] data;
            public SSLContext sslContext;
        }
    }
}
