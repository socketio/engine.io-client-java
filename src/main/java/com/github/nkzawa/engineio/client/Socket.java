package com.github.nkzawa.engineio.client;

import com.github.nkzawa.emitter.Emitter;
import com.github.nkzawa.engineio.client.transports.Polling;
import com.github.nkzawa.engineio.client.transports.PollingXHR;
import com.github.nkzawa.engineio.client.transports.WebSocket;
import com.github.nkzawa.engineio.parser.HandshakeData;
import com.github.nkzawa.engineio.parser.Packet;
import com.github.nkzawa.engineio.parser.Parser;
import com.google.gson.Gson;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;


/**
 * The socket class for Event.IO Client.
 *
 * @see <a href="https://github.com/LearnBoost/engine.io-client">https://github.com/LearnBoost/engine.io-client</a>
 */
public abstract class Socket extends Emitter {

    private static final Logger logger = Logger.getLogger(Socket.class.getName());

    private static final Gson gson = new Gson();

    private enum ReadyState {
        OPENING, OPEN, CLOSING, CLOSED;

        @Override
        public String toString() {
            return super.toString().toLowerCase();
        }
    }

    /**
     * Called on successful connection.
     */
    public static final String EVENT_OPEN = "open";

    /**
     * Called on disconnection.
     */
    public static final String EVENT_CLOSE = "close";

    /**
     * Called when data is received from the server.
     */
    public static final String EVENT_MESSAGE = "message";

    /**
     * Called when an error occurs.
     */
    public static final String EVENT_ERROR = "error";

    /**
     * Called on completing a buffer flush.
     */
    public static final String EVENT_FLUSH = "flush";

    /**
     * Called after `drain` event of transport if writeBuffer is empty.
     */
    public static final String EVENT_DRAIN = "drain";

    public static final String EVENT_HANDSHAKE = "handshake";
    public static final String EVENT_UPGRADING = "upgrading";
    public static final String EVENT_UPGRADE = "upgrade";
    public static final String EVENT_PACKET = "packet";
    public static final String EVENT_PACKET_CREATE = "packetCreate";
    public static final String EVENT_HEARTBEAT = "heartbeat";
    public static final String EVENT_DATA = "data";

    private static final Runnable noop = new Runnable() {
        @Override
        public void run() {}
    };

    /**
     * List of Socket instances.
     */
    public static final Sockets sockets = new Sockets();

    /**
     * The protocol version.
     */
    public static final int protocol = Parser.protocol;

    private boolean secure;
    private boolean upgrade;
    private boolean timestampRequests;
    private boolean upgrading;
    private int port;
    private int policyPort;
    private int prevBufferLen;
    private long pingInterval;
    private long pingTimeout;
    private String id;
    private String hostname;
    private String path;
    private String timestampParam;
    private String cookie;
    private List<String> transports;
    private List<String> upgrades;
    private Map<String, String> query;
    private Queue<Packet> writeBuffer = new LinkedList<Packet>();
    private Queue<Runnable> callbackBuffer = new LinkedList<Runnable>();
    private Transport transport;
    private Future pingTimeoutTimer;
    private Future pingIntervalTimer;

    private ReadyState readyState;
    private ScheduledExecutorService heartbeatScheduler = Executors.newSingleThreadScheduledExecutor();


    /**
     * Creates a socket.
     *
     * @param uri URI to connect.
     * @throws URISyntaxException
     */
    public Socket(String uri) throws URISyntaxException {
        this(uri, null);
    }

    public Socket(URI uri) {
        this(uri, null);
    }

    /**
     * Creates a socket with options.
     *
     * @param uri URI to connect.
     * @param opts options for socket
     * @throws URISyntaxException
     */
    public Socket(String uri, Options opts) throws URISyntaxException {
        this(new URI(uri), opts);
    }

    public Socket(URI uri, Options opts) {
        this(Options.fromURI(uri, opts));
    }

    public Socket(Options opts) {
        if (opts.host != null) {
            String[] pieces = opts.host.split(":");
            opts.hostname = pieces[0];
            if (pieces.length > 1) {
                opts.port = Integer.parseInt(pieces[pieces.length - 1]);
            }
        }

        this.secure = opts.secure;
        this.hostname = opts.hostname != null ? opts.hostname : "localhost";
        this.port = opts.port != 0 ? opts.port : (this.secure ? 443 : 80);
        this.query = opts.query != null ?
                Util.qsParse(opts.query) : new HashMap<String, String>();
        this.upgrade = opts.upgrade;
        this.path = (opts.path != null ? opts.path : "/engine.io").replaceAll("/$", "") + "/";
        this.timestampParam = opts.timestampParam != null ? opts.timestampParam : "t";
        this.timestampRequests = opts.timestampRequests;
        this.transports = new ArrayList<String>(Arrays.asList(opts.transports != null ?
                opts.transports : new String[]{Polling.NAME, WebSocket.NAME}));
        this.policyPort = opts.policyPort != 0 ? opts.policyPort : 843;
        this.cookie = opts.cookie;

        Socket.sockets.add(this);
        Socket.sockets.evs.emit(Sockets.EVENT_ADD, this);
    }

