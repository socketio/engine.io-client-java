package io.socket.engineio.client;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * @author Eugene.Kudelevsky
 */
public interface HttpConnectionProvider {
    HttpURLConnection openConnection(URL url) throws IOException;
}
