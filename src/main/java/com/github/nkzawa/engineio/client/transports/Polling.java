package com.github.nkzawa.engineio.client.transports;


import com.github.nkzawa.engineio.client.Transport;
import com.github.nkzawa.engineio.parser.Packet;
import com.github.nkzawa.engineio.parser.Parser;
import com.github.nkzawa.parseqs.ParseQS;
import com.github.nkzawa.thread.EventThread;

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

    @Override
    protected void onData(String data) {
        _onData(data);
    }

    @Override
    protected void onData(byte[] data) {
        _onData(data);
    }

    private void _onData(Object data) {
        final Polling self = this;
        logger.fine(String.format("polling got data %s", data));
        Parser.DecodePayloadCallback callback = new Parser.DecodePayloadCallback() {
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
        };

        if (data instanceof String) {
            @SuppressWarnings("unchecked")
            Parser.DecodePayloadCallback<String> _callback = callback;
            Parser.decodePayload((String)data, _callback);
        } else if (data instanceof byte[]) {
            Parser.decodePayload((byte[])data, callback);
        }

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
        final Polling self = this;

        Listener close = new Listener() {
            @Override
            public void call(Object... args) {
                logger.fine("writing close packet");
                self.write(new Packet[] {new Packet(Packet.CLOSE)});
            }
        };

        if (this.readyState == ReadyState.OPEN) {
            logger.fine("transport open - closing");
            close.call();
        } else {
            // in case we're trying to close while
            // handshaking is in progress (engine.io-client GH-164)
            logger.fine("transport not open - deferring close");
            this.once(EVENT_OPEN, close);
        }
    }

    protected void write(Packet[] packets) {
        final Polling self = this;
        this.writable = false;
        final Runnable callbackfn = new Runnable() {
            @Override
            public void run() {
                self.writable = true;
                self.emit(EVENT_DRAIN);
            }
        };

        Parser.encodePayload(packets, new Parser.EncodeCallback<byte[]>() {
            @Override
            public void call(byte[] data) {
                self.doWrite(data, callbackfn);
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
            query.put(this.timestampParam, String.valueOf(new Date().getTime()) + "-" + Transport.timestamps++);
        }

        String _query = ParseQS.encode(query);

        if (this.port > 0 && (("https".equals(schema) && this.port != 443)
                || ("http".equals(schema) && this.port != 80))) {
            port = ":" + this.port;
        }

        if (_query.length() > 0) {
            _query = "?" + _query;
        }

        return schema + "://" + this.hostname + port + this.path + _query;
    }

    abstract protected void doWrite(byte[] data, Runnable fn);

    abstract protected void doPoll();
}
