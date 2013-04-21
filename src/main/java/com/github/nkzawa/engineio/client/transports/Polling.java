package com.github.nkzawa.engineio.client.transports;


import com.github.nkzawa.engineio.client.Transport;
import com.github.nkzawa.engineio.parser.Packet;
import com.github.nkzawa.engineio.parser.Parser;
import org.apache.http.Consts;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.message.BasicNameValuePair;

import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;

abstract public class Polling extends Transport {

    private static final Logger logger = Logger.getLogger("engine.io-client:polling");

    private boolean polling;


    public Polling(Options opts) {
        super(opts);
        this.name = "polling";
    }

    protected void doOpen() {
        this.poll();
    }

    public void pause(final Runnable onPause) {
        int pending = 0;
        final Polling self = this;

        this.readyState = "paused";

        final Runnable pause = new Runnable() {
            @Override
            public void run() {
                logger.info("paused");
                self.readyState = "paused";
                onPause.run();
            }
        };

        if (this.polling || !this.writable) {
            final int[] total = new int[] {0};

            if (this.polling) {
                logger.info("we are currently polling - waiting to pause");
                total[0]++;
                this.once("pollComplete", new Listener() {
                    @Override
                    public void call(Object... args) {
                        logger.info("pre-pause polling complete");
                        if (--total[0] == 0) {
                            pause.run();
                        }
                    }
                });
            }

            if (!this.writable) {
                logger.info("we are currently writing - waiting to pause");
                total[0]++;
                this.once("drain", new Listener() {
                    @Override
                    public void call(Object... args) {
                        logger.info("pre-pause writing complete");
                        if (--total[0] == 0) {
                            pause.run();
                        }
                    }
                });
            }
        } else {
            pause.run();
        }
    }

    private void poll() {
        logger.info("polling");
        this.polling = true;
        this.doPoll();
        this.emit("poll");
    }

    protected void onData(String data) {
        final Polling self = this;
        logger.info(String.format("polling got data %s", data));

        Parser.decodePayload(data, new Parser.DecodePayloadCallback() {
            @Override
            public boolean call(Packet packet, int index, int total) {
                if ("opening".equals(self.readyState)) {
                    self.onOpen();
                }

                if ("close".equals(packet.type)) {
                    self.onClose();
                    return false;
                }

                self.onPacket(packet);
                return true;
            }
        });

        if (!"closed".equals(this.readyState)) {
            this.polling = false;
            this.emit("pollComplete");

            if ("open".equals(this.readyState)) {
                this.poll();
            } else {
                logger.info(String.format("ignoring poll - transport state '%s'", this.readyState));
            }
        }
    }

    protected void doClose() {
        logger.info("sending close packet");
        this.send(new Packet[] {new Packet("close", null)});
    }

    protected void write(Packet[] packets) {
        final Polling self = this;
        this.writable = false;
        this.doWrite(Parser.encodePayload(packets), new Runnable() {
            @Override
            public void run() {
                self.writable = true;
                self.emit("drain");
            }
        });
    }

    protected String uri() {
        List<NameValuePair> query = this.query;
        if (query == null) {
            query = new ArrayList<NameValuePair>();
        }
        String schema = this.secure ? "https" : "http";
        String port = "";

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

        if (this.port > 0 && (("https".equals(schema) && this.port != 443)
                || ("http".equals(schema) && this.port != 80))) {
            port = ":" + this.port;
        }

        if (_query.length() > 0) {
            _query = "?" + _query;
        }

        return new StringBuilder()
                .append(schema).append("://").append(this.hostname)
                .append(port).append(this.path).append(_query).toString();
    }

    abstract protected void doWrite(String data, Runnable fn);

    abstract protected void doPoll();
}
