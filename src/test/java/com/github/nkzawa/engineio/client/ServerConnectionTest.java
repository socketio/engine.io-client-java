package com.github.nkzawa.engineio.client;

import com.github.nkzawa.emitter.Emitter;
import com.github.nkzawa.engineio.client.transports.Polling;
import com.github.nkzawa.engineio.client.transports.WebSocket;
import com.github.nkzawa.thread.EventThread;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.net.URISyntaxException;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.Assert.assertThat;

@RunWith(JUnit4.class)
public class ServerConnectionTest extends Connection {

    private Socket socket;

    @Test(timeout = TIMEOUT)
    public void openAndClose() throws URISyntaxException, InterruptedException {
        final BlockingQueue<String> events = new LinkedBlockingQueue<String>();

        socket = new Socket(createOptions());
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

        socket = new Socket(createOptions());
        socket.on(Socket.EVENT_OPEN, new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                socket.send("hello");
            }
        }).on(Socket.EVENT_MESSAGE, new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                events.offer((String)args[0]);
            }
        });
        socket.open();

        assertThat(events.take(), is("hi"));
        assertThat(events.take(), is("hello"));
        socket.close();
    }

    @Test(timeout = TIMEOUT)
    public void handshake() throws URISyntaxException, InterruptedException {
        final BlockingQueue<Object> values = new LinkedBlockingQueue<Object>();

        socket = new Socket(createOptions());
        socket.on(Socket.EVENT_HANDSHAKE, new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                values.offer(args);
            }
        });
        socket.open();

        Object[] args = (Object[])values.take();
        assertThat(args.length, is(1));
        HandshakeData data = (HandshakeData)args[0];
        assertThat(data.sid, is(notNullValue()));
        assertThat(data.upgrades, is(not(emptyArray())));
        assertThat(data.pingTimeout, is(greaterThan((long)0)));
        assertThat(data.pingInterval, is(greaterThan((long) 0)));
        socket.close();
    }

    @Test(timeout = TIMEOUT)
    public void upgrade() throws URISyntaxException, InterruptedException {
        final BlockingQueue<Object[]> events = new LinkedBlockingQueue<Object[]>();

        socket = new Socket(createOptions());
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

        Socket.Options opts = createOptions();
        opts.transports = new String[] {Polling.NAME};

        socket = new Socket(opts);
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

        Socket.Options opts = createOptions();
        opts.transports = new String[] {WebSocket.NAME};

        socket = new Socket(opts);
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
        final BlockingQueue<Object> values = new LinkedBlockingQueue<Object>();

        EventThread.exec(new Runnable() {
            @Override
            public void run() {
                final Socket socket = new Socket(createOptions());

                socket.on(Socket.EVENT_UPGRADE, new Emitter.Listener() {
                    @Override
                    public void call(Object... args) {
                        Transport transport = (Transport) args[0];
                        socket.close();
                        if (WebSocket.NAME.equals(transport.name)) {
                            Socket.Options opts = new Socket.Options();
                            opts.port = PORT;
                            opts.rememberUpgrade = true;

                            Socket socket2 = new Socket(opts);
                            socket2.open();
                            values.offer(socket2.transport.name);
                            socket2.close();
                        }
                    }
                });
                socket.open();
                values.offer(socket.transport.name);
            }
        });

        assertThat((String)values.take(), is(Polling.NAME));
        assertThat((String)values.take(), is(WebSocket.NAME));
    }

    @Test(timeout = TIMEOUT)
    public void notRememberWebsocket() throws InterruptedException {
        final BlockingQueue<Object> values = new LinkedBlockingQueue<Object>();

        EventThread.exec(new Runnable() {
            @Override
            public void run() {
                final Socket socket = new Socket(createOptions());

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
                            values.offer(socket2.transport.name);
                            socket2.close();
                        }
                    }
                });
                socket.open();
                values.offer(socket.transport.name);
            }
        });

        assertThat((String)values.take(), is(Polling.NAME));
        assertThat((String)values.take(), is(not(WebSocket.NAME)));
    }
}
