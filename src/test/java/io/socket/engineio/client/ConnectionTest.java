package io.socket.engineio.client;

import io.socket.emitter.Emitter;
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
                socket.send("\uD800\uDC00-\uDB7F\uDFFF\uDB80\uDC00-\uDBFF\uDFFF\uE000-\uF8FF");
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

        assertThat((String)values.take(), is("\uD800\uDC00-\uDB7F\uDFFF\uDB80\uDC00-\uDBFF\uDFFF\uE000-\uF8FF"));
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
                socket.send("hi");
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

    @Test(timeout = TIMEOUT)
    public void deferCloseWhenUpgrading() throws InterruptedException {
        final BlockingQueue<Object> values = new LinkedBlockingQueue<Object>();

        socket = new Socket(createOptions());
        socket.on(Socket.EVENT_OPEN, new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                final boolean[] upgraded = new boolean[] {false};
                socket.on(Socket.EVENT_UPGRADE, new Emitter.Listener() {
                    @Override
                    public void call(Object... args) {
                        upgraded[0] = true;
                    }
                }).on(Socket.EVENT_UPGRADING, new Emitter.Listener() {
                    @Override
                    public void call(Object... args) {
                        socket.on(Socket.EVENT_CLOSE, new Emitter.Listener() {
                            @Override
                            public void call(Object... args) {
                                values.offer(upgraded[0]);
                            }
                        });
                        socket.close();
                    }
                });
            }
        });
        socket.open();
        assertThat((Boolean)values.take(), is(true));
    }

    @Test(timeout = TIMEOUT)
    public void closeOnUpgradeErrorIfClosingIsDeferred() throws InterruptedException {
        final BlockingQueue<Object> values = new LinkedBlockingQueue<Object>();

        socket = new Socket(createOptions());
        socket.on(Socket.EVENT_OPEN, new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                final boolean[] upgradError = new boolean[] {false};
                socket.on(Socket.EVENT_UPGRADE_ERROR, new Emitter.Listener() {
                    @Override
                    public void call(Object... args) {
                        upgradError[0] = true;
                    }
                }).on(Socket.EVENT_UPGRADING, new Emitter.Listener() {
                    @Override
                    public void call(Object... args) {
                        socket.on(Socket.EVENT_CLOSE, new Emitter.Listener() {
                            @Override
                            public void call(Object... args) {
                                values.offer(upgradError[0]);
                            }
                        });
                        socket.close();
                        socket.transport.onError("upgrade error", new Exception());
                    }
                });
            }
        });
        socket.open();
        assertThat((Boolean) values.take(), is(true));
    }

    public void notSendPacketsIfClosingIsDeferred() throws InterruptedException {
        final BlockingQueue<Object> values = new LinkedBlockingQueue<Object>();

        socket = new Socket(createOptions());
        socket.on(Socket.EVENT_OPEN, new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                final boolean[] noPacket = new boolean[] {true};
                socket.on(Socket.EVENT_UPGRADING, new Emitter.Listener() {
                    @Override
                    public void call(Object... args) {
                        socket.on(Socket.EVENT_PACKET_CREATE, new Emitter.Listener() {
                            @Override
                            public void call(Object... args) {
                                noPacket[0] = false;
                            }
                        });
                        socket.close();
                        socket.send("hi");
                    }
                });
                new Timer().schedule(new TimerTask() {
                    @Override
                    public void run() {
                        values.offer(noPacket[0]);
                    }
                }, 1200);
            }
        });
        socket.open();
        assertThat((Boolean) values.take(), is(true));
    }

    @Test(timeout = TIMEOUT)
    public void sendAllBufferedPacketsIfClosingIsDeferred() throws InterruptedException {
        final BlockingQueue<Object> values = new LinkedBlockingQueue<Object>();

        socket = new Socket(createOptions());
        socket.on(Socket.EVENT_OPEN, new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                socket.on(Socket.EVENT_UPGRADING, new Emitter.Listener() {
                    @Override
                    public void call(Object... args) {
                        socket.send("hi");
                        socket.close();
                    }
                }).on(Socket.EVENT_CLOSE, new Emitter.Listener() {
                    @Override
                    public void call(Object... args) {
                        values.offer(socket.writeBuffer.size());
                    }
                });
            }
        });
        socket.open();
        assertThat((Integer) values.take(), is(0));
    }
}
