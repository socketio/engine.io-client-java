# Engine.IO-client.java
[![Build Status](https://travis-ci.org/nkzawa/engine.io-client.java.png?branch=master)](https://travis-ci.org/nkzawa/engine.io-client.java)

This is the Engine.IO Client Library for Java, which is simply ported from the [client](https://github.com/LearnBoost/engine.io-client) for JavaScript.

## Hello World
Engine.IO-client.java has the similar api with the client for js. You can use `Socket` to connect as follows:

```java
Socket socket = new Socket("ws://localhost") {
  @Override
  public void onopen() {}

  @Override
  public void onmessage(String data) {}

  @Override
  public void onclose() {}
};
socket.open();
```

## Features
This library supports all of the features the JS client does, except the Flash transport, including events, options and upgrage.

## License

MIT

