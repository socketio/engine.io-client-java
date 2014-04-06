package com.github.nkzawa.engineio.client;


import org.json.JSONArray;
import org.json.JSONObject;

public class HandshakeData {

    public String sid;
    public String[] upgrades;
    public long pingInterval;
    public long pingTimeout;

    /*package*/ HandshakeData(JSONObject data) {
        JSONArray upgrades = data.getJSONArray("upgrades");
        int length = upgrades.length();
        String[] _upgrades = new String[length];
        for (int i = 0; i < length; i ++) {
            _upgrades[i] = upgrades.getString(i);
        }

        this.sid = data.getString("sid");
        this.upgrades = _upgrades;
        this.pingInterval = data.getLong("pingInterval");
        this.pingTimeout = data.getLong("pingTimeout");
    }
}
