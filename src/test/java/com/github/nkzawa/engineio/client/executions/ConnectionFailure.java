package com.github.nkzawa.engineio.client.executions;

import com.github.nkzawa.emitter.Emitter;
import com.github.nkzawa.engineio.client.Socket;

import java.net.URISyntaxException;
import java.util.Map;

public class ConnectionFailure {

    public static void main(String[] args) throws URISyntaxException {
        Map<String, String> env = System.getenv();
        int port = Integer.parseInt(env.get("PORT"));
        port++;
        final Socket socket = new Socket("http://localhost:" + port);
        socket.on(Socket.EVENT_CLOSE, new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                System.out.println("close");
            }
        }).on(Socket.EVENT_ERROR, new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                System.out.println("error");
            }
        });
        socket.open();
    }
}
