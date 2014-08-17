package com.github.nkzawa.engineio.client;

import com.github.nkzawa.emitter.Emitter;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

@RunWith(JUnit4.class)
public class ConnectionTest extends Connection {

    private Socket socket;

    @Test(timeout = TIMEOUT)
    public void connectToLocalhost() throws InterruptedException {
        final BlockingQueue<Object> values = new LinkedBlockingQueue<Object>();

        socket = new Socket(createOptions());
        socket.on(Socket.EVENT_OPEN, new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                socket.on(Socket.EVENT_MESSAGE, new Emitter.Listener() {
                    @Override
                    public void call(Object... args) {
                        values.offer(args[0]);
                        socket.close();
                    }
                });
            }
        });
        socket.open();

        assertThat((String)values.take(), is("hi"));
    }

    @Test(timeout = TIMEOUT)
    public void receiveMultibyteUTF8StringsWithPolling() throws InterruptedException {
        final BlockingQueue<Object> values = new LinkedBlockingQueue<Object>();

        socket = new Socket(createOptions());
        socket.on(Socket.EVENT_OPEN, new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                socket.send("cash money €€€");
                socket.on(Socket.EVENT_MESSAGE, new Emitter.Listener() {
                    @Override
                    public void call(Object... args) {
                        if ("hi".equals(args[0])) return;
                        values.offer(args[0]);
                        socket.close();
                    }
                });
            }
        });
        socket.open();

        assertThat((String)values.take(), is("cash money €€€"));
    }

    @Test(timeout = TIMEOUT)
    public void receiveEmoji() throws InterruptedException {
        final BlockingQueue<Object> values = new LinkedBlockingQueue<Object>();

        socket = new Socket(createOptions());
        socket.on(Socket.EVENT_OPEN, new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                socket.send("\uD800-\uDB7F\uDB80-\uDBFF\uDC00-\uDFFF\uE000-\uF8FF");
                socket.on(Socket.EVENT_MESSAGE, new Emitter.Listener() {
                    @Override
                    public void call(Object... args) {
                        if ("hi".equals(args[0])) return;
                        values.offer(args[0]);
                        socket.close();
                    }
                });
            }
        });
        socket.open();

        assertThat((String)values.take(), is("\uD800-\uDB7F\uDB80-\uDBFF\uDC00-\uDFFF\uE000-\uF8FF"));
    }

    @Test(timeout = TIMEOUT)
    public void notSendPacketsIfSocketCloses() throws InterruptedException {
        final BlockingQueue<Object> values = new LinkedBlockingQueue<Object>();

        socket = new Socket(createOptions());
        socket.on(Socket.EVENT_OPEN, new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                final boolean[] noPacket = new boolean[] {true};
                socket.on(Socket.EVENT_PACKET_CREATE, new Emitter.Listener() {
                    @Override
                    public void call(Object... args) {
                        noPacket[0] = false;
                    }
                });
                socket.close();
                Timer timer = new Timer();
                timer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        values.offer(noPacket[0]);
                    }
                }, 1200);

            }
        });
        socket.open();
        assertThat((Boolean)values.take(), is(true));
    }
}
