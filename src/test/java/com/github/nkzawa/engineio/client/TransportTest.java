package com.github.nkzawa.engineio.client;

import com.github.nkzawa.engineio.client.transports.PollingXHR;
import com.github.nkzawa.engineio.client.transports.WebSocket;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.HashMap;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

@RunWith(JUnit4.class)
public class TransportTest {

    @Test
    public void uri() {
        Transport.Options opt = new Transport.Options();
        opt.path ="/engine.io";
        opt.hostname = "localhost";
        opt.secure = false;
        opt.query = new HashMap<String, String>() {{
            put("sid", "test");
        }};
        Polling polling = new Polling(opt);
        assertThat(polling.uri(), is("http://localhost/engine.io?sid=test"));
    }

    @Test
    public void uriWithDefaultPort() {
        Transport.Options opt = new Transport.Options();
        opt.path ="/engine.io";
        opt.hostname = "localhost";
        opt.secure = false;
        opt.query = new HashMap<String, String>() {{
            put("sid", "test");
        }};
        opt.port = 80;
        Polling polling = new Polling(opt);
        assertThat(polling.uri(), is("http://localhost/engine.io?sid=test"));
    }

    @Test
    public void uriWithPort() {
        Transport.Options opt = new Transport.Options();
        opt.path ="/engine.io";
        opt.hostname = "localhost";
        opt.secure = false;
        opt.query = new HashMap<String, String>() {{
            put("sid", "test");
        }};
        opt.port = 3000;
        Polling polling = new Polling(opt);
        assertThat(polling.uri(), is("http://localhost:3000/engine.io?sid=test"));
    }

    @Test
    public void httpsUriWithDefaultPort() {
        Transport.Options opt = new Transport.Options();
        opt.path ="/engine.io";
        opt.hostname = "localhost";
        opt.secure = true;
        opt.query = new HashMap<String, String>() {{
            put("sid", "test");
        }};
        opt.port = 443;
        Polling polling = new Polling(opt);
        assertThat(polling.uri(), is("https://localhost/engine.io?sid=test"));
    }

    @Test
    public void timestampedUri() {
        Transport.Options opt = new Transport.Options();
        opt.path ="/engine.io";
        opt.hostname = "localhost";
        opt.timestampParam = "t";
        opt.timestampRequests = true;
        Polling polling = new Polling(opt);
        assertThat(polling.uri().matches("http://localhost/engine.io\\?t=[0-9]+"), is(true));
    }

    @Test
    public void wsUri() {
        Transport.Options opt = new Transport.Options();
        opt.path ="/engine.io";
        opt.hostname = "test";
        opt.secure = false;
        opt.query = new HashMap<String, String>() {{
            put("transport", "websocket");
        }};
        WS ws = new WS(opt);
        assertThat(ws.uri(), is("ws://test/engine.io?transport=websocket"));
    }

    @Test
    public void wssUri() {
        Transport.Options opt = new Transport.Options();
        opt.path ="/engine.io";
        opt.hostname = "test";
        opt.secure = true;
        WS ws = new WS(opt);
        assertThat(ws.uri(), is("wss://test/engine.io"));
    }

    @Test
    public void wsTimestampedUri() {
        Transport.Options opt = new Transport.Options();
        opt.path ="/engine.io";
        opt.hostname = "localhost";
        opt.timestampParam = "woot";
        opt.timestampRequests = true;
        WS ws = new WS(opt);
        assertThat(ws.uri().matches("ws://localhost/engine.io\\?woot=[0-9]+"), is(true));
    }

    class Polling extends PollingXHR {

        public Polling(Options opts) {
            super(opts);
        }

        public String uri() {
            return super.uri();
        }
    }

    class WS extends WebSocket {

        public WS(Options opts) {
            super(opts);
        }

        public String uri() {
            return super.uri();
        }
    }
}
