package io.socket.engineio.client.transports;

import com.neovisionaries.ws.client.WebSocketAdapter;
import com.neovisionaries.ws.client.WebSocketCloseCode;
import com.neovisionaries.ws.client.WebSocketException;
import com.neovisionaries.ws.client.WebSocketFactory;
import com.neovisionaries.ws.client.WebSocketFrame;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Logger;

import io.socket.engineio.client.Transport;
import io.socket.engineio.parser.Packet;
import io.socket.engineio.parser.Parser;
import io.socket.parseqs.ParseQS;
import io.socket.thread.EventThread;
import io.socket.utf8.UTF8Exception;
import io.socket.yeast.Yeast;

public class WebSocket extends Transport {

    public static final String NAME = "websocket";

    private static final Logger logger = Logger.getLogger(PollingXHR.class.getName());

    private com.neovisionaries.ws.client.WebSocket ws;

    public WebSocket(Options opts) {
        super(opts);
        this.name = NAME;
    }

    protected void doOpen() {
        Map<String, List<String>> headers = new TreeMap<String, List<String>>(String.CASE_INSENSITIVE_ORDER);
        this.emit(EVENT_REQUEST_HEADERS, headers);

        WebSocketFactory webSocketFactory = new WebSocketFactory();
        webSocketFactory.setConnectionTimeout(10000);
        final WebSocket self = this;

        if (this.sslContext != null) {
            webSocketFactory.setSSLContext(sslContext);
        }

        try {
            ws = webSocketFactory.createSocket(uri());
            for (String webSocketExtension : webSocketExtensions) {
                ws.addExtension(webSocketExtension);
            }
            ws.setMaxPayloadSize(8192);

            for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
                for (String v : entry.getValue()) {
                    ws.addHeader(entry.getKey(), v);
                }
            }
            ws.addListener(new WebSocketAdapter() {
                @Override
                public void onConnected(com.neovisionaries.ws.client.WebSocket websocket, final Map<String, List<String>> headers) throws Exception {
                    ws = websocket;
                    EventThread.exec(new Runnable() {
                        @Override
                        public void run() {
                            self.emit(EVENT_RESPONSE_HEADERS, headers);
                            self.onOpen();
                        }
                    });
                }

                @Override
                public void onBinaryMessage(com.neovisionaries.ws.client.WebSocket websocket, final byte[] binary) throws Exception {
                    EventThread.exec(new Runnable() {
                        @Override
                        public void run() {
                            if (binary != null) {
                                self.onData(binary);
                            }
                        }
                    });
                }

                @Override
                public void onTextMessage(com.neovisionaries.ws.client.WebSocket websocket, final String text) throws Exception {
                    EventThread.exec(new Runnable() {
                        @Override
                        public void run() {
                            if (text != null) {
                                self.onData(text);
                            }
                        }
                    });
                }

                @Override
                public void onDisconnected(com.neovisionaries.ws.client.WebSocket websocket, WebSocketFrame serverCloseFrame, WebSocketFrame clientCloseFrame, boolean closedByServer) throws Exception {
                    EventThread.exec(new Runnable() {
                        @Override
                        public void run() {
                            self.onClose();
                        }
                    });
                }

                @Override
                public void onError(com.neovisionaries.ws.client.WebSocket websocket, final WebSocketException cause) throws Exception {
                    EventThread.exec(new Runnable() {
                        @Override
                        public void run() {
                            self.onError("websocket error", cause);
                        }
                    });
                }
            });
            ws.connect();

        } catch (final Exception e) {
            EventThread.exec(new Runnable() {
                @Override
                public void run() {
                    self.onError("websocket error", e);
                }
            });
        }
    }

    protected void write(Packet[] packets) throws UTF8Exception {
        final WebSocket self = this;
        this.writable = false;

        final Runnable done = new Runnable() {
            @Override
            public void run() {
                // fake drain
                // defer to next tick to allow Socket to clear writeBuffer
                EventThread.nextTick(new Runnable() {
                    @Override
                    public void run() {
                        self.writable = true;
                        self.emit(EVENT_DRAIN);
                    }
                });
            }
        };

        final int[] total = new int[]{packets.length};
        for (Packet packet : packets) {
            Parser.encodePacket(packet, new Parser.EncodeCallback() {
                @Override
                public void call(Object packet1) {
                    if (packet1 instanceof String) {
                        self.ws.sendText((String) packet1);
                    } else if (packet1 instanceof byte[]) {
                        self.ws.sendBinary((byte[]) packet1);
                    }

                    if (0 == --total[0]) {
                        done.run();
                    }
                }
            });
        }
    }

    @Override
    protected void onClose() {
        super.onClose();
    }

    protected void doClose() {
        if (ws != null) {
            try {
                ws.disconnect(WebSocketCloseCode.NORMAL, null);
            } catch (IllegalStateException e) {
                // websocket already closed
            }
        }
    }

    protected String uri() {
        Map<String, String> query = this.query;
        if (query == null) {
            query = new HashMap<>();
        }
        String schema = this.secure ? "wss" : "ws";
        String port = "";

        if (this.port > 0 && (("wss".equals(schema) && this.port != 443)
                || ("ws".equals(schema) && this.port != 80))) {
            port = ":" + this.port;
        }

        if (this.timestampRequests) {
            query.put(this.timestampParam, Yeast.yeast());
        }

        String derivedQuery = ParseQS.encode(query);
        if (derivedQuery.length() > 0) {
            derivedQuery = "?" + derivedQuery;
        }

        boolean ipv6 = this.hostname.contains(":");
        return schema + "://" + (ipv6 ? "[" + this.hostname + "]" : this.hostname) + port + this.path + derivedQuery;
    }
}