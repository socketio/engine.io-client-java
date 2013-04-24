package com.github.nkzawa.engineio.client;

import com.github.nkzawa.emitter.Emitter;
import com.github.nkzawa.engineio.client.transports.Polling;
import com.github.nkzawa.engineio.client.transports.PollingXHR;
import com.github.nkzawa.engineio.client.transports.WebSocket;
import com.github.nkzawa.engineio.parser.Packet;
import com.github.nkzawa.engineio.parser.Parser;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;

public abstract class Socket extends Emitter {

    private static final Logger logger = Logger.getLogger("engine.io-client:socket");

    private static final int OPENING = 0;
    private static final int OPEN = 1;
    private static final int CLOSING = 2;
    private static final int CLOSED = 3;
    private static final Map<Integer, String> STATE_MAP = new HashMap<Integer, String>() {{
        put(OPENING, "opening");
        put(OPEN, "open");
        put(CLOSING, "closing");
        put(CLOSED, "closed");
    }};

    public static final String EVENT_OPEN = "open";
    public static final String EVENT_CLOSE = "close";
    public static final String EVENT_HANDSHAKE = "handshake";
    public static final String EVENT_UPGRADING = "upgrading";
    public static final String EVENT_UPGRADE = "upgrade";
    public static final String EVENT_PACKET = "packet";
    public static final String EVENT_PACKET_CREATE = "packetCreate";
    public static final String EVENT_HEARTBEAT = "heartbeat";
    public static final String EVENT_DATA = "data";
    public static final String EVENT_MESSAGE = "message";
    public static final String EVENT_ERROR = "error";
    public static final String EVENT_DRAIN = "drain";
    public static final String EVENT_FLUSH = "flush";

    private static final Runnable noop = new Runnable() {
        @Override
        public void run() {}
    };

    public static final Sockets sockets = new Sockets();
    public static final int protocol = Parser.protocol;

    private boolean secure;
    private boolean upgrade;
    private boolean timestampRequests;
    private boolean upgrading;
    private int port;
    private int policyPort;
    private int prevBufferLen;
    private int readyState = -1;
    private long pingInterval;
    private long pingTimeout;
    private String id;
    private String hostname;
    private String path;
    private String timestampParam;
    private List<String> transports;
    private List<String> upgrades;
    private Map<String, String> query;
    private ConcurrentLinkedQueue<Packet> writeBuffer = new ConcurrentLinkedQueue<Packet>();
    private ConcurrentLinkedQueue<Runnable> callbackBuffer = new ConcurrentLinkedQueue<Runnable>();
    private Transport transport;
    private Future pingTimeoutTimer;
    private Future pingIntervalTimer;

    private ScheduledExecutorService heartbeatScheduler = Executors.newSingleThreadScheduledExecutor();


    public Socket(String uri) throws URISyntaxException {
        this(uri, null);
    }

    public Socket(URI uri) {
        this(uri, null);
    }

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
        this.transports = new ArrayList<String>(Arrays.asList(
                opts.transports != null ? opts.transports : new String[] {"polling", "websocket"}));
        this.policyPort = opts.policyPort != 0 ? opts.policyPort : 843;

