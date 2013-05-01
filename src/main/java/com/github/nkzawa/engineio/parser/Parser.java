package com.github.nkzawa.engineio.parser;


import java.util.HashMap;
import java.util.Map;

public class Parser {

    public static final int protocol = 2;
    private static final Map<String, Integer> packets = new HashMap<String, Integer>() {{
        put(Packet.OPEN, 0);
        put(Packet.CLOSE, 1);
        put(Packet.PING, 2);
        put(Packet.PONG, 3);
        put(Packet.MESSAGE, 4);
        put(Packet.UPGRADE, 5);
        put(Packet.NOOP, 6);
    }};
    private static final Map<Integer, String> bipackets = new HashMap<Integer, String>();
    static {
        for (Map.Entry<String, Integer> entry : packets.entrySet()) {
            bipackets.put(entry.getValue(), entry.getKey());
        }
    }

    private static Packet err = new Packet(Packet.ERROR, "parser error");


    private Parser() {}

    public static String encodePacket(Packet packet) {
        String encoded = String.valueOf(packets.get(packet.type));

        if (packet.data != null) {
            encoded += packet.data;
        }
        return encoded;
    }

    public static Packet decodePacket(String data) {
        int type = -1;
        try {
            type = Character.getNumericValue(data.charAt(0));
        } catch(IndexOutOfBoundsException e) {}
        if (type < 0 || type >= packets.size()) {
            return err;
        }

        return new Packet(bipackets.get(type), data.length() > 1 ? data.substring(1) : null);
    }

    public static String encodePayload(Packet[] packets) {
        if (packets.length == 0) {
            return "0:";
        }

        StringBuilder encoded = new StringBuilder();
        for (Packet packet : packets) {
            String message = encodePacket(packet);
            encoded.append(message.length()).append(":").append(message);
        }

        return encoded.toString();
    }

    public static void decodePayload(String data, DecodePayloadCallback callback) {
        if (data == null || data.isEmpty()) {
            callback.call(err, 0, 1);
            return;
        }

        StringBuilder length = new StringBuilder();
        for (int i = 0, l = data.length(); i < l; i++) {
            char chr = data.charAt(i);

            if (':' != chr) {
                length.append(chr);
            } else {
                int n;
                try {
                    n = Integer.parseInt(length.toString());
                } catch (NumberFormatException e) {
                    callback.call(err, 0, 1);
                    return;
                }

                String msg;
                try {
                    msg = data.substring(i + 1, i + 1 + n);
                } catch (IndexOutOfBoundsException e) {
                    callback.call(err, 0, 1);
                    return;
                }

                if (msg.length() != 0) {
                    Packet packet = decodePacket(msg);
                    if (err.type.equals(packet.type) && err.data.equals(packet.data)) {
                        callback.call(err, 0, 1);
                        return;
                    }

                    boolean ret = callback.call(packet, i + n, l);
                    if (!ret) return;
                }

                i += n;
                length = new StringBuilder();
            }
        }

        if (length.length() > 0) {
            callback.call(err, 0, 1);
        }
    }

    public static interface DecodePayloadCallback {

        public boolean call(Packet packet, int index, int total);
    }
}
