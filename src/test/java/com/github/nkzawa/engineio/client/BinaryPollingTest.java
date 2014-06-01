package com.github.nkzawa.engineio.client;

import com.github.nkzawa.emitter.Emitter;
import com.github.nkzawa.engineio.client.transports.Polling;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.concurrent.Semaphore;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

@RunWith(JUnit4.class)
public class BinaryPollingTest extends Connection {

    private Socket socket;

    @Test(timeout = TIMEOUT)
    public void receiveBinaryData() throws InterruptedException {
        final Semaphore semaphore = new Semaphore(0);
        final byte[] binaryData = new byte[5];
        for (int i = 0; i < binaryData.length; i++) {
            binaryData[i] = (byte)i;
        }
        Socket.Options opts = new Socket.Options();
        opts.port = PORT;
        opts.transports = new String[] {Polling.NAME};

        socket = new Socket(opts);
        socket.on(Socket.EVENT_OPEN, new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                socket.send(binaryData);
                socket.on(Socket.EVENT_MESSAGE, new Emitter.Listener() {
                    @Override
                    public void call(Object... args) {
                        if ("hi".equals(args[0])) return;

                        assertThat(args[0], instanceOf(byte[].class));
                        assertThat((byte[])args[0], is(binaryData));
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
    public void receiveBinaryDataAndMultibyteUTF8String() throws InterruptedException {
        final Semaphore semaphore = new Semaphore(0);
        final byte[] binaryData = new byte[5];
        for (int i = 0; i < binaryData.length; i++) {
            binaryData[i] = (byte)i;
        }

        final int[] msg = new int[] {0};
        Socket.Options opts = new Socket.Options();
        opts.port = PORT;
        opts.transports = new String[] {Polling.NAME};
        socket = new Socket(opts);
        socket.on(Socket.EVENT_OPEN, new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                socket.send(binaryData);
                socket.send("cash money €€€");
                socket.on(Socket.EVENT_MESSAGE, new Emitter.Listener() {
                    @Override
                    public void call(Object... args) {
                        if ("hi".equals(args[0])) return;

                        if (msg[0] == 0) {
                            assertThat(args[0], instanceOf(byte[].class));
                            assertThat((byte[])args[0], is(binaryData));
                            msg[0]++;
                        } else {
                            assertThat(args[0], instanceOf(String.class));
                            assertThat((String)args[0], is("cash money €€€"));
                            socket.close();
                            semaphore.release();
                        }
                    }
                });
            }
        });
        socket.open();
        semaphore.acquire();
    }
}
