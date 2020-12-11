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
