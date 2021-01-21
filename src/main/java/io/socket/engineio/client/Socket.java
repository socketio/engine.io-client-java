package io.socket.engineio.client;

import org.json.JSONException;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.socket.emitter.Emitter;
import io.socket.engineio.client.transports.Polling;
import io.socket.engineio.client.transports.PollingXHR;
import io.socket.engineio.client.transports.WebSocket;
import io.socket.engineio.parser.Packet;
import io.socket.engineio.parser.Parser;
import io.socket.parseqs.ParseQS;
import io.socket.thread.EventThread;
import okhttp3.OkHttpClient;


/**
 * The socket class for Event.IO Client.
 *
 * @see <a href="https://github.com/LearnBoost/engine.io-client">https://github.com/LearnBoost/engine.io-client</a>
 */
public class Socket extends Emitter {

    private static final Logger logger = Logger.getLogger(Socket.class.getName());

    private static final AtomicInteger HEARTBEAT_THREAD_COUNTER = new AtomicInteger();

    private static final String PROBE_ERROR = "probe error";


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

    public static final String EVENT_UPGRADE_ERROR = "upgradeError";

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
    public static final String EVENT_PING = "ping";
    public static final String EVENT_PONG = "pong";

    /**
     * Called on a new transport is created.
     */
    public static final String EVENT_TRANSPORT = "transport";

    /**
     * The protocol version.
     */
    public static final int PROTOCOL = Parser.PROTOCOL;

    private static boolean priorWebsocketSuccess = false;

    private static okhttp3.WebSocket.Factory defaultWebSocketFactory;
    private static okhttp3.Call.Factory defaultCallFactory;
    private static OkHttpClient defaultOkHttpClient;

    private boolean secure;
    private boolean upgrade;
    private boolean timestampRequests;
    private boolean upgrading;
    private boolean rememberUpgrade;
    /*package*/ int port;
    private int policyPort;
    private int prevBufferLen;
    private long pingInterval;
    private long pingTimeout;
    private String id;
    /*package*/ String hostname;
    private String path;
    private String timestampParam;
    private List<String> transports;
    private Map<String, Transport.Options> transportOptions;
    private List<String> upgrades;
    private Map<String, String> query;
    /*package*/ LinkedList<Packet> writeBuffer = new LinkedList<Packet>();
    /*package*/ Transport transport;
    private Future pingTimeoutTimer;
    private okhttp3.WebSocket.Factory webSocketFactory;
    private okhttp3.Call.Factory callFactory;
    private final Map<String, List<String>> extraHeaders;

    private ReadyState readyState;
    private ScheduledExecutorService heartbeatScheduler;
    private final Listener onHeartbeatAsListener = new Listener() {
        @Override
        public void call(Object... args) {
            Socket.this.onHeartbeat();
        }
    };

