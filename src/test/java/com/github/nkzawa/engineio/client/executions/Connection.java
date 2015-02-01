package com.github.nkzawa.engineio.client.executions;

import com.github.nkzawa.emitter.Emitter;
import com.github.nkzawa.engineio.client.Socket;

import java.net.URISyntaxException;

public class Connection {

    public static void main(String[] args) throws URISyntaxException {
        final Socket socket = new Socket("http://localhost:" + System.getenv("PORT"));
        socket.on(Socket.EVENT_OPEN, new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                System.out.println("open");
                socket.close();
            }
        });
        socket.open();
    }
}
