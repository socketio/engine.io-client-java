package com.github.nkzawa.engineio.client.transports;


import com.github.nkzawa.emitter.Emitter;
import com.github.nkzawa.thread.EventThread;
import com.squareup.okhttp.Call;
import com.squareup.okhttp.Callback;
import com.squareup.okhttp.Headers;
import com.squareup.okhttp.Interceptor;
import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request.Builder;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;

import java.io.IOException;
import java.net.URL;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;

import static com.squareup.okhttp.internal.Util.closeQuietly;


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
        opts.hostnameVerifier = this.hostnameVerifier;
        opts.networkInterceptor = this.networkInterceptor;

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
        private static final String BINARY_CONTENT_TYPE = "application/octet-stream";
        private static final MediaType BINARY_MEDIA_TYPE = MediaType.parse(BINARY_CONTENT_TYPE);

        private String method;
        private String uri;

        // data is always a binary
        private byte[] data;

        private SSLContext sslContext;
        private OkHttpClient okHttpClient = new OkHttpClient();
        private Response response;
        private Call requestCall;
        private HostnameVerifier hostnameVerifier;
        private Interceptor networkInterceptor;

        public Request(Options opts) {
            this.method = opts.method != null ? opts.method : "GET";
            this.uri = opts.uri;
            this.data = opts.data;
            this.sslContext = opts.sslContext;
            this.hostnameVerifier = opts.hostnameVerifier;
            this.networkInterceptor = opts.networkInterceptor;
        }

        public void create() {
            final Request self = this;
            logger.fine(String.format("xhr open %s: %s", this.method, this.uri));
            okHttpClient.setConnectTimeout(10000, TimeUnit.MILLISECONDS);
            if (this.sslContext != null) {
                okHttpClient.setSocketFactory(this.sslContext.getSocketFactory());
            }
            if (this.hostnameVerifier != null) {
                okHttpClient.setHostnameVerifier(this.hostnameVerifier);
            }
            if (this.networkInterceptor != null) {
                okHttpClient.networkInterceptors().add(networkInterceptor);
            }

            Map<String, String> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            MediaType mediaType = null;
            if ("POST".equals(this.method)) {
                headers.put("Content-type", BINARY_CONTENT_TYPE);
                mediaType = BINARY_MEDIA_TYPE;
            }

            self.onRequestHeaders(headers);

            logger.fine(String.format("sending xhr with url %s | data %s", this.uri, this.data));

            try {
                final Builder requestBuilder = new Builder();
                for (Map.Entry<String, String> header : headers.entrySet()) {
                    requestBuilder.addHeader(header.getKey(), header.getValue());
                }
                final com.squareup.okhttp.Request request = requestBuilder
                        .url(new URL(self.uri))
                        .method(self.method, (self.data != null) ?
                                RequestBody.create(mediaType, self.data) : null)
                        .build();

                requestCall = okHttpClient.newCall(request);
                requestCall.enqueue(new Callback() {
                    @Override
                    public void onFailure(com.squareup.okhttp.Request request, IOException e) {
                        self.onError(e);
                    }

                    @Override
                    public void onResponse(Response response) throws IOException {
                        self.response = response;
                        Map<String, String> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
                        Headers responseHeaders = response.headers();
                        for (int i = 0, size = responseHeaders.size(); i < size; i++) {
                            headers.put(responseHeaders.name(i), responseHeaders.value(i));
                        }

                        self.onResponseHeaders(headers);

                        final int statusCode = response.code();
                        if (statusCode == 200) {
                            self.onLoad();
                        } else {
                            self.onError(new IOException(Integer.toString(statusCode)));
                        }
                    }
                });
            } catch (IOException e) {
                self.onError(e);
            }
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
            if (requestCall != null) {
                requestCall.cancel();
                requestCall = null;
            }
            if (response != null) {
                closeQuietly(response.body());
                response = null;
            }
        }

        private void onLoad() {
            String contentType = response.body().contentType().toString();

            try {
                if (BINARY_CONTENT_TYPE.equalsIgnoreCase(contentType)) {
                    this.onData(response.body().bytes());
                } else {
                    this.onData(response.body().string());
                }
            } catch (IOException e) {
                this.onError(e);
            }
        }
        public void abort() {
            this.cleanup();
        }

        public static class Options {

            public String uri;
            public String method;
            public byte[] data;
            public SSLContext sslContext;
            public HostnameVerifier hostnameVerifier;
            public Interceptor networkInterceptor;
        }
    }
}
