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
            try {
                str.append(URLEncoder.encode(entry.getKey(), "UTF-8")).append("=")
                        .append(URLEncoder.encode(entry.getValue(), "UTF-8"));
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException(e);
            }
        }
        return str.toString();
    }

    public static Map<String, String> qsParse(String qs) {
        Map<String, String> qry = new HashMap<String, String>();
        String[] pairs = qs.split("&");
        for (String _pair : pairs) {
            String[] pair = _pair.split("=");
            try {
                qry.put(URLDecoder.decode(pair[0], "UTF-8"),
                        pair.length > 0 ? URLDecoder.decode(pair[1], "UTF-8") : "");
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException(e);
            }

        }
        return qry;
    }
}
