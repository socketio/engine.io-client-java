package com.github.nkzawa.engineio.client.transports;


import com.github.nkzawa.emitter.Emitter;
import com.github.nkzawa.engineio.client.EventThread;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

public class PollingXHR extends Polling {

    private static final Logger logger = Logger.getLogger(PollingXHR.class.getName());

    private Request sendXhr;
    private Request pollXhr;
    private String cookie;

    public PollingXHR(Options opts) {
        super(opts);
        this.cookie = opts.cookie;
    }

    protected Request request() {
        return this.request(null);
    }

    protected Request request(Request.Options opts) {
        if (opts == null) {
            opts = new Request.Options();
        }
        opts.uri = this.uri();
        opts.cookie = this.cookie;
        return new Request(opts);
    }

    protected void doWrite(String data, final Runnable fn) {
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
                        String data = args.length > 0 ? (String) args[0] : null;
                        self.onData(data);
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

        private static final ExecutorService xhrService = Executors.newCachedThreadPool();

        String method;
        String uri;
        String data;
        String cookie;
        HttpURLConnection xhr;

        public Request(Options opts) {
            this.method = opts.method != null ? opts.method : "GET";
            this.uri = opts.uri;
            this.data = opts.data;
            this.cookie = opts.cookie;
        }

        public void create() {
            final Request self = this;
            try {
                URL url = new URL(this.uri);
                xhr = (HttpURLConnection)url.openConnection();
                xhr.setRequestMethod(this.method);
            } catch (IOException e) {
                this.onError(e);
                return;
            }

            if ("POST".equals(this.method)) {
                xhr.setDoOutput(true);
                xhr.setRequestProperty("Content-type", "text/plain;charset=UTF-8");
            }

            if (this.cookie != null) {
                xhr.setRequestProperty("Cookie", this.cookie);
            }

            logger.fine(String.format("sending xhr with url %s | data %s", this.uri, this.data));
            xhrService.submit(new Runnable() {
                @Override
                public void run() {
                    BufferedWriter writer = null;
                    BufferedReader reader = null;
                    try {
                        if (self.data != null) {
                            byte[] data = self.data.getBytes("UTF-8");
                            xhr.setFixedLengthStreamingMode(data.length);
                            writer = new BufferedWriter(new OutputStreamWriter(xhr.getOutputStream()));
                            writer.write(self.data);
                            writer.flush();
                        }

                        String line;
                        StringBuilder data = new StringBuilder();
                        reader = new BufferedReader(new InputStreamReader(xhr.getInputStream()));
                        while ((line = reader.readLine()) != null) {
                            data.append(line);
                        }
                        self.onData(data.toString());
                    } catch (IOException e) {
                        self.onError(e);
                    } finally {
                        try {
                            if (writer != null) writer.close();
                        } catch (IOException e) {}
                        try {
                            if (reader != null) reader.close();
                        } catch (IOException e) {}
                    }
                }
            });
        }

        private void onSuccess() {
            this.emit(EVENT_SUCCESS);
            this.cleanup();
        }

        private void onData(String data) {
            this.emit(EVENT_DATA, data);
            this.onSuccess();
        }

        private void onError(Exception err) {
            this.emit(EVENT_ERROR, err);
            this.cleanup();
        }

        private void cleanup() {
            if (xhr != null) {
                xhr.disconnect();
                xhr = null;
            }
        }

        public void abort() {
            this.cleanup();
        }

        public static class Options {

            public String uri;
            public String method;
            public String data;
            public String cookie;
        }
    }
}
