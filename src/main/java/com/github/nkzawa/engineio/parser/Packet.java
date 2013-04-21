package com.github.nkzawa.engineio.parser;


public class Packet {

    public String type;
    public String data;

    public Packet(String type, String data) {
        this.type = type;
        this.data = data;
    }

    @Override
    public String toString() {
        return String.format("{\"type\": \"%s\", \"data\": \"%s\"}", this.type, this.data);
    }

}