    /**
     * Connects the client.
     */
    public void open() {
        EventThread.exec(new Runnable() {
            @Override
            public void run() {
                Socket.this.readyState = ReadyState.OPENING;
                Transport transport = Socket.this.createTransport(Socket.this.transports.get(0));
                Socket.this.setTransport(transport);
                transport.open();
            }
        });
    }

    private Transport createTransport(String name) {
        logger.fine(String.format("creating transport '%s'", name));
        Map<String, String> query = new HashMap<String, String>(this.query);

        query.put("EIO", String.valueOf(Parser.protocol));
        query.put("transport", name);
        if (this.id != null) {
            query.put("sid", this.id);
        }

        Transport.Options opts = new Transport.Options();
        opts.hostname = this.hostname;
        opts.port = this.port;
        opts.secure = this.secure;
        opts.path = this.path;
        opts.query = query;
        opts.timestampRequests = this.timestampRequests;
        opts.timestampParam = this.timestampParam;
        opts.policyPort = this.policyPort;
        opts.cookie = this.cookie;

        if (WebSocket.NAME.equals(name)) {
            return new WebSocket(opts);
        } else if (Polling.NAME.equals(name)) {
            return new PollingXHR(opts);
        }

        throw new RuntimeException();
    }

    private void setTransport(Transport transport) {
        final Socket self = this;

        if (this.transport != null) {
            logger.fine("clearing existing transport");
            this.transport.off();
        }

        this.transport = transport;

        transport.on(Transport.EVENT_DRAIN, new Listener() {
            @Override
            public void call(Object... args) {
                self.onDrain();
            }
        }).on(Transport.EVENT_PACKET, new Listener() {
            @Override
            public void call(Object... args) {
                self.onPacket(args.length > 0 ? (Packet) args[0] : null);
            }
        }).on(Transport.EVENT_ERROR, new Listener() {
            @Override
            public void call(Object... args) {
                self.onError(args.length > 0 ? (Exception) args[0] : null);
            }
        }).on(Transport.EVENT_CLOSE, new Listener() {
            @Override
            public void call(Object... args) {
                self.onClose("transport close");
            }
        });
    }

    private void probe(final String name) {
        logger.fine(String.format("probing transport '%s'", name));
        final Transport[] transport = new Transport[] {this.createTransport(name)};
        final boolean[] failed = new boolean[] {false};
        final Socket self = this;

        final Listener onerror = new Listener() {
            @Override
            public void call(Object... args) {
                if (failed[0]) return;

                failed[0] = true;

                // TODO: handle error
                Exception err = args.length > 0 ? (Exception)args[0] : null;
                EngineIOException error = new EngineIOException("probe error", err);
                //error.transport = transport[0].name;

                transport[0].close();
                transport[0] = null;
                logger.fine(String.format("probing transport '%s' failed because of error: %s", name, err));
                self.emit(EVENT_ERROR, error);
            }
        };

        transport[0].once(Transport.EVENT_OPEN, new Listener() {
            @Override
            public void call(Object... args) {
                if (failed[0]) return;

                logger.fine(String.format("probe transport '%s' opened", name));
                Packet packet = new Packet(Packet.PING, "probe");
                transport[0].send(new Packet[] {packet});
                transport[0].once(Transport.EVENT_PACKET, new Listener() {
                    @Override
                    public void call(Object... args) {
                        if (failed[0]) return;
                        Packet msg = (Packet)args[0];
                        if (Packet.PONG.equals(msg.type) && "probe".equals(msg.data)) {
                            logger.fine(String.format("probe transport '%s' pong", name));
                            self.upgrading = true;
                            self.emit(EVENT_UPGRADING, transport[0]);

                            logger.fine(String.format("pausing current transport '%s'", self.transport.name));
                            ((Polling)self.transport).pause(new Runnable() {
                                @Override
                                public void run() {
                                    if (failed[0]) return;
                                    if (self.readyState == ReadyState.CLOSED || self.readyState == ReadyState.CLOSING) {
                                        return;
                                    }

                                    logger.fine("changing transport and sending upgrade packet");
                                    transport[0].off(Transport.EVENT_ERROR, onerror);
                                    self.emit(EVENT_UPGRADE, transport[0]);
                                    self.setTransport(transport[0]);
                                    Packet packet = new Packet(Packet.UPGRADE);
                                    transport[0].send(new Packet[]{packet});
                                    transport[0] = null;
                                    self.upgrading = false;
                                    self.flush();
                                }
                            });
                        } else {
                            logger.fine(String.format("probe transport '%s' failed", name));
                            EngineIOException err = new EngineIOException("probe error");
                            //err.transport = transport[0].name;
                            self.emit(EVENT_ERROR, err);
                        }
                    }
                });
            }
        });

        transport[0].once(Transport.EVENT_ERROR, onerror);

        this.once(EVENT_CLOSE, new Listener() {
            @Override
            public void call(Object... args) {
                if (transport[0] != null) {
                    logger.fine("socket closed prematurely - aborting probe");
                    failed[0] = true;
                    transport[0].close();
                    transport[0] = null;
                }
            }
        });

        this.once(EVENT_UPGRADING, new Listener() {
            @Override
            public void call(Object... args) {
                Transport to = (Transport)args[0];
                if (transport[0] != null && !to.name.equals(transport[0].name)) {
                    logger.fine(String.format("'%s' works - aborting '%s'", to.name, transport[0].name));
                    transport[0].close();
                    transport[0] = null;
                }
            }
        });

        transport[0].open();
    }

