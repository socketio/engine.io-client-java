package com.github.nkzawa.engineio.client.executions;

import com.github.nkzawa.emitter.Emitter;
import com.github.nkzawa.engineio.client.Socket;

import java.net.URISyntaxException;
import java.util.Map;

public class ImmediateClose {

    public static void main(String[] args) throws URISyntaxException {
        Map<String, String> env = System.getenv();
        final Socket socket = new Socket("http://localhost:" + env.get("PORT"));
        socket.on(Socket.EVENT_OPEN, new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                System.out.println("open");
            }
        }).on(Socket.EVENT_CLOSE, new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                System.out.println("close");
            }
        });
        socket.open();
        socket.close();
    }
}
