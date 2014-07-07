package com.github.nkzawa.engineio.client;

import com.github.nkzawa.emitter.Emitter;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Semaphore;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

@RunWith(JUnit4.class)
public class ConnectionTest extends Connection {

    private Socket socket;

    @Test(timeout = TIMEOUT)
    public void connectToLocalhost() throws InterruptedException {
        final Semaphore semaphore = new Semaphore(0);

        socket = new Socket(createOptions());
        socket.on(Socket.EVENT_OPEN, new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                socket.on(Socket.EVENT_MESSAGE, new Emitter.Listener() {
                    @Override
                    public void call(Object... args) {
                        assertThat((String)args[0], is("hi"));
                        socket.close();
                        semaphore.release();
                    }
                });
            }
        });
        socket.open();
        semaphore.acquire();
    }

    @Test(timeout = TIMEOUT)
    public void receiveMultibyteUTF8StringsWithPolling() throws InterruptedException {
        final Semaphore semaphore = new Semaphore(0);

        socket = new Socket(createOptions());
        socket.on(Socket.EVENT_OPEN, new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                socket.send("cash money €€€");
                socket.on(Socket.EVENT_MESSAGE, new Emitter.Listener() {
                    @Override
                    public void call(Object... args) {
                        if ("hi".equals(args[0])) return;
                        assertThat((String)args[0], is("cash money €€€"));
                        socket.close();
                        semaphore.release();
                    }
                });
            }
        });
        socket.open();
        semaphore.acquire();
    }

    @Test(timeout = TIMEOUT)
    public void receiveEmoji() throws InterruptedException {
        final Semaphore semaphore = new Semaphore(0);

        socket = new Socket(createOptions());
        socket.on(Socket.EVENT_OPEN, new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                socket.send("\uD800-\uDB7F\uDB80-\uDBFF\uDC00-\uDFFF\uE000-\uF8FF");
                socket.on(Socket.EVENT_MESSAGE, new Emitter.Listener() {
                    @Override
                    public void call(Object... args) {
                        if ("hi".equals(args[0])) return;
                        assertThat((String)args[0], is("\uD800-\uDB7F\uDB80-\uDBFF\uDC00-\uDFFF\uE000-\uF8FF"));
                        socket.close();
                        semaphore.release();
                    }
                });
            }
        });
        socket.open();
        semaphore.acquire();
    }

    @Test(timeout = TIMEOUT)
    public void notSendPacketsIfSocketCloses() throws InterruptedException {
        final Semaphore semaphore = new Semaphore(0);

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
                        assertThat(noPacket[0], is(true));
                        semaphore.release();
                    }
                }, 1200);

            }
        });
        socket.open();
        semaphore.acquire();
    }
}
