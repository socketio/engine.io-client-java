package io.socket.yeast;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * A Java implementation of yeast. https://github.com/unshiftio/yeast
 */
public final class Yeast {
    private static char[] alphabet = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz-_".toCharArray();

    private static int length = alphabet.length;

    private static Map<Character, Integer> map = new HashMap<Character, Integer>(length);
    static {
        for (int i = 0; i < length; i++) {
            map.put(alphabet[i], i);
        }
    }

    private Yeast () {}

    private static int seed = 0;

    private static String prev;

    public static String encode(long num) {
        final StringBuilder encoded = new StringBuilder();

        do {
            encoded.insert(0, alphabet[(int)(num % length)]);
            num = (long)Math.floor(num / length);
        } while (num > 0);

        return encoded.toString();
    }

    public static long decode(String str) {
        long decoded = 0;

        for (char c : str.toCharArray()) {
            decoded = decoded * length + map.get(c);
        }

        return decoded;
    }

    public static String yeast() {
        String now = encode(new Date().getTime());

        if (!now.equals(prev)) {
            seed = 0;
            prev = now;
            return now;
        }

        return now + "." + encode(seed++);
    }
}
