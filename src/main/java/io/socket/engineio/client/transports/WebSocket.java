package io.socket.engineio.client.transports;


import io.socket.engineio.client.Transport;
import io.socket.engineio.parser.Packet;
import io.socket.engineio.parser.Parser;
import io.socket.parseqs.ParseQS;
import io.socket.thread.EventThread;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import com.squareup.okhttp.ws.WebSocket.PayloadType;
import com.squareup.okhttp.ws.WebSocketCall;
import com.squareup.okhttp.ws.WebSocketListener;
import io.socket.utf8.UTF8Exception;
import okio.Buffer;
import okio.BufferedSource;

import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static com.squareup.okhttp.ws.WebSocket.PayloadType.BINARY;
import static com.squareup.okhttp.ws.WebSocket.PayloadType.TEXT;

public class WebSocket extends Transport {

    public static final String NAME = "websocket";

    private static final Logger logger = Logger.getLogger(PollingXHR.class.getName());

    private com.squareup.okhttp.ws.WebSocket ws;
    private WebSocketCall wsCall;

    public WebSocket(Options opts) {
        super(opts);
        this.name = NAME;
    }

    protected void doOpen() {
        Map<String, List<String>> headers = new TreeMap<String, List<String>>(String.CASE_INSENSITIVE_ORDER);
        this.emit(EVENT_REQUEST_HEADERS, headers);

        final WebSocket self = this;
        final OkHttpClient client = new OkHttpClient();

        // turn off timeouts (github.com/socketio/engine.io-client-java/issues/32)
        client.setConnectTimeout(0, TimeUnit.MILLISECONDS);
        client.setReadTimeout(0, TimeUnit.MILLISECONDS);
        client.setWriteTimeout(0, TimeUnit.MILLISECONDS);

        if (this.sslContext != null) {
            SSLSocketFactory factory = sslContext.getSocketFactory();// (SSLSocketFactory) SSLSocketFactory.getDefault();
            client.setSslSocketFactory(factory);
        }
        if (this.hostnameVerifier != null) {
            client.setHostnameVerifier(this.hostnameVerifier);
        }
        Request.Builder builder = new Request.Builder().url(uri());
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            for (String v : entry.getValue()) {
                builder.addHeader(entry.getKey(), v);
            }
        }
        final Request request = builder.build();
        wsCall = WebSocketCall.create(client, request);
        wsCall.enqueue(new WebSocketListener() {
            @Override
            public void onOpen(com.squareup.okhttp.ws.WebSocket webSocket, Response response) {
                ws = webSocket;
                final Map<String, List<String>> headers = response.headers().toMultimap();
                EventThread.exec(new Runnable() {
                    @Override
                    public void run() {
                        self.emit(EVENT_RESPONSE_HEADERS, headers);
                        self.onOpen();
                    }
                });
            }

            @Override
            public void onMessage(BufferedSource payload, final PayloadType type) throws IOException {
                Object data = null;
                switch (type) {
                    case TEXT:
                        data = payload.readUtf8();
                        break;
                    case BINARY:
                        data = payload.readByteArray();
                        break;
                    default:
                        EventThread.exec(new Runnable() {
                            @Override
                            public void run() {
                                self.onError("Unknown payload type: " + type, new IllegalStateException());
                            }
                        });
                }
                payload.close();
                final Object finalData = data;
                EventThread.exec(new Runnable() {
                    @Override
                    public void run() {
                        if (finalData == null) {
                            return;
                        }
                        if (finalData instanceof String) {
                            self.onData((String) finalData);
                        } else {
                            self.onData((byte[]) finalData);
                        }
                    }
                });

            }

            @Override
            public void onPong(Buffer payload) {
            }

            @Override
            public void onClose(int code, String reason) {
                EventThread.exec(new Runnable() {
                    @Override
                    public void run() {
                        self.onClose();
                    }
                });
            }

            @Override
            public void onFailure(final IOException e, final Response response) {
                EventThread.exec(new Runnable() {
                    @Override
                    public void run() {
                        self.onError("websocket error", e);
                    }
                });
            }
        });
        client.getDispatcher().getExecutorService().shutdown();
    }

    protected void write(Packet[] packets) throws UTF8Exception {
        final WebSocket self = this;
        this.writable = false;
        for (Packet packet : packets) {
            Parser.encodePacket(packet, new Parser.EncodeCallback() {
                @Override
                public void call(Object packet) {
                    try {
                        if (packet instanceof String) {
                            self.ws.sendMessage(TEXT, new Buffer().writeUtf8((String) packet));
                        } else if (packet instanceof byte[]) {
                            self.ws.sendMessage(BINARY, new Buffer().write((byte[]) packet));
                        }
                    } catch (IOException e) {
                        logger.fine("websocket closed before onclose event");
                    }
                }
            });
        }

        final Runnable ondrain = new Runnable() {
            @Override
            public void run() {
                self.writable = true;
                self.emit(EVENT_DRAIN);
            }
        };

        // fake drain
        // defer to next tick to allow Socket to clear writeBuffer
        EventThread.nextTick(ondrain);
    }

    @Override
    protected void onClose() {
        super.onClose();
    }

    protected void doClose() {
        if (wsCall != null) {
            wsCall.cancel();
        }
        if (ws != null) {
            try {
                ws.close(1000, "");
            } catch (IOException e) {
                // websocket already closed
            } catch (IllegalStateException e) {
                // websocket already closed
            }
        }
    }

    protected String uri() {
        Map<String, String> query = this.query;
        if (query == null) {
            query = new HashMap<String, String>();
        }
        String schema = this.secure ? "wss" : "ws";
        String port = "";

        if (this.port > 0 && (("wss".equals(schema) && this.port != 443)
                || ("ws".equals(schema) && this.port != 80))) {
            port = ":" + this.port;
        }

        if (this.timestampRequests) {
            query.put(this.timestampParam, String.valueOf(new Date().getTime()));
        }

        String _query = ParseQS.encode(query);
        if (_query.length() > 0) {
            _query = "?" + _query;
        }

        return schema + "://" + this.hostname + port + this.path + _query;
    }
}