package com.github.nkzawa.engineio.client;


import com.github.nkzawa.emitter.Emitter;
import com.github.nkzawa.engineio.parser.Packet;
import com.github.nkzawa.engineio.parser.Parser;
import org.apache.http.NameValuePair;

import java.util.List;

public abstract class Transport extends Emitter {

    public boolean writable;
    public String name;
    public List<NameValuePair> query;

    protected boolean secure;
    protected boolean timestampRequests;
    protected int port;
    protected String path;
    protected String hostname;
    protected String timestampParam;
    protected String readyState = "";


    public Transport(Options opts) {
        this.path = opts.path;
        this.hostname = opts.hostname;
        this.port = opts.port;
        this.secure = opts.secure;
        this.query = opts.query;
        this.timestampParam = opts.timestampParam;
        this.timestampRequests = opts.timestampRequests;
    }

    protected Transport onError(String msg, Exception desc) {
        // TODO: handle error
        Exception err = new EngineIOException(msg, desc);
        this.emit("error", err);
        return this;
    }

    public Emitter open() {
        if ("closed".equals(this.readyState) || "".equals(this.readyState)) {
            this.readyState = "opening";
            this.doOpen();
        }
        return this;
    }

    public Transport close() {
        if ("opening".equals(this.readyState) || "open".equals(this.readyState)) {
            this.doClose();
            this.onClose();
        }
        return this;
    }

    public void send(Packet[] packets) {
        if ("open".equals(this.readyState)) {
            this.write(packets);
        } else {
            throw new RuntimeException("Transport not open");
        }
    }

    protected void onOpen() {
        this.readyState = "open";
        this.writable = true;
        this.emit("open");
    }

    protected void onData(String data) {
        this.onPacket(Parser.decodePacket(data));
    }

    protected void onPacket(Packet packet) {
        this.emit("packet", packet);
    }

    protected void onClose() {
        this.readyState = "closed";
        this.emit("close");
    }

    abstract protected void write(Packet[] packets);

    abstract protected void doOpen();

    abstract protected void doClose();


    public static class Options {

        public String hostname;
        public String path;
        public String timestampParam;
        public boolean secure;
        public boolean timestampRequests;
        public int port;
        public int policyPort;
        public List<NameValuePair> query;

    }
}
