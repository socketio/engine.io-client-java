package io.socket.engineio.client;

import io.socket.engineio.client.transports.Polling;
import io.socket.engineio.client.transports.WebSocket;
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

    @Test
    public void properlyParseHttpUriWithoutPort() throws URISyntaxException {
        Socket client = new Socket("http://localhost");
        assertThat(client.hostname, is("localhost"));
        assertThat(client.port, is(80));
    }

    @Test
    public void properlyParseHttpsUriWithoutPort() throws URISyntaxException {
        Socket client = new Socket("https://localhost");
        assertThat(client.hostname, is("localhost"));
        assertThat(client.port, is(443));
    }

    @Test
    public void properlyParseWssUriWithoutPort() throws URISyntaxException {
        Socket client = new Socket("wss://localhost");
        assertThat(client.hostname, is("localhost"));
        assertThat(client.port, is(443));
    }

    @Test
    public void properlyParseWssUriWithPort() throws URISyntaxException {
        Socket client = new Socket("wss://localhost:2020");
        assertThat(client.hostname, is("localhost"));
        assertThat(client.port, is(2020));
    }

    @Test
    public void properlyParseHostWithPort() {
        Socket.Options opts = new Socket.Options();
        opts.host = "localhost";
        opts.port = 8080;
        Socket client = new Socket(opts);
        assertThat(client.hostname, is("localhost"));
        assertThat(client.port, is(8080));
    }

    @Test
    public void properlyParseIPv6UriWithoutPort() throws URISyntaxException {
        Socket client = new Socket("http://[::1]");
        assertThat(client.hostname, is("::1"));
        assertThat(client.port, is(80));
    }

    @Test
    public void properlyParseIPv6UriWithPort() throws URISyntaxException {
        Socket client = new Socket("http://[::1]:8080");
        assertThat(client.hostname, is("::1"));
        assertThat(client.port, is(8080));
    }

    @Test
    public void properlyParseIPv6HostWithoutPort1() {
        Socket.Options opts = new Socket.Options();
        opts.host = "[::1]";
        Socket client = new Socket(opts);
        assertThat(client.hostname, is("::1"));
        assertThat(client.port, is(80));
    }

    @Test
    public void properlyParseIPv6HostWithoutPort2() {
        Socket.Options opts = new Socket.Options();
        opts.secure = true;
        opts.host = "[::1]";
        Socket client = new Socket(opts);
        assertThat(client.hostname, is("::1"));
        assertThat(client.port, is(443));
    }

    @Test
    public void properlyParseIPv6HostWithPort() {
        Socket.Options opts = new Socket.Options();
        opts.host = "[::1]";
        opts.port = 8080;
        Socket client = new Socket(opts);
        assertThat(client.hostname, is("::1"));
        assertThat(client.port, is(8080));
    }

    @Test
    public void properlyParseIPv6HostWithoutBrace() {
        Socket.Options opts = new Socket.Options();
        opts.host = "::1";
        Socket client = new Socket(opts);
        assertThat(client.hostname, is("::1"));
        assertThat(client.port, is(80));
    }
}