    private void onOpen() {
        logger.fine("socket open");
        this.readyState = ReadyState.OPEN;
        this.emit(EVENT_OPEN);
        this.onopen();
        this.flush();

        if (this.readyState == ReadyState.OPEN && this.upgrade && this.transport instanceof Polling) {
            logger.fine("starting upgrade probes");
            for (String upgrade: this.upgrades) {
                this.probe(upgrade);
            }
        }
    }

    private void onPacket(Packet packet) {
        if (this.readyState == ReadyState.OPENING || this.readyState == ReadyState.OPEN) {
            logger.fine(String.format("socket received: type '%s', data '%s'", packet.type, packet.data));

            this.emit(EVENT_PACKET, packet);
            this.emit(EVENT_HEARTBEAT);

            if (Packet.OPEN.equals(packet.type)) {
                this.onHandshake(gson.fromJson(packet.data, HandshakeData.class));
            } else if (Packet.PONG.equals(packet.type)) {
                this.ping();
            } else if (Packet.ERROR.equals(packet.type)) {
                // TODO: handle error
                EngineIOException err = new EngineIOException("server error");
                //err.code = packet.data;
                this.emit(EVENT_ERROR, err);
            } else if (Packet.MESSAGE.equals(packet.type)) {
                this.emit(EVENT_DATA, packet.data);
                this.emit(EVENT_MESSAGE, packet.data);
                this.onmessage(packet.data);
            }
        } else {
            logger.fine(String.format("packet received with socket readyState '%s'", this.readyState));
        }
    }

    private void onHandshake(HandshakeData data) {
        this.emit(EVENT_HANDSHAKE, data);
        this.id = data.sid;
        this.transport.query.put("sid", data.sid);
        this.upgrades = this.filterUpgrades(data.upgrades);
        this.pingInterval = data.pingInterval;
        this.pingTimeout = data.pingTimeout;
        this.onOpen();
        this.ping();

        this.off(EVENT_HEARTBEAT, this.onHeartbeatAsListener);
        this.on(EVENT_HEARTBEAT, this.onHeartbeatAsListener);
    }

    private final Listener onHeartbeatAsListener = new Listener() {
        @Override
        public void call(Object... args) {
            Socket.this.onHeartbeat(args.length > 0 ? (Long)args[0]: 0);
        }
    };

    private void onHeartbeat(long timeout) {
        if (this.pingTimeoutTimer != null) {
            pingTimeoutTimer.cancel(true);
        }

        if (timeout <= 0) {
            timeout = this.pingInterval + this.pingTimeout;
        }

        final Socket self = this;
        this.pingTimeoutTimer = this.heartbeatScheduler.schedule(new Runnable() {
            @Override
            public void run() {
                EventThread.exec(new Runnable() {
                    @Override
                    public void run() {
                        if (self.readyState == ReadyState.CLOSED) return;
                        self.onClose("ping timeout");
                    }
                });
            }
        }, timeout, TimeUnit.MILLISECONDS);
    }

    private void ping() {
        if (this.pingIntervalTimer != null) {
            pingIntervalTimer.cancel(true);
        }

        final Socket self = this;
        this.pingIntervalTimer = this.heartbeatScheduler.schedule(new Runnable() {
            @Override
            public void run() {
                EventThread.exec(new Runnable() {
                    @Override
                    public void run() {
                        logger.fine(String.format("writing ping packet - expecting pong within %sms", self.pingTimeout));
                        self.sendPacket(Packet.PING);
                        self.onHeartbeat(self.pingTimeout);
                    }
                });
            }
        }, this.pingInterval, TimeUnit.MILLISECONDS);
    }

