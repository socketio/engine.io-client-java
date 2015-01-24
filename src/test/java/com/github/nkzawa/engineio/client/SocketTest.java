package com.github.nkzawa.engineio.client;

import com.github.nkzawa.engineio.client.transports.Polling;
import com.github.nkzawa.engineio.client.transports.WebSocket;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

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

    public void properlyParseHttpUriWithoutPort() throws URISyntaxException {
        Socket client = new Socket("http://localhost");
        assertThat(client.port, is(80));
    }

    public void properlyParseHttpsUriWithoutPort() throws URISyntaxException {
        Socket client = new Socket("http://localhost");
        assertThat(client.port, is(443));
    }

    public void properlyParseWssUriWithoutPort() throws URISyntaxException {
        Socket client = new Socket("http://localhost");
        assertThat(client.port, is(443));
    }

    public void properlyParseWssUriWithPort() throws URISyntaxException {
        Socket client = new Socket("http://localhost:2020");
        assertThat(client.port, is(2020));
    }
}
