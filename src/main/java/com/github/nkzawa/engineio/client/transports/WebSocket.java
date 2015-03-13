package com.github.nkzawa.engineio.client.transports;


import com.github.nkzawa.engineio.client.Transport;
import com.github.nkzawa.engineio.parser.Packet;
import com.github.nkzawa.engineio.parser.Parser;
import com.github.nkzawa.parseqs.ParseQS;
import com.github.nkzawa.thread.EventThread;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import com.squareup.okhttp.ws.WebSocketCall;
import com.squareup.okhttp.ws.WebSocketListener;
import java.util.logging.Logger;
import okio.Buffer;
import okio.BufferedSource;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static com.squareup.okhttp.ws.WebSocket.PayloadType.TEXT;
import static com.squareup.okhttp.ws.WebSocket.PayloadType.BINARY;

public class WebSocket extends Transport {


  private static final Logger logger = Logger.getLogger(WebSocket.class.getName());

    public static final String NAME = "websocket";

    private final OkHttpClient client;
    private com.squareup.okhttp.ws.WebSocket ws;
    private WebSocketCall wsCall;

    public WebSocket(Options opts) {
        super(opts);
        this.name = NAME;
        if (opts.webSocketClient == null){
            client = defaultClient();
        } else {
            client = opts.webSocketClient;
        }
        if (this.sslContext != null) {
          client.setSslSocketFactory(sslContext.getSocketFactory());
        }
    }

    private OkHttpClient defaultClient() {
        OkHttpClient wsClient = new OkHttpClient();
        wsClient.setConnectTimeout(15, TimeUnit.SECONDS);
        wsClient.setReadTimeout(15, TimeUnit.SECONDS);
        wsClient.setWriteTimeout(15, TimeUnit.SECONDS);
        return wsClient;
    }

    protected void doOpen() {
        if (ws != null) {
            logger.fine("doOpen WebSocket already exist");
            return;
        }

        Map<String, String> headers = new TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER);
        this.emit(EVENT_REQUEST_HEADERS, headers);

        final WebSocket self = this;

        Request.Builder builder = new Request.Builder()
          .url(this.uri());
        for (Map.Entry<String, String> entry : headers.entrySet()) {
          builder.addHeader(entry.getKey(), entry.getValue());
        }
        Request request = builder.build();
        wsCall = WebSocketCall.create(client, request);
        wsCall.enqueue(new WebSocketListener() {
            @Override
            public void onOpen(com.squareup.okhttp.ws.WebSocket webSocket, Request request, final Response response) throws IOException {
                self.ws = webSocket;
                logger.fine("onOpen");
                final Map<String, String> headers = new TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER);
                for (int i = 0; i < response.headers().size(); ++i) {
                    headers.put(response.headers().name(i), response.headers().value(i));
                }
                EventThread.exec(new Runnable() {
                    @Override
                    public void run() {
                        self.emit(EVENT_RESPONSE_HEADERS, headers);
                        self.onOpen();
                    }
                });
            }

            @Override
            public void onMessage(final BufferedSource payload, final com.squareup.okhttp.ws.WebSocket.PayloadType type) throws IOException {
                logger.fine("onMessage, type: " + type);
                Object data;
                switch (type) {
                    case TEXT:
                        data = payload.readUtf8();
                        break;
                    case BINARY:
                        data = payload.readByteArray();
                        break;
                    default:
                        throw new IllegalStateException("Unknown payload type: " + type);
                }
                payload.close();
                final Object finalData = data;
                EventThread.exec(new Runnable() {
                    @Override
                    public void run() {
                        if (finalData instanceof String) {
                            self.onData((String) finalData);
                        } else {
                            self.onData((byte[]) finalData);
                        }
                    }
                });
            }

            @Override
            public void onPong(final Buffer payload) {
                logger.fine("onPong");
                EventThread.exec(new Runnable() {
                    @Override
                    public void run() {
                        self.onPong();
                    }
                });
            }

            @Override
            public void onClose(int code, String reason) {
                logger.fine("onClose, reason:" + reason);
                EventThread.exec(new Runnable() {
                    @Override
                    public void run() {
                        self.onClose();
                    }
                });
            }

            @Override
            public void onFailure(final IOException e) {
                logger.fine("onFailure");
                EventThread.exec(new Runnable() {
                    @Override
                    public void run() {
                        self.onError("WebSocket error", e);
                    }
                });
            }
        });
    }

    @Override
    protected void write(Packet[] packets) {
        final WebSocket self = this;
        this.writable = false;
        for (final Packet packet : packets) {
            Parser.encodePacket(packet, new Parser.EncodeCallback() {
                @Override
                public void call(Object encodedPacket) {
                    try {
                        if (Packet.PING.equals(packet.type)) {
                            self.ws.sendPing(null);
                        } else if (encodedPacket instanceof String) {
                            self.ws.sendMessage(
                                    TEXT, new Buffer().writeUtf8((String) encodedPacket));
                        } else if (encodedPacket instanceof byte[]) {
                            self.ws.sendMessage(
                                    BINARY, new Buffer().write((byte[]) encodedPacket));
                        }
                    } catch (final IOException e) {
                        EventThread.exec(new Runnable() {
                            @Override
                            public void run() {
                                self.onError("WebSocket sendMessage error", e);
                            }
                        });
                    }
                }
            });
        }

        final Runnable onDrain = new Runnable() {
            @Override
            public void run() {
                self.writable = true;
                self.emit(EVENT_DRAIN);
            }
        };

        // fake drain
        // defer to next tick to allow Socket to clear writeBuffer
        EventThread.nextTick(onDrain);
    }

    @Override
    protected void onClose() {
        super.onClose();
    }

    protected void doClose() {
        logger.fine("doClose");
        if (wsCall != null) {
            wsCall.cancel();
            wsCall = null;
        }
        if (this.ws != null) {
            try {
            this.ws.close(1000, "Good bye");
            } catch (IOException e) {
              // ignore?;
            }
            ws = null;
        }
    }

    protected void onPong() {
        this.onPacket(new Packet(Packet.PONG, "probe"));
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
