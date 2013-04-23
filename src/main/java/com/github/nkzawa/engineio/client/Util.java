package com.github.nkzawa.engineio.client;


import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;

public class Util {

    private Util() {}

    public static String qs(Map<String, String> obj) {
        StringBuilder str = new StringBuilder();
        for (Map.Entry<String, String> entry : obj.entrySet()) {
            if (str.length() > 0) str.append("&");
            str.append(encodeURIComponent(entry.getKey())).append("=")
                    .append(encodeURIComponent(entry.getValue()));
        }
        return str.toString();
    }

    public static Map<String, String> qsParse(String qs) {
        Map<String, String> qry = new HashMap<String, String>();
        String[] pairs = qs.split("&");
        for (String _pair : pairs) {
            String[] pair = _pair.split("=");
            qry.put(decodeURIComponent(pair[0]),
                    pair.length > 0 ? decodeURIComponent(pair[1]) : "");
        }
        return qry;
    }

    public static String encodeURIComponent(String str) {
        try {
            return URLEncoder.encode(str, "UTF-8")
                    .replace("+", "%20")
                    .replace("%21", "!")
                    .replace("%27", "'")
                    .replace("%28", "(")
                    .replace("%29", ")")
                    .replace("%7E", "~");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    public static String decodeURIComponent(String str) {
        try {
            return URLDecoder.decode(str, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }
}
