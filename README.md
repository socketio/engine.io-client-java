# Engine.IO-client Java
[![Build Status](https://travis-ci.org/socketio/engine.io-client-java.png?branch=master)](https://travis-ci.org/socketio/engine.io-client-java)

This is the Engine.IO Client Library for Java, which is simply ported from the [JavaScript client](https://github.com/socketio/engine.io-client).

See also: [Socket.IO-client Java](https://github.com/socketio/socket.io-client-java)

## Installation
The latest artifact is available on Maven Central. To install manually, please refer [dependencies](https://socketio.github.io/engine.io-client-java/dependencies.html).

### Maven
Add the following dependency to your `pom.xml`.

```xml
<dependencies>
  <dependency>
    <groupId>io.socket</groupId>
    <artifactId>engine.io-client</artifactId>
    <version>1.0.0</version>
  </dependency>
</dependencies>
```

### Gradle
Add it as a gradle dependency for Android Studio, in `build.gradle`:

```groovy
compile ('io.socket:engine.io-client:1.0.0') {
  // excluding org.json which is provided by Android
  exclude group: 'org.json', module: 'json'
}
```

#### Engine.IO Server 1.x suppport

The current version of engine.io-client-java doesn't support engine.io server 1.x.
Please use engine.io-client-java 0.9.x for that instead.

## Usage
Engine.IO-client Java has the similar api with the JS client. You can use `Socket` to connect:

```java
socket = new Socket("ws://localhost");
socket.on(Socket.EVENT_OPEN, new Emitter.Listener() {
  @Override
  public void call(Object... args) {
    socket.send("hi");
    socket.close();
  }
});
socket.open();
```

You can listen events as follows:

```java
socket.on(Socket.EVENT_MESSAGE, new Emitter.Listener() {
  @Override
  public void call(Object... args) {
    String data = (String)args[0];
  }
}).on(Socket.EVENT_ERROR, new Emitter.Listener() {
  @Override
  public void call(Object... args) {
    Exception err = (Exception)args[0];
  }
});
```

How to set options:

```java
opts = new Socket.Options();
opts.transports = new String[] {WebSocket.NAME};

socket = new Socket(opts);
```

Sending and receiving binary data:

```java
socket = new Socket();
socket.on(Socket.EVENT_OPEN, new Emitter.Listener() {
  @Override
  public void call(Object... args) {
    // send binary data
    byte[] data = new byte[42];
    socket.send(data);
  }
}).on(Socket.EVENT_MESSAGE, new Emitter.Listener() {
  @Override
  public void call(Object... args) {
    // receive binary data
    byte[] data = (byte[])args[0];
  }
});
```

Use custom SSL settings:

```java
OkHttpClient okHttpClient = new OkHttpClient.Builder()
    .hostnameVerifier(myHostnameVerifier)
    .sslSocketFactory(mySSLContext.getSocketFactory(), myX509TrustManager)
    .build();

// default SSLContext for all sockets
Socket.setDefaultOkHttpWebSocketFactory(okHttpClient);
Socket.setDefaultOkHttpCallFactory(okHttpClient);

// set as an option
opts = new Socket.Options();
opts.callFactory = okHttpClient;
opts.webSocketFactory = okHttpClient;
socket = new Socket(opts);
```

## Features
This library supports all of the features the JS client does, including events, options and upgrading transport. Android is fully supported.

### Extra features only for Java client

#### Accessing HTTP Headers
You can access HTTP headers like the following.

```java
socket.on(Socket.EVENT_TRANSPORT, new Emitter.listener() {
  @Override
  public void call(Object... args) {
    // Called on a new transport created.
    Transport transport = (Transport)args[0];

    transport.on(Transport.EVENT_REQUEST_HEADERS, new Emitter.Listener() {
      @Override
      public void call(Object... args) {
        @SuppressWarnings("unchecked")
        Map<String, List<String>> headers = (Map<String, List<String>>)args[0];
        // send cookie value to server.
        headers.put("Cookie", Arrays.asList("foo=1;"));
      }
    }).on(Transport.EVENT_RESPONSE_HEADERS, new Emitter.Listener() {
      @Override
      public void call(Object... args) {
        @SuppressWarnings("unchecked")
        Map<String, List<String>> headers = (Map<String, List<String>>)args[0];
        // receive cookie value from server.
        String cookie = headers.get("Set-Cookie").get(0);
      }
    });
  }
});
```

See the Javadoc for more details.

http://socketio.github.io/engine.io-client-java/apidocs/

## License

MIT

