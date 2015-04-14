package com.github.nkzawa.engineio.client;

import com.github.nkzawa.emitter.Emitter;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.internal.SslContextBuilder;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import javax.net.ssl.HostnameVerifier;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

@RunWith(JUnit4.class)
public class SSLConnectionTest extends Connection {

    static HostnameVerifier hostnameVerifier = new javax.net.ssl.HostnameVerifier(){
        public boolean verify(String hostname, javax.net.ssl.SSLSession sslSession) {
            return true;
        }
    };


    private Socket socket;

    @After
    public void tearDown() {
        Socket.setDefaultSSLContext(null);
    }

    @Override
    Socket.Options createOptions() {
        Socket.Options opts = super.createOptions();
        opts.secure = true;
        return opts;
    }

    @Override
    String[] createEnv() {
        return new String[] {"DEBUG=engine*", "PORT=" + PORT, "SSL=1"};
    }

    @Test(timeout = TIMEOUT)
    public void connect() throws Exception {
        final BlockingQueue<Object> values = new LinkedBlockingQueue<Object>();

        Socket.Options opts = createOptions();
        opts.sslContext = SslContextBuilder.localhost();
        opts.okHttpClient = new OkHttpClient().setHostnameVerifier(hostnameVerifier);
        socket = new Socket(opts);
        socket.on(Socket.EVENT_OPEN, new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                socket.on(Socket.EVENT_MESSAGE, new Emitter.Listener() {
                    @Override
                    public void call(Object... args) {
                        values.offer(args[0]);
                    }
                });
            }
        });
        socket.open();

        assertThat((String)values.take(), is("hi"));
        socket.close();
    }

    @Test(timeout = TIMEOUT)
    public void upgrade() throws Exception {
        final BlockingQueue<Object> values = new LinkedBlockingQueue<Object>();

        Socket.Options opts = createOptions();
        opts.sslContext = SslContextBuilder.localhost();
        opts.okHttpClient = new OkHttpClient().setHostnameVerifier(hostnameVerifier);
        socket = new Socket(opts);
        socket.on(Socket.EVENT_OPEN, new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                socket.on(Socket.EVENT_UPGRADE, new Emitter.Listener() {
                    @Override
                    public void call(Object... args) {
                        socket.send("hi");
                        socket.on(Socket.EVENT_MESSAGE, new Emitter.Listener() {
                            @Override
                            public void call(Object... args) {
                                values.offer(args[0]);
                            }
                        });
                    }
                });
            }
        });
        socket.open();

        assertThat((String)values.take(), is("hi"));
        socket.close();
    }

    @Test(timeout = TIMEOUT)
    public void defaultSSLContext() throws Exception {
        final BlockingQueue<Object> values = new LinkedBlockingQueue<Object>();

        Socket.Options opts = createOptions();
        Socket.setDefaultSSLContext(SslContextBuilder.localhost());
        opts.okHttpClient = new OkHttpClient().setHostnameVerifier(hostnameVerifier);
        socket = new Socket(opts);
        socket.on(Socket.EVENT_OPEN, new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                socket.on(Socket.EVENT_MESSAGE, new Emitter.Listener() {
                    @Override
                    public void call(Object... args) {
                        values.offer(args[0]);
                    }
                });
            }
        });
        socket.open();

        assertThat((String)values.take(), is("hi"));
        socket.close();
    }
}