        Socket.sockets.add(this);
        Socket.sockets.evs.emit(Sockets.EVENT_ADD, this);
    }

    public void open() {
        this.readyState = OPENING;
        Transport transport = this.createTransport(this.transports.get(0));
        this.setTransport(transport);
        transport.open();
    }

    private Transport createTransport(String name) {
        logger.info(String.format("creating transport '%s'", name));
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

        if ("websocket".equals(name)) {
            return new WebSocket(opts);
        } else if ("polling".equals(name)) {
            return new PollingXHR(opts);
        }

        throw new RuntimeException();
    }

    private void setTransport(Transport transport) {
        final Socket self = this;

        if (this.transport != null) {
            logger.info("clearing existing transport");
            this.transport.off();
        }

        this.transport = transport;

        transport.on("drain", new Listener() {
            @Override
            public void call(Object... args) {
                self.onDrain();
            }
        }).on("packet", new Listener() {
            @Override
            public void call(Object... args) {
                self.onPacket(args.length > 0 ? (Packet) args[0] : null);
            }
        }).on("error", new Listener() {
            @Override
            public void call(Object... args) {
                self.onError(args.length > 0 ? (Exception) args[0] : null);
            }
        }).on("close", new Listener() {
            @Override
            public void call(Object... args) {
                self.onClose("transport close");
            }
        });
    }

    private void probe(final String name) {
        logger.info(String.format("probing transport '%s'", name));
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
                logger.info(String.format("probing transport '%s' failed because of error: %s", name, err));
                self.emit(EVENT_ERROR, error);
            }
        };

        transport[0].once("open", new Listener() {
            @Override
            public void call(Object... args) {
                if (failed[0]) return;

                logger.info(String.format("probe transport '%s' opened", name));
                Packet packet = new Packet("ping", "probe");
                transport[0].send(new Packet[] {packet});
                transport[0].once("packet", new Listener() {
                    @Override
                    public void call(Object... args) {
                        if (failed[0]) return;
                        Packet msg = (Packet)args[0];
                        if ("pong".equals(msg.type) && "probe".equals(msg.data)) {
                            logger.info(String.format("probe transport '%s' pong", name));
                            self.upgrading = true;
                            self.emit(EVENT_UPGRADING, transport[0]);

                            logger.info(String.format("pausing current transport '%s'", self.transport.name));
                            ((Polling)self.transport).pause(new Runnable() {
                                @Override
                                public void run() {
                                    if (failed[0]) return;
                                    if (self.readyState == CLOSED || self.readyState == CLOSING) {
                                        return;
                                    }

                                    logger.info("changing transport and sending upgrade packet");
                                    transport[0].off("error", onerror);
                                    self.emit(EVENT_UPGRADE, transport);
                                    self.setTransport(transport[0]);
                                    Packet packet = new Packet("upgrade", null);
                                    transport[0].send(new Packet[]{packet});
                                    transport[0] = null;
                                    self.upgrading = false;
                                    self.flush();
                                }
                            });
                        } else {
                            logger.info(String.format("probe transport '%s' failed", name));
                            EngineIOException err = new EngineIOException("probe error");
                            //err.transport = transport[0].name;
                            self.emit(EVENT_ERROR, err);
                        }
                    }
                });
            }
        });

        transport[0].once("error", onerror);

        this.once("close", new Listener() {
            @Override
            public void call(Object... args) {
                if (transport[0] != null) {
                    logger.info("socket closed prematurely - aborting probe");
                    failed[0] = true;
                    transport[0].close();
                    transport[0] = null;
                }
            }
        });

        this.once("upgrading", new Listener() {
            @Override
            public void call(Object... args) {
                Transport to = (Transport)args[0];
                if (transport[0] != null && !to.name.equals(transport[0].name)) {
                    logger.info(String.format("'%s' works - aborting '%s'", to.name, transport[0].name));
                    transport[0].close();
                    transport[0] = null;
                }
            }
        });

        transport[0].open();
    }

    private void onOpen() {
        logger.info("socket open");
        this.readyState = OPEN;
        this.emit(EVENT_OPEN);
        this.onopen();
        this.flush();

        if (this.readyState == OPEN && this.upgrade && this.transport instanceof Polling) {
            logger.info("starting upgrade probes");
            for (String upgrade: this.upgrades) {
                this.probe(upgrade);
            }
        }
    }

    private void onPacket(Packet packet) {
        if (this.readyState == OPENING || this.readyState == OPEN) {
            logger.info(String.format("socket received: type '%s', data '%s'", packet.type, packet.data));

            this.emit(EVENT_PACKET, packet);
            this.emit(EVENT_HEARTBEAT);

            if ("open".equals(packet.type)) {
                this.onHandshake(new JsonParser().parse(packet.data).getAsJsonObject());
            } else if ("pong".equals(packet.type)) {
                this.ping();
            } else if ("error".equals(packet.type)) {
                // TODO: handle error
                EngineIOException err = new EngineIOException("server error");
                //err.code = packet.data;
                this.emit(EVENT_ERROR, err);
            } else if ("message".equals(packet.type)) {
                this.emit(EVENT_DATA, packet.data);
                this.emit(EVENT_MESSAGE, packet.data);
                this.onmessage(packet.data);
            }
        } else {
            logger.info(String.format("packet received with socket readyState '%s'", STATE_MAP.get(this.readyState)));
        }
    }

    private void onHandshake(JsonObject data) {
        this.emit(EVENT_HANDSHAKE, data);
        this.id = data.get("sid").getAsString();
        this.transport.query.put("sid", data.get("sid").getAsString());

        List<String> upgrades = new ArrayList<String>();
        for (JsonElement upgrade : data.get("upgrades").getAsJsonArray()) {
            upgrades.add(upgrade.getAsString());
        }
        this.upgrades = this.filterUpgrades(upgrades);

        this.pingInterval = data.get("pingInterval").getAsLong();
        this.pingTimeout = data.get("pingTimeout").getAsLong();
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

    private synchronized void onHeartbeat(long timeout) {
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
                if (self.readyState == CLOSED) return;
                self.onClose("ping timeout");
            }
        }, timeout, TimeUnit.MILLISECONDS);
    }

    private synchronized void ping() {
        if (this.pingIntervalTimer != null) {
            pingIntervalTimer.cancel(true);
        }

        final Socket self = this;
        this.pingIntervalTimer = this.heartbeatScheduler.schedule(new Runnable() {
            @Override
            public void run() {
                logger.info(String.format("writing ping packet - expecting pong within %sms", self.pingTimeout));
                self.sendPacket("ping");
                self.onHeartbeat(self.pingTimeout);
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
        if (this.readyState != CLOSED && this.transport.writable &&
                !this.upgrading && this.writeBuffer.size() != 0) {
            logger.info(String.format("flushing %d packets in socket", this.writeBuffer.size()));
            this.prevBufferLen = this.writeBuffer.size();
            this.transport.send(this.writeBuffer.toArray(new Packet[0]));
            this.emit(EVENT_FLUSH);
        }
    }

    public void write(String msg) {
        this.write(msg, null);
    }

    public void write(String msg, Runnable fn) {
        this.send(msg, fn);
    }

    public void send(String msg) {
        this.send(msg, null);
    }

    public void send(String msg, Runnable fn) {
        this.sendPacket("message", msg, fn);
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

    public Socket close() {
        if (this.readyState == OPENING || this.readyState == OPEN) {
            this.onClose("forced close");
            logger.info("socket closing - telling transport to close");
            this.transport.close();
            this.transport.off();
        }

        return this;
    }

    private void onError(Exception err) {
        logger.info(String.format("socket error %s", err));
        this.emit(EVENT_ERROR, err);
        this.onClose("transport error", err);
    }

    private void onClose(String reason) {
        this.onClose(reason, null);
    }

    private void onClose(String reason, Exception desc) {
        if (this.readyState == OPENING || this.readyState == OPEN) {
            logger.info(String.format("socket close with reason: %s", reason));
            if (this.pingIntervalTimer != null) {
                this.pingIntervalTimer.cancel(true);
            }
            if (this.pingTimeoutTimer != null) {
                this.pingTimeoutTimer.cancel(true);
            }
            this.readyState = CLOSED;
            this.emit(EVENT_CLOSE, reason, desc);
            this.onclose();
            // TODO:
            // clean buffer in next tick, so developers can still
            // grab the buffers on `close` event
            // setTimeout(function() {}
            //   self.writeBuffer = [];
            //   self.callbackBuffer = [];
            // );
            this.writeBuffer.clear();
            this.callbackBuffer.clear();
            this.id = null;
        }
    }

    private List<String > filterUpgrades(List<String> upgrades) {
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

        public String host;
        public String query;
        public String[] transports;
        public boolean upgrade = true;


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

    public static class Sockets extends ConcurrentLinkedQueue<Socket> {

        public static final String EVENT_ADD = "add";

        public Emitter evs = new Emitter();
    }
}
