package com.github.nkzawa.engineio.parser;


import java.util.List;

public class HandshakeData {

    public String sid;
    public List<String> upgrades;
    public long pingInterval;
    public long pingTimeout;
}
