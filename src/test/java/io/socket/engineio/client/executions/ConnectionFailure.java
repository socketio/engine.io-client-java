package io.socket.engineio.client.executions;

import io.socket.emitter.Emitter;
import io.socket.engineio.client.Socket;

import java.net.URISyntaxException;

public class ConnectionFailure {

    public static void main(String[] args) throws URISyntaxException {
        int port = Integer.parseInt(System.getenv("PORT"));
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
