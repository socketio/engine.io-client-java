package com.github.nkzawa.engineio.client.transports;


import com.github.nkzawa.engineio.client.Transport;
import com.github.nkzawa.engineio.parser.Packet;
import com.github.nkzawa.engineio.parser.Parser;
import org.apache.http.Consts;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.message.BasicNameValuePair;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft_17;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class WebSocket extends Transport {

    private WebSocketClient socket;
    private Future bufferedAmountId;

    private ScheduledExecutorService drainScheduler = Executors.newSingleThreadScheduledExecutor();


    public WebSocket(Options opts) {
        super(opts);
        this.name = "websocket";
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
                    self.onOpen();
                }
                @Override
                public void onClose(int i, String s, boolean b) {
                    self.onClose();
                }
                @Override
                public void onMessage(String s) {
                    self.onData(s);
                }
                @Override
                public void onError(Exception e) {
                    self.onError("websocket error", e);
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
                self.emit("drain");
            }
        };

        if (this.socket.getConnection().hasBufferedData()) {
            this.bufferedAmountId = this.drainScheduler.scheduleAtFixedRate(new Runnable() {
                @Override
                public void run() {
                    if (!self.socket.getConnection().hasBufferedData()) {
                        self.bufferedAmountId.cancel(true);
                        ondrain.run();
                    }
                }
            }, 50, 50, TimeUnit.MILLISECONDS);
        } else {
            this.drainScheduler.schedule(ondrain, 0, TimeUnit.MILLISECONDS);
        }
    }

    @Override
    public void onClose() {
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
        List<NameValuePair> query = this.query;
        if (query == null) {
            query = new ArrayList<NameValuePair>();
        }
        String schema = this.secure ? "wss" : "ws";
        String port = "";

        if (this.port > 0 && (("wss".equals(schema) && this.port != 443)
                || ("ws".equals(schema) && this.port != 80))) {
            port = ":" + this.port;
        }

        if (this.timestampRequests) {
            Iterator<NameValuePair> i = query.iterator();
            while (i.hasNext()) {
                NameValuePair pair = i.next();
                if (this.timestampParam.equals(pair.getName())) {
                    i.remove();
                }
            }
            query.add(new BasicNameValuePair(this.timestampParam,
                    String.valueOf(new Date().getTime())));
        }

        String _query = URLEncodedUtils.format(query, Consts.UTF_8);
        if (_query.length() > 0) {
            _query = "?" + _query;
        }

        return new StringBuilder()
                .append(schema).append("://").append(this.hostname)
                .append(port).append(this.path).append(_query).toString();
    }

    private boolean check() {
        // for checking if the websocket is available. Should we remove?
        return true;
    }

}
