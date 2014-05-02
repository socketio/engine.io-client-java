package com.github.nkzawa.engineio.client;

import com.github.nkzawa.emitter.Emitter;
import com.github.nkzawa.engineio.client.transports.Polling;
import com.github.nkzawa.engineio.client.transports.WebSocket;
import com.github.nkzawa.thread.EventThread;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.concurrent.*;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.Assert.assertThat;

@RunWith(JUnit4.class)
public class ServerConnectionTest {

    final static int TIMEOUT = 3000;
    final static int PORT = 3000;

    private Process serverProcess;
    private ExecutorService serverService;
    private Future serverOutout;
    private Future serverError;
    private Socket socket;

    @Before
    public void startServer() throws IOException, InterruptedException {
        System.out.println("Starting server ...");

        final CountDownLatch latch = new CountDownLatch(1);
        serverProcess = Runtime.getRuntime().exec(
                "node src/test/resources/index.js " + PORT, new String[] {"DEBUG=engine*"});
        serverService = Executors.newCachedThreadPool();
        serverOutout = serverService.submit(new Runnable() {
            @Override
            public void run() {
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(serverProcess.getInputStream()));
                String line;
                try {
                    line = reader.readLine();
                    latch.countDown();
                    do {
                        System.out.println("SERVER OUT: " + line);
                    } while ((line = reader.readLine()) != null);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
        serverError = serverService.submit(new Runnable() {
            @Override
            public void run() {
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(serverProcess.getErrorStream()));
                String line;
                try {
                    while ((line = reader.readLine()) != null) {
                        System.err.println("SERVER ERR: " + line);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
        latch.await(3000, TimeUnit.MILLISECONDS);
    }

    @After
    public void stopServer() throws InterruptedException {
        System.out.println("Stopping server ...");
        serverProcess.destroy();
        serverOutout.cancel(true);
        serverError.cancel(true);
        serverService.shutdown();
        serverService.awaitTermination(3000, TimeUnit.MILLISECONDS);
    }

    @Test(timeout = TIMEOUT)
    public void openAndClose() throws URISyntaxException, InterruptedException {
        final BlockingQueue<String> events = new LinkedBlockingQueue<String>();

        socket = new Socket("ws://localhost:" + PORT);
        socket.on(Socket.EVENT_OPEN, new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                events.offer("onopen");
            }
        }).on(Socket.EVENT_CLOSE, new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                events.offer("onclose");
            }
        });
        socket.open();

        assertThat(events.take(), is("onopen"));
        socket.close();
        assertThat(events.take(), is("onclose"));
    }

    @Test(timeout = TIMEOUT)
    public void messages() throws URISyntaxException, InterruptedException {
        final BlockingQueue<String> events = new LinkedBlockingQueue<String>();

        socket = new Socket("ws://localhost:" + PORT);
        socket.on(Socket.EVENT_OPEN, new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                socket.send("hi");
            }
        }).on(Socket.EVENT_MESSAGE, new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                events.offer((String)args[0]);
            }
        });
        socket.open();

