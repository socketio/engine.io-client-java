package com.github.nkzawa.engineio.client.transports;


import com.github.nkzawa.engineio.client.EventThread;
import com.github.nkzawa.engineio.client.Transport;
import com.github.nkzawa.engineio.client.Util;
import com.github.nkzawa.engineio.parser.Packet;
import com.github.nkzawa.engineio.parser.Parser;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

abstract public class Polling extends Transport {

    private static final Logger logger = Logger.getLogger(Polling.class.getName());

    public static final String NAME = "polling";

    public static final String EVENT_POLL = "poll";
    public static final String EVENT_POLL_COMPLETE = "pollComplete";

    private boolean polling;


    public Polling(Options opts) {
        super(opts);
        this.name = NAME;
    }

    protected void doOpen() {
        this.poll();
    }

    public void pause(final Runnable onPause) {
        EventThread.exec(new Runnable() {
            @Override
            public void run() {
                final Polling self = Polling.this;

                Polling.this.readyState = ReadyState.PAUSED;

                final Runnable pause = new Runnable() {
                    @Override
                    public void run() {
                        logger.fine("paused");
                        self.readyState = ReadyState.PAUSED;
                        onPause.run();
                    }
                };

                if (Polling.this.polling || !Polling.this.writable) {
                    final int[] total = new int[]{0};

                    if (Polling.this.polling) {
                        logger.fine("we are currently polling - waiting to pause");
                        total[0]++;
                        Polling.this.once(EVENT_POLL_COMPLETE, new Listener() {
                            @Override
                            public void call(Object... args) {
                                logger.fine("pre-pause polling complete");
                                if (--total[0] == 0) {
                                    pause.run();
                                }
                            }
                        });
                    }

                    if (!Polling.this.writable) {
                        logger.fine("we are currently writing - waiting to pause");
                        total[0]++;
                        Polling.this.once(EVENT_DRAIN, new Listener() {
                            @Override
                            public void call(Object... args) {
                                logger.fine("pre-pause writing complete");
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
        });
    }

    private void poll() {
        logger.fine("polling");
        this.polling = true;
        this.doPoll();
        this.emit(EVENT_POLL);
    }

    protected void onData(String data) {
        final Polling self = this;
        logger.fine(String.format("polling got data %s", data));

        Parser.decodePayload(data, new Parser.DecodePayloadCallback() {
            @Override
            public boolean call(Packet packet, int index, int total) {
                if (self.readyState == ReadyState.OPENING) {
                    self.onOpen();
                }

                if (Packet.CLOSE.equals(packet.type)) {
                    self.onClose();
                    return false;
                }

                self.onPacket(packet);
                return true;
            }
        });

        if (this.readyState != ReadyState.CLOSED) {
            this.polling = false;
            this.emit(EVENT_POLL_COMPLETE);

            if (this.readyState == ReadyState.OPEN) {
                this.poll();
            } else {
                logger.fine(String.format("ignoring poll - transport state '%s'", this.readyState));
            }
        }
    }

    protected void doClose() {
        logger.fine("sending close packet");
        this.send(new Packet[] {new Packet(Packet.CLOSE, null)});
    }

    protected void write(Packet[] packets) {
        final Polling self = this;
        this.writable = false;
        this.doWrite(Parser.encodePayload(packets), new Runnable() {
            @Override
            public void run() {
                self.writable = true;
                self.emit(EVENT_DRAIN);
            }
        });
    }

    protected String uri() {
        Map<String, String> query = this.query;
        if (query == null) {
            query = new HashMap<String, String>();
        }
        String schema = this.secure ? "https" : "http";
        String port = "";

        if (this.timestampRequests) {
            query.put(this.timestampParam, String.valueOf(new Date().getTime()));
        }

        String _query = Util.qs(query);

        if (this.port > 0 && (("https".equals(schema) && this.port != 443)
                || ("http".equals(schema) && this.port != 80))) {
            port = ":" + this.port;
        }

        if (_query.length() > 0) {
            _query = "?" + _query;
        }

        return schema + "://" + this.hostname + port + this.path + _query;
    }

    abstract protected void doWrite(String data, Runnable fn);

    abstract protected void doPoll();
}
