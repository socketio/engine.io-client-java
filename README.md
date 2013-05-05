# Engine.IO-client.java
[![Build Status](https://travis-ci.org/nkzawa/engine.io-client.java.png?branch=master)](https://travis-ci.org/nkzawa/engine.io-client.java)

This is the Engine.IO Client Library for Java, which is simply ported from the [client](https://github.com/LearnBoost/engine.io-client) for JavaScript.

See also: https://github.com/nkzawa/socket.io-client.java

## Usage
Engine.IO-client.java has the similar api with the js client. You can use `Socket` to connect:

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

See the Javadoc for more details.

http://nkzawa.github.io/engine.io-client.java/apidocs/


## Features
This library supports all of the features the JS client does, including events, options and upgraging transport.

## License

MIT