        assertThat(events.take(), is("hello client"));
        assertThat(events.take(), is("hi"));
        socket.close();
    }

    @Test(timeout = TIMEOUT)
    public void handshake() throws URISyntaxException, InterruptedException {
        final Semaphore semaphore = new Semaphore(0);

        socket = new Socket("ws://localhost:" + PORT);
        socket.on(Socket.EVENT_HANDSHAKE, new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                assertThat(args.length, is(1));
                assertThat(args[0], is(instanceOf(HandshakeData.class)));

                HandshakeData data = (HandshakeData)args[0];
                assertThat(data.sid, is(notNullValue()));
                assertThat(data.upgrades, is(notNullValue()));
                assertThat(data.upgrades, is(not(emptyArray())));
                assertThat(data.pingTimeout, is(greaterThan((long)0)));
                assertThat(data.pingInterval, is(greaterThan((long) 0)));

                socket.close();
                semaphore.release();
            }
        });
        socket.open();
        semaphore.acquire();
    }

    @Test(timeout = TIMEOUT)
    public void upgrade() throws URISyntaxException, InterruptedException {
        final BlockingQueue<Object[]> events = new LinkedBlockingQueue<Object[]>();

        socket = new Socket("ws://localhost:" + PORT);
        socket.on(Socket.EVENT_UPGRADING, new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                events.offer(args);
            }
        });
        socket.on(Socket.EVENT_UPGRADE, new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                events.offer(args);
            }
        });
        socket.open();

        Object[] args1 = events.take();
        assertThat(args1.length, is(1));
        assertThat(args1[0], is(instanceOf(Transport.class)));
        Transport transport1 = (Transport)args1[0];
        assertThat(transport1, is(notNullValue()));

        Object[] args2 = events.take();
        assertThat(args2.length, is(1));
        assertThat(args2[0], is(instanceOf(Transport.class)));
        Transport transport2 = (Transport)args2[0];
        assertThat(transport2, is(notNullValue()));

        socket.close();
    }

    @Test(timeout = TIMEOUT)
    public void pollingHeaders() throws URISyntaxException, InterruptedException {
        final BlockingQueue<String> messages = new LinkedBlockingQueue<String>();

        Socket.Options opts = new Socket.Options();
        opts.transports = new String[] {Polling.NAME};

        socket = new Socket("ws://localhost:" + PORT, opts);
        socket.on(Socket.EVENT_TRANSPORT, new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                Transport transport = (Transport)args[0];
                transport.on(Transport.EVENT_REQUEST_HEADERS, new Emitter.Listener() {
                    @Override
                    public void call(Object... args) {
                        @SuppressWarnings("unchecked")
                        Map<String, String> headers = (Map<String, String>)args[0];
                        headers.put("X-EngineIO", "foo");
                    }
                }).on(Transport.EVENT_RESPONSE_HEADERS, new Emitter.Listener() {
                    @Override
                    public void call(Object... args) {
                        @SuppressWarnings("unchecked")
                        Map<String, String> headers = (Map<String, String>)args[0];
                        messages.offer(headers.get("X-EngineIO"));
                    }
                });
            }
        });
        socket.open();

        assertThat(messages.take(), is("foo"));
        assertThat(messages.take(), is("foo"));
        socket.close();
    }

    @Test(timeout = TIMEOUT)
    public void websocketHandshakeHeaders() throws URISyntaxException, InterruptedException {
        final BlockingQueue<String> messages = new LinkedBlockingQueue<String>();

        Socket.Options opts = new Socket.Options();
        opts.transports = new String[] {WebSocket.NAME};

        socket = new Socket("ws://localhost:" + PORT, opts);
        socket.on(Socket.EVENT_TRANSPORT, new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                Transport transport = (Transport)args[0];
                transport.on(Transport.EVENT_REQUEST_HEADERS, new Emitter.Listener() {
                    @Override
                    public void call(Object... args) {
                        @SuppressWarnings("unchecked")
                        Map<String, String> headers = (Map<String, String>)args[0];
                        headers.put("X-EngineIO", "foo");
                    }
                }).on(Transport.EVENT_RESPONSE_HEADERS, new Emitter.Listener() {
                    @Override
                    public void call(Object... args) {
                        @SuppressWarnings("unchecked")
                        Map<String, String> headers = (Map<String, String>)args[0];
                        messages.offer(headers.get("X-EngineIO"));
                    }
                });
            }
        });
        socket.open();

        assertThat(messages.take(), is("foo"));
        socket.close();
    }

    @Test(timeout = TIMEOUT)
    public void rememberWebsocket() throws InterruptedException {
        final Semaphore semaphore = new Semaphore(0);

        EventThread.exec(new Runnable() {
            @Override
            public void run() {
                Socket.Options opts = new Socket.Options();
                opts.port = PORT;

                final Socket socket = new Socket(opts);

                socket.on(Socket.EVENT_UPGRADE, new Emitter.Listener() {
                    @Override
                    public void call(Object... args) {
                        Transport transport = (Transport) args[0];
                        socket.close();
                        if (WebSocket.NAME.equals(transport.name)) {
                            Socket.Options opts = new Socket.Options();
                            opts.port = PORT;
                            opts.rememberUpgrade = true;

                            final Socket socket2 = new Socket(opts);
                            socket2.open();
                            assertThat(socket2.transport.name, is(WebSocket.NAME));
                        }
                        semaphore.release();
                    }
                });
                socket.open();
                assertThat(socket.transport.name, is(Polling.NAME));
            }
        });
        semaphore.acquire();
    }

    @Test(timeout = TIMEOUT)
    public void notRememberWebsocket() throws InterruptedException {
        final Semaphore semaphore = new Semaphore(0);

        EventThread.exec(new Runnable() {
            @Override
            public void run() {
                Socket.Options opts = new Socket.Options();
                opts.port = PORT;

                final Socket socket = new Socket(opts);

                socket.on(Socket.EVENT_UPGRADE, new Emitter.Listener() {
                    @Override
                    public void call(Object... args) {
                        Transport transport = (Transport)args[0];
                        socket.close();
                        if (WebSocket.NAME.equals(transport.name)) {
                            Socket.Options opts = new Socket.Options();
                            opts.port = PORT;
                            opts.rememberUpgrade = false;

                            final Socket socket2 = new Socket(opts);
                            socket2.open();
                            assertThat(socket2.transport.name, is(not(WebSocket.NAME)));
                        }
                        semaphore.release();
                    }
                });
                socket.open();
                assertThat(socket.transport.name, is(Polling.NAME));
            }
        });
        semaphore.acquire();
    }

    @Test(timeout = TIMEOUT)
    public void sendAndReceiveBinaryDataWhenPolling() throws InterruptedException {
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
            }
        }).on(Socket.EVENT_MESSAGE, new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                if (args[0] instanceof byte[]) {
                    assertThat((byte[])args[0], is(binaryData));
                    socket.close();
                    semaphore.release();
                }
            }
        });
        socket.open();
        semaphore.acquire();
    }

    @Test(timeout = TIMEOUT)
    public void sendAndReceiveBinaryDataWhenWS() throws InterruptedException {
        final Semaphore semaphore = new Semaphore(0);
        final byte[] binaryData = new byte[5];
        for (int i = 0; i < binaryData.length; i++) {
            binaryData[i] = (byte)i;
        }

        Socket.Options opts = new Socket.Options();
        opts.port = PORT;

        socket = new Socket(opts);
        socket.on(Socket.EVENT_OPEN, new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                socket.on(Socket.EVENT_UPGRADE, new Emitter.Listener() {
                    @Override
                    public void call(Object... args) {
                        socket.send(binaryData);
                    }
                });
            }
        }).on(Socket.EVENT_MESSAGE, new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                if (args[0] instanceof byte[]) {
                    assertThat((byte[])args[0], is(binaryData));
                    socket.close();
                    semaphore.release();
                }
            }
        });
        socket.open();
        semaphore.acquire();
    }
}
