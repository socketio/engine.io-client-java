package io.socket.engineio.parser;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

public class Parser {

    public static final int PROTOCOL = 4;

    private static final char SEPARATOR = '\u001e';

    private static final Map<String, Integer> packets = new HashMap<String, Integer>() {{
        put(Packet.OPEN, 0);
        put(Packet.CLOSE, 1);
        put(Packet.PING, 2);
        put(Packet.PONG, 3);
        put(Packet.MESSAGE, 4);
        put(Packet.UPGRADE, 5);
        put(Packet.NOOP, 6);
    }};

    private static final Map<Integer, String> packetslist = new HashMap<>();
    static {
        for (Map.Entry<String, Integer> entry : packets.entrySet()) {
            packetslist.put(entry.getValue(), entry.getKey());
        }
    }

    private static final Packet<String> err = new Packet<String>(Packet.ERROR, "parser error");

    private Parser() {}

    public static void encodePacket(Packet packet, EncodeCallback callback) {
        if (packet.data instanceof byte[]) {
            ((EncodeCallback<byte[]>) callback).call(((Packet<byte[]>) packet).data);
        } else {
            String type = String.valueOf(packets.get(packet.type));
            String content = packet.data != null ? String.valueOf(packet.data) : "";
            ((EncodeCallback<String>) callback).call(type + content);
        }
    }

    private static void encodePacketAsBase64(Packet packet, EncodeCallback<String> callback) {
        if (packet.data instanceof byte[]) {
            byte[] data = ((Packet<byte[]>) packet).data;
            String value = "b" + Base64.getEncoder().encodeToString(data);
            callback.call(value);
        } else {
            encodePacket(packet, callback);
        }
    }

    public static Packet<String> decodePacket(String data) {
        if (data == null) {
            return err;
        }

        int type;
        try {
            type = Character.getNumericValue(data.charAt(0));
        } catch (IndexOutOfBoundsException e) {
            type = -1;
        }

        if (type < 0 || type >= packetslist.size()) {
            return err;
        }

        if (data.length() > 1) {
            return new Packet<String>(packetslist.get(type), data.substring(1));
        } else {
            return new Packet<String>(packetslist.get(type));
        }
    }

    public static Packet decodeBase64Packet(String data) {
        if (data == null) {
            return err;
        }

        if (data.charAt(0) == 'b') {
            return new Packet(Packet.MESSAGE, Base64.getDecoder().decode(data.substring(1)));
        } else {
            return decodePacket(data);
        }
    }

    public static Packet<byte[]> decodePacket(byte[] data) {
        return new Packet<>(Packet.MESSAGE, data);
    }

    public static void encodePayload(Packet[] packets, EncodeCallback<String> callback) {
        if (packets.length == 0) {
            callback.call("0:");
            return;
        }

        final StringBuilder result = new StringBuilder();

        for (int i = 0, l = packets.length; i < l; i++) {
            final boolean isLast = i == l - 1;
            encodePacketAsBase64(packets[i], new EncodeCallback<String>() {
                @Override
                public void call(String message) {
                    result.append(message);
                    if (!isLast) {
                        result.append(SEPARATOR);
                    }
                }
            });
        }

        callback.call(result.toString());
    }

    public static void decodePayload(String data, DecodePayloadCallback<String> callback) {
        if (data == null || data.length() == 0) {
            callback.call(err, 0, 1);
            return;
        }

        String[] messages = data.split(String.valueOf(SEPARATOR));

        for (int i = 0, l = messages.length; i < l; i++) {
            Packet<String> packet = decodeBase64Packet(messages[i]);
            if (err.type.equals(packet.type) && err.data.equals(packet.data)) {
                callback.call(err, 0, 1);
                return;
            }

            boolean ret = callback.call(packet, i, l);
            if (!ret) {
                return;
            }
        }
    }

    public interface EncodeCallback<T> {

        void call(T data);
    }


    public interface DecodePayloadCallback<T> {

        boolean call(Packet<T> packet, int index, int total);
    }
}