    private void onDrain() {
        this.callbacks();
        for (int i = 0; i < this.prevBufferLen; i++) {
            this.writeBuffer.poll();
            this.callbackBuffer.poll();
        }

        this.prevBufferLen = 0;
        if (this.writeBuffer.size() == 0) {
            this.emit(EVENT_DRAIN);
        } else {
            this.flush();
        }
    }

    private void callbacks() {
        Iterator<Runnable> iter = this.callbackBuffer.iterator();
        for (int i = 0; i < this.prevBufferLen && iter.hasNext(); i++) {
            Runnable callback = iter.next();
            if (callback != null) {
                callback.run();
            }
        }
    }

    private void flush() {
        if (this.readyState != ReadyState.CLOSED && this.transport.writable &&
                !this.upgrading && this.writeBuffer.size() != 0) {
            logger.fine(String.format("flushing %d packets in socket", this.writeBuffer.size()));
            this.prevBufferLen = this.writeBuffer.size();
            this.transport.send(this.writeBuffer.toArray(new Packet[this.writeBuffer.size()]));
            this.emit(EVENT_FLUSH);
        }
    }

    public void write(String msg) {
        this.write(msg, null);
    }

    public void write(String msg, Runnable fn) {
        this.send(msg, fn);
    }

    /**
     * Sends a message.
     *
     * @param msg
     */
    public void send(String msg) {
        this.send(msg, null);
    }

    /**
     * Sends a message.
     *
     * @param msg
     * @param fn callback to be called on drain
     */
    public void send(final String msg, final Runnable fn) {
        EventThread.exec(new Runnable() {
            @Override
            public void run() {
                Socket.this.sendPacket(Packet.MESSAGE, msg, fn);
            }
        });
    }

    private void sendPacket(String type) {
        this.sendPacket(type, null, null);
    }

    private void sendPacket(String type, String data, Runnable fn) {
        if (fn == null) {
            // ConcurrentLinkedList does not permit `null`.
            fn = noop;
        }

        Packet packet = new Packet(type, data);
        this.emit(EVENT_PACKET_CREATE, packet);
        this.writeBuffer.offer(packet);
        this.callbackBuffer.offer(fn);
        this.flush();
    }

    /**
     * Disconnects the client.
     *
     * @return a reference to to this object.
     */
    public Socket close() {
        EventThread.exec(new Runnable() {
            @Override
            public void run() {
                if (Socket.this.readyState == ReadyState.OPENING || Socket.this.readyState == ReadyState.OPEN) {
                    Socket.this.onClose("forced close");
                    logger.fine("socket closing - telling transport to close");
                    Socket.this.transport.close();
                    Socket.this.transport.off();
                }

            }
        });
        return this;
    }

    private void onError(Exception err) {
        logger.fine(String.format("socket error %s", err));
        this.emit(EVENT_ERROR, err);
        this.onClose("transport error", err);
    }

    private void onClose(String reason) {
        this.onClose(reason, null);
    }

    private void onClose(String reason, Exception desc) {
        if (this.readyState == ReadyState.OPENING || this.readyState == ReadyState.OPEN) {
            logger.fine(String.format("socket close with reason: %s", reason));
            if (this.pingIntervalTimer != null) {
                this.pingIntervalTimer.cancel(true);
            }
            if (this.pingTimeoutTimer != null) {
                this.pingTimeoutTimer.cancel(true);
            }
            EventThread.nextTick(new Runnable() {
                @Override
                public void run() {
                    Socket.this.writeBuffer.clear();
                    Socket.this.callbackBuffer.clear();
                }
            });
            this.readyState = ReadyState.CLOSED;
            this.emit(EVENT_CLOSE, reason, desc);
            this.onclose();
            this.id = null;
        }
    }

    /*package*/ List<String > filterUpgrades(List<String> upgrades) {
        List<String> filteredUpgrades = new ArrayList<String>();
        for (String upgrade : upgrades) {
            if (this.transports.contains(upgrade)) {
                filteredUpgrades.add(upgrade);
            }
        }
        return filteredUpgrades;
    }

    public abstract void onopen();

    public abstract void onmessage(String data);

    public abstract void onclose();


    public static class Options extends Transport.Options {

        /**
         * List of transport names.
         */
        public String[] transports;

        /**
         * Whether to upgrade the transport. Defaults to `true`.
         */
        public boolean upgrade = true;

        public String host;
        public String query;


        private static Options fromURI(URI uri, Options opts) {
            if (opts == null) {
                opts = new Options();
            }

            opts.host = uri.getHost();
            opts.secure = "https".equals(uri.getScheme()) || "wss".equals(uri.getScheme());
            opts.port = uri.getPort();

            String query = uri.getRawQuery();
            if (query != null) {
                opts.query = query;
            }

            return opts;
        }
    }

    public static class Sockets extends ArrayList<Socket> {

        public static final String EVENT_ADD = "add";

        public Emitter evs = new Emitter();
    }
}
