package com.github.nkzawa.engineio.client.transports;


import com.github.nkzawa.engineio.client.EventThread;
import com.github.nkzawa.engineio.client.Transport;
import com.github.nkzawa.engineio.client.Util;
import com.github.nkzawa.engineio.parser.Packet;
import com.github.nkzawa.engineio.parser.Parser;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft_17;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class WebSocket extends Transport {

    public static final String NAME = "websocket";

    private WebSocketClient socket;
    private Future bufferedAmountId;

    private ScheduledExecutorService drainScheduler = Executors.newSingleThreadScheduledExecutor();


    public WebSocket(Options opts) {
        super(opts);
        this.name = NAME;
    }

    protected void doOpen() {
        if (!this.check()) {
            return;
        }

        final WebSocket self = this;
        try {
            this.socket = new WebSocketClient(new URI(this.uri()), new Draft_17()) {
                @Override
                public void onOpen(ServerHandshake serverHandshake) {
                    EventThread.exec(new Runnable() {
                        @Override
                        public void run() {
                            self.onOpen();
                        }
                    });
                }
                @Override
                public void onClose(int i, String s, boolean b) {
                    EventThread.exec(new Runnable() {
                        @Override
                        public void run() {
                            self.onClose();
                        }
                    });
                }
                @Override
                public void onMessage(final String s) {
                    EventThread.exec(new Runnable() {
                        @Override
                        public void run() {
                            self.onData(s);
                        }
                    });
                }
                @Override
                public void onError(final Exception e) {
                    EventThread.exec(new Runnable() {
                        @Override
                        public void run() {
                            self.onError("websocket error", e);
                        }
                    });
                }
            };
            this.socket.connect();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    protected void write(Packet[] packets) {
        final WebSocket self = this;
        this.writable = false;
        for (Packet packet : packets) {
            this.socket.send(Parser.encodePacket(packet));
        }

        final Runnable ondrain = new Runnable() {
            @Override
            public void run() {
                self.writable = true;
                self.emit(EVENT_DRAIN);
            }
        };

        if (this.socket.getConnection().hasBufferedData()) {
            this.bufferedAmountId = this.drainScheduler.scheduleAtFixedRate(new Runnable() {
                @Override
                public void run() {
                    EventThread.exec(new Runnable() {
                        @Override
                        public void run() {
                            if (!self.socket.getConnection().hasBufferedData()) {
                                self.bufferedAmountId.cancel(true);
                                ondrain.run();
                            }
                        }
                    });
                }
            }, 50, 50, TimeUnit.MILLISECONDS);
        } else {
            EventThread.nextTick(ondrain);
        }
    }

    @Override
    protected void onClose() {
        if (this.bufferedAmountId != null) {
            this.bufferedAmountId.cancel(true);
        }
        super.onClose();
    }

    protected void doClose() {
        if (this.socket != null) {
            this.socket.close();
        }
    }

    private String uri() {
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

        String _query = Util.qs(query);
        if (_query.length() > 0) {
            _query = "?" + _query;
        }

        return schema + "://" + this.hostname + port + this.path + _query;
    }

    private boolean check() {
        // for checking if the websocket is available. Should we remove?
        return true;
    }

}
