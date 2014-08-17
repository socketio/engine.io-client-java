# Engine.IO-client.java
[![Build Status](https://travis-ci.org/nkzawa/engine.io-client.java.png?branch=master)](https://travis-ci.org/nkzawa/engine.io-client.java)

This is the Engine.IO Client Library for Java, which is simply ported from the [JavaScript client](https://github.com/LearnBoost/engine.io-client).

See also: [Socket.IO-client.java](https://github.com/nkzawa/socket.io-client.java)

## Installation
The latest artifact is available on Maven Central. Add the following dependency to your `pom.xml`.

```xml
<dependencies>
  <dependency>
    <groupId>com.github.nkzawa</groupId>
    <artifactId>engine.io-client</artifactId>
    <version>0.2.3</version>
  </dependency>
</dependencies>
```

Or to install it manually, please refer dependencies to [pom.xml](https://github.com/nkzawa/engine.io-client.java/blob/master/pom.xml).

## Usage
Engine.IO-client.java has the similar api with the JS client. You can use `Socket` to connect:

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
// default SSLContext for all sockets
Socket.setDefaultSSLContext(mySSLContext);

// set as an option
opts = new Socket.Options();
opts.sslContext = mySSLContext;
socket = new Socket(opts);
```

## Features
This library supports all of the features the JS client does, including events, options and upgrading transport. Android is fully supported.

### Extra features only for Java client
Some features are added for simulating browser behavior like handling cookies.

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
        Map<String, String> headers = (Map<String, String>)args[0];
        // send cookies to server.
        headers.put("Cookie", "foo=1;");
      }
    }).on(Transport.EVENT_RESPONSE_HEADERS, new Emitter.Listener() {
      @Override
      public void call(Object... args) {
        @SuppressWarnings("unchecked")
        Map<String, String> headers = (Map<String, String>)args[0];
        // get cookies from server.
        String cookie = headers.get("Set-Cookie");
      }
    });
  }
});
```

See the Javadoc for more details.

http://nkzawa.github.io/engine.io-client.java/apidocs/

## License

MIT