    public Socket() {
        this(new Options());
    }

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
        this(uri == null ? null : new URI(uri), opts);
    }

    public Socket(URI uri, Options opts) {
        this(uri == null ? opts : Options.fromURI(uri, opts));
    }

    public Socket(Options opts) {
        if (opts.host != null) {
            String hostname = opts.host;
            boolean ipv6 = hostname.split(":").length > 2;
            if (ipv6) {
                int start = hostname.indexOf('[');
                if (start != -1) hostname = hostname.substring(start + 1);
                int end = hostname.lastIndexOf(']');
                if (end != -1) hostname = hostname.substring(0, end);
            }
            opts.hostname = hostname;
        }

        this.secure = opts.secure;

        if (opts.port == -1) {
            // if no port is specified manually, use the protocol default
            opts.port = this.secure ? 443 : 80;
        }

        this.hostname = opts.hostname != null ? opts.hostname : "localhost";
        this.port = opts.port;
        this.query = opts.query != null ?
                ParseQS.decode(opts.query) : new HashMap<String, String>();
        this.upgrade = opts.upgrade;
        this.path = (opts.path != null ? opts.path : "/engine.io").replaceAll("/$", "") + "/";
        this.timestampParam = opts.timestampParam != null ? opts.timestampParam : "t";
        this.timestampRequests = opts.timestampRequests;
        this.transports = new ArrayList<String>(Arrays.asList(opts.transports != null ?
                opts.transports : new String[]{Polling.NAME, WebSocket.NAME}));
        this.transportOptions = opts.transportOptions != null ?
                opts.transportOptions : new HashMap<String, Transport.Options>();
        this.policyPort = opts.policyPort != 0 ? opts.policyPort : 843;
        this.rememberUpgrade = opts.rememberUpgrade;
        this.callFactory = opts.callFactory != null ? opts.callFactory : defaultCallFactory;
        this.webSocketFactory = opts.webSocketFactory != null ? opts.webSocketFactory : defaultWebSocketFactory;
        if (callFactory == null) {
            if (defaultOkHttpClient == null) {
                defaultOkHttpClient = new OkHttpClient();
            }
            callFactory = defaultOkHttpClient;
        }
        if (webSocketFactory == null) {
            if (defaultOkHttpClient == null) {
                defaultOkHttpClient = new OkHttpClient();
            }
            webSocketFactory = defaultOkHttpClient;
        }
        this.extraHeaders = opts.extraHeaders;
    }

    public static void setDefaultOkHttpWebSocketFactory(okhttp3.WebSocket.Factory factory) {
        defaultWebSocketFactory = factory;
    }

    public static void setDefaultOkHttpCallFactory(okhttp3.Call.Factory factory) {
        defaultCallFactory = factory;
    }

    /**
     * Connects the client.
     *
     * @return a reference to to this object.
     */
    public Socket open() {
        EventThread.exec(new Runnable() {
            @Override
            public void run() {
                String transportName;
                if (Socket.this.rememberUpgrade && Socket.priorWebsocketSuccess && Socket.this.transports.contains(WebSocket.NAME)) {
                    transportName = WebSocket.NAME;
                } else if (0 == Socket.this.transports.size()) {
                    // Emit error on next tick so it can be listened to
                    final Socket self = Socket.this;
                    EventThread.nextTick(new Runnable() {
                        @Override
                        public void run() {
                            self.emit(Socket.EVENT_ERROR, new EngineIOException("No transports available"));
                        }
                    });
                    return;
                } else {
                    transportName = Socket.this.transports.get(0);
                }
                Socket.this.readyState = ReadyState.OPENING;
                Transport transport = Socket.this.createTransport(transportName);
                Socket.this.setTransport(transport);
                transport.open();
            }
        });
        return this;
    }

    private Transport createTransport(String name) {
        if (logger.isLoggable(Level.FINE)) {
            logger.fine(String.format("creating transport '%s'", name));
        }
        Map<String, String> query = new HashMap<String, String>(this.query);

        query.put("EIO", String.valueOf(Parser.PROTOCOL));
        query.put("transport", name);
        if (this.id != null) {
            query.put("sid", this.id);
        }

        // per-transport options
        Transport.Options options = this.transportOptions.get(name);

        Transport.Options opts = new Transport.Options();
        opts.query = query;
        opts.socket = this;

        opts.hostname = options != null ? options.hostname : this.hostname;
        opts.port = options != null ? options.port : this.port;
        opts.secure = options != null ? options.secure : this.secure;
        opts.path = options != null ? options.path : this.path;
        opts.timestampRequests = options != null ? options.timestampRequests : this.timestampRequests;
        opts.timestampParam = options != null ? options.timestampParam : this.timestampParam;
        opts.policyPort = options != null ? options.policyPort : this.policyPort;
        opts.callFactory = options != null ? options.callFactory : this.callFactory;
        opts.webSocketFactory = options != null ? options.webSocketFactory : this.webSocketFactory;
        opts.extraHeaders = this.extraHeaders;

        Transport transport;
        if (WebSocket.NAME.equals(name)) {
            transport = new WebSocket(opts);
        } else if (Polling.NAME.equals(name)) {
            transport = new PollingXHR(opts);
        } else {
            throw new RuntimeException();
        }

        this.emit(EVENT_TRANSPORT, transport);

        return transport;
    }

    private void setTransport(Transport transport) {
        if (logger.isLoggable(Level.FINE)) {
            logger.fine(String.format("setting transport %s", transport.name));
        }
        final Socket self = this;

        if (this.transport != null) {
            if (logger.isLoggable(Level.FINE)) {
                logger.fine(String.format("clearing existing transport %s", this.transport.name));
            }
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
        if (logger.isLoggable(Level.FINE)) {
            logger.fine(String.format("probing transport '%s'", name));
        }
        final Transport[] transport = new Transport[] {this.createTransport(name)};
        final boolean[] failed = new boolean[] {false};
        final Socket self = this;

        Socket.priorWebsocketSuccess = false;

        final Runnable[] cleanup = new Runnable[1];

        final Listener onTransportOpen = new Listener() {
            @Override
            public void call(Object... args) {
                if (failed[0]) return;

                if (logger.isLoggable(Level.FINE)) {
                    logger.fine(String.format("probe transport '%s' opened", name));
                }
                Packet<String> packet = new Packet<String>(Packet.PING, "probe");
                transport[0].send(new Packet[] {packet});
                transport[0].once(Transport.EVENT_PACKET, new Listener() {
                    @Override
                    public void call(Object... args) {
                        if (failed[0]) return;

                        Packet msg = (Packet)args[0];
                        if (Packet.PONG.equals(msg.type) && "probe".equals(msg.data)) {
                            if (logger.isLoggable(Level.FINE)) {
                                logger.fine(String.format("probe transport '%s' pong", name));
                            }
                            self.upgrading = true;
                            self.emit(EVENT_UPGRADING, transport[0]);
                            if (null == transport[0]) return;
                            Socket.priorWebsocketSuccess = WebSocket.NAME.equals(transport[0].name);

                            if (logger.isLoggable(Level.FINE)) {
                                logger.fine(String.format("pausing current transport '%s'", self.transport.name));
                            }
                            ((Polling)self.transport).pause(new Runnable() {
                                @Override
                                public void run() {
                                    if (failed[0]) return;
                                    if (ReadyState.CLOSED == self.readyState) return;

                                    logger.fine("changing transport and sending upgrade packet");

                                    cleanup[0].run();

                                    self.setTransport(transport[0]);
                                    Packet packet = new Packet(Packet.UPGRADE);
                                    transport[0].send(new Packet[]{packet});
                                    self.emit(EVENT_UPGRADE, transport[0]);
                                    transport[0] = null;
                                    self.upgrading = false;
                                    self.flush();
                                }
                            });
                        } else {
                            if (logger.isLoggable(Level.FINE)) {
                                logger.fine(String.format("probe transport '%s' failed", name));
                            }
                            EngineIOException err = new EngineIOException(PROBE_ERROR);
                            err.transport = transport[0].name;
                            self.emit(EVENT_UPGRADE_ERROR, err);
                        }
                    }
                });
            }
        };

        final Listener freezeTransport = new Listener() {
            @Override
            public void call(Object... args) {
                if (failed[0]) return;

                failed[0] = true;

                cleanup[0].run();

                transport[0].close();
                transport[0] = null;
            }
        };

        // Handle any error that happens while probing
        final Listener onerror = new Listener() {
            @Override
            public void call(Object... args) {
                Object err = args[0];
                EngineIOException error;
                if (err instanceof Exception) {
                    error = new EngineIOException(PROBE_ERROR, (Exception)err);
                } else if (err instanceof String) {
                    error = new EngineIOException("probe error: " + (String)err);
                } else {
                    error = new EngineIOException(PROBE_ERROR);
                }
                error.transport = transport[0].name;

                freezeTransport.call();

                if (logger.isLoggable(Level.FINE)) {
                    logger.fine(String.format("probe transport \"%s\" failed because of error: %s", name, err));
                }

                self.emit(EVENT_UPGRADE_ERROR, error);
            }
        };

        final Listener onTransportClose = new Listener() {
            @Override
            public void call(Object... args) {
                onerror.call("transport closed");
            }
        };

        // When the socket is closed while we're probing
        final Listener onclose = new Listener() {
            @Override
            public void call(Object... args) {
                onerror.call("socket closed");
            }
        };

        // When the socket is upgraded while we're probing
        final Listener onupgrade = new Listener() {
            @Override
            public void call(Object... args) {
                Transport to = (Transport)args[0];
                if (transport[0] != null && !to.name.equals(transport[0].name)) {
                    if (logger.isLoggable(Level.FINE)) {
                        logger.fine(String.format("'%s' works - aborting '%s'", to.name, transport[0].name));
                    }
                    freezeTransport.call();
                }
            }
        };

        cleanup[0] = new Runnable() {
            @Override
            public void run() {
                transport[0].off(Transport.EVENT_OPEN, onTransportOpen);
                transport[0].off(Transport.EVENT_ERROR, onerror);
                transport[0].off(Transport.EVENT_CLOSE, onTransportClose);
                self.off(EVENT_CLOSE, onclose);
                self.off(EVENT_UPGRADING, onupgrade);
            }
        };

        transport[0].once(Transport.EVENT_OPEN, onTransportOpen);
        transport[0].once(Transport.EVENT_ERROR, onerror);
        transport[0].once(Transport.EVENT_CLOSE, onTransportClose);

        this.once(EVENT_CLOSE, onclose);
        this.once(EVENT_UPGRADING, onupgrade);

        transport[0].open();
    }

    private void onOpen() {
        logger.fine("socket open");
        this.readyState = ReadyState.OPEN;
        Socket.priorWebsocketSuccess = WebSocket.NAME.equals(this.transport.name);
        this.emit(EVENT_OPEN);
        this.flush();

        if (this.readyState == ReadyState.OPEN && this.upgrade && this.transport instanceof Polling) {
            logger.fine("starting upgrade probes");
            for (String upgrade: this.upgrades) {
                this.probe(upgrade);
            }
        }
    }

    private void onPacket(Packet packet) {
        if (this.readyState == ReadyState.OPENING ||
                this.readyState == ReadyState.OPEN ||
                this.readyState == ReadyState.CLOSING) {
            if (logger.isLoggable(Level.FINE)) {
                logger.fine(String.format("socket received: type '%s', data '%s'", packet.type, packet.data));
            }

            this.emit(EVENT_PACKET, packet);
            this.emit(EVENT_HEARTBEAT);

            if (Packet.OPEN.equals(packet.type)) {
                try {
                    this.onHandshake(new HandshakeData((String)packet.data));
                } catch (JSONException e) {
                    this.emit(EVENT_ERROR, new EngineIOException(e));
                }
            } else if (Packet.PING.equals(packet.type)) {
                this.emit(EVENT_PING);
                EventThread.exec(new Runnable() {
                    @Override
                    public void run() {
                        Socket.this.sendPacket(Packet.PONG, null);
                    }
                });
            } else if (Packet.ERROR.equals(packet.type)) {
                EngineIOException err = new EngineIOException("server error");
                err.code = packet.data;
                this.onError(err);
            } else if (Packet.MESSAGE.equals(packet.type)) {
                this.emit(EVENT_DATA, packet.data);
                this.emit(EVENT_MESSAGE, packet.data);
            }
        } else {
            if (logger.isLoggable(Level.FINE)) {
                logger.fine(String.format("packet received with socket readyState '%s'", this.readyState));
            }
        }
    }

    private void onHandshake(HandshakeData data) {
        this.emit(EVENT_HANDSHAKE, data);
        this.id = data.sid;
        this.transport.query.put("sid", data.sid);
        this.upgrades = this.filterUpgrades(Arrays.asList(data.upgrades));
        this.pingInterval = data.pingInterval;
        this.pingTimeout = data.pingTimeout;
        this.onOpen();
        // In case open handler closes socket
        if (ReadyState.CLOSED == this.readyState) return;
        this.onHeartbeat();

        this.off(EVENT_HEARTBEAT, this.onHeartbeatAsListener);
        this.on(EVENT_HEARTBEAT, this.onHeartbeatAsListener);
    }

    private void onHeartbeat() {
        if (this.pingTimeoutTimer != null) {
            pingTimeoutTimer.cancel(false);
        }

        long timeout = this.pingInterval + this.pingTimeout;

        final Socket self = this;
        this.pingTimeoutTimer = this.getHeartbeatScheduler().schedule(new Runnable() {
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

    private void onDrain() {
        for (int i = 0; i < this.prevBufferLen; i++) {
            this.writeBuffer.poll();
        }

        this.prevBufferLen = 0;
        if (0 == this.writeBuffer.size()) {
            this.emit(EVENT_DRAIN);
        } else {
            this.flush();
        }
    }

    private void flush() {
        if (this.readyState != ReadyState.CLOSED && this.transport.writable &&
                !this.upgrading && this.writeBuffer.size() != 0) {
            if (logger.isLoggable(Level.FINE)) {
                logger.fine(String.format("flushing %d packets in socket", this.writeBuffer.size()));
            }
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

    public void write(byte[] msg) {
        this.write(msg, null);
    }

    public void write(byte[] msg, Runnable fn) {
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

    public void send(byte[] msg) {
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

    public void send(final byte[] msg, final Runnable fn) {
        EventThread.exec(new Runnable() {
            @Override
            public void run() {
                Socket.this.sendPacket(Packet.MESSAGE, msg, fn);
            }
        });
    }

    private void sendPacket(String type, Runnable fn) {
        this.sendPacket(new Packet(type), fn);
    }

    private void sendPacket(String type, String data, Runnable fn) {
        Packet<String> packet = new Packet<String>(type, data);
        sendPacket(packet, fn);
    }

    private void sendPacket(String type, byte[] data, Runnable fn) {
        Packet<byte[]> packet = new Packet<byte[]>(type, data);
        sendPacket(packet, fn);
    }

    private void sendPacket(Packet packet, final Runnable fn) {
        if (ReadyState.CLOSING == this.readyState || ReadyState.CLOSED == this.readyState) {
            return;
        }

        this.emit(EVENT_PACKET_CREATE, packet);
        this.writeBuffer.offer(packet);
        if (null != fn) {
            this.once(EVENT_FLUSH, new Listener() {
                @Override
                public void call(Object... args) {
                    fn.run();
                }
            });
        }
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
                    Socket.this.readyState = ReadyState.CLOSING;

                    final Socket self = Socket.this;

                    final Runnable close = new Runnable() {
                        @Override
                        public void run() {
                            self.onClose("forced close");
                            logger.fine("socket closing - telling transport to close");
                            self.transport.close();
                        }
                    };

                    final Listener[] cleanupAndClose = new Listener[1];
                    cleanupAndClose[0] = new Listener() {
                        @Override
                        public void call(Object ...args) {
                            self.off(EVENT_UPGRADE, cleanupAndClose[0]);
                            self.off(EVENT_UPGRADE_ERROR, cleanupAndClose[0]);
                            close.run();
                        }
                    };

                    final Runnable waitForUpgrade = new Runnable() {
                        @Override
                        public void run() {
                            // wait for updade to finish since we can't send packets while pausing a transport
                            self.once(EVENT_UPGRADE, cleanupAndClose[0]);
                            self.once(EVENT_UPGRADE_ERROR, cleanupAndClose[0]);
                        }
                    };

                    if (Socket.this.writeBuffer.size() > 0) {
                        Socket.this.once(EVENT_DRAIN, new Listener() {
                            @Override
                            public void call(Object... args) {
                                if (Socket.this.upgrading) {
                                    waitForUpgrade.run();
                                } else {
                                    close.run();
                                }
                            }
                        });
                    } else if (Socket.this.upgrading) {
                        waitForUpgrade.run();
                    } else {
                        close.run();
                    }
                }
            }
        });
        return this;
    }

    private void onError(Exception err) {
        if (logger.isLoggable(Level.FINE)) {
            logger.fine(String.format("socket error %s", err));
        }
        Socket.priorWebsocketSuccess = false;
        this.emit(EVENT_ERROR, err);
        this.onClose("transport error", err);
    }

    private void onClose(String reason) {
        this.onClose(reason, null);
    }

    private void onClose(String reason, Exception desc) {
        if (ReadyState.OPENING == this.readyState || ReadyState.OPEN == this.readyState || ReadyState.CLOSING == this.readyState) {
            if (logger.isLoggable(Level.FINE)) {
                logger.fine(String.format("socket close with reason: %s", reason));
            }
            final Socket self = this;

            // clear timers
            if (this.pingTimeoutTimer != null) {
                this.pingTimeoutTimer.cancel(false);
            }
            if (this.heartbeatScheduler != null) {
                this.heartbeatScheduler.shutdown();
            }

            // stop event from firing again for transport
            this.transport.off(EVENT_CLOSE);

            // ensure transport won't stay open
            this.transport.close();

            // ignore further transport communication
            this.transport.off();

            // set ready state
            this.readyState = ReadyState.CLOSED;

            // clear session id
            this.id = null;

            // emit close events
            this.emit(EVENT_CLOSE, reason, desc);

            // clear buffers after, so users can still
            // grab the buffers on `close` event
            self.writeBuffer.clear();
            self.prevBufferLen = 0;
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

    public String id() {
        return this.id;
    }

    private ScheduledExecutorService getHeartbeatScheduler() {
        if (this.heartbeatScheduler == null || this.heartbeatScheduler.isShutdown()) {
            this.heartbeatScheduler = createHeartbeatScheduler();
        }
        return this.heartbeatScheduler;
    }

    private ScheduledExecutorService createHeartbeatScheduler() {
        return Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r, "engine.io-client.heartbeat-" + HEARTBEAT_THREAD_COUNTER.getAndIncrement());
                thread.setDaemon(true);
                return thread;
            }
        });
    }

    public static class Options extends Transport.Options {

        /**
         * List of transport names.
         */
        public String[] transports;

        /**
         * Whether to upgrade the transport. Defaults to `true`.
         */
        public boolean upgrade = true;

        public boolean rememberUpgrade;
        public String host;
        public String query;
        public Map<String, Transport.Options> transportOptions;

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
}
