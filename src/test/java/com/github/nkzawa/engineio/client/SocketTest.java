package com.github.nkzawa.engineio.client;

import com.github.nkzawa.emitter.Emitter;
import com.github.nkzawa.engineio.client.transports.Polling;
import com.github.nkzawa.engineio.client.transports.WebSocket;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

@RunWith(JUnit4.class)
public class SocketTest {

    @Test
    public void filterUpgrades() {
        Socket.Options opts = new Socket.Options();
        opts.transports = new String[] {Polling.NAME};
        Socket socket = new Socket(opts);
        List<String> upgrades = new ArrayList<String>() {{
            add(Polling.NAME);
            add(WebSocket.NAME);
        }};
        List<String> expected = new ArrayList<String>() {{add(Polling.NAME);}};
        assertThat(socket.filterUpgrades(upgrades), is(expected));
    }

    /**
     * should emit close on incorrect connection.
     *
     * @throws URISyntaxException
     * @throws InterruptedException
     */
    @Test
    public void socketClosing() throws URISyntaxException, InterruptedException {
        final BlockingQueue<Object> values = new LinkedBlockingQueue<Object>();

        Socket socket = new Socket("ws://0.0.0.0:8080");
        final boolean[] closed = {false};

        socket.once(Socket.EVENT_ERROR, new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                Timer timer = new Timer();
                timer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        values.offer(closed[0]);
                    }
                }, 20);
            }
        });

        socket.on(Socket.EVENT_CLOSE, new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                closed[0] = true;
            }
        });
        socket.open();

        assertThat((Boolean)values.take(), is(true));
    }

}
