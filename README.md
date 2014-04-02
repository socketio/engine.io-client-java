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
    <version>0.1.3</version>
  </dependency>
</dependencies>
```

## Usage
Engine.IO-client.java has the similar api with the JS client. You can use `Socket` to connect:

```java
socket = new Socket("ws://localhost") {
  @Override
  public void onopen() {
    socket.send("hi");
    socket.close();
  }

  @Override
  public void onmessage(String data) {}

  @Override
  public void onclose() {}

  @Override
  public void onerror(Exception err) {}
};
socket.open();
```

You can receive events as follows:

```java
socket.on(Socket.EVENT_ERROR, new Emitter.Listener() {
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

socket = new Socket(opts) { ... };
```

Sending and receiving binary data:

```java
socket = new Socket() {
  @Override
  public void onopen() {
    // send binary data
    byte[] data = new byte[42];
    socket.send(data);
  }

  @Override
  public void onmessage(byte[] data) {
    // receive binary data
  }
  ...
};
```

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
        String cookie = headers.get("Set-Cookie"));
      }
    });
  }
});
```

See the Javadoc for more details.

http://nkzawa.github.io/engine.io-client.java/apidocs/


## Features
This library supports all of the features the JS client does, including events, options and upgrading transport. Android is fully supported.

## License

MIT

