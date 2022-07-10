
1.0.2 / 2022-07-10
==================

From the "1.x" branch.

### Bug Fixes

* check the type of the initial packet ([319f2e2](https://github.com/socketio/engine.io-client-java/commit/319f2e21bedced2866790671b3ae9ae7b0fabb82))
* increase the readTimeout value of the default OkHttpClient ([2d87497](https://github.com/socketio/engine.io-client-java/commit/2d874971c2428a7a444b3a33afe66aedcdce3a96))



2.1.0 / 2022-07-10
==================

### Features

* create heartbeat scheduler with named threads and as daemon ([#106](https://github.com/socketio/engine.io-client-java/issues/106)) ([7c9c382](https://github.com/socketio/engine.io-client-java/commit/7c9c382505f7411544add5a68fa326df3b82d2c1))

### Bug Fixes

* increase the readTimeout value of the default OkHttpClient ([fb531fa](https://github.com/socketio/engine.io-client-java/commit/fb531fab30968a4b65a402c81f37e92dd5671f33))



2.0.0 / 2020-12-11
==================

### Features

* add an extraHeaders option ([dfe65e3](https://github.com/socketio/engine.io-client-java/commit/dfe65e3b3b5eab4c3fddb9dfbf53d684fe461043))
* add support for Engine.IO v4 ([41f89a3](https://github.com/socketio/engine.io-client-java/commit/41f89a38b7594f54ee9906bc91051874a60b690d))

### Bug Fixes

* check the type of the initial packet ([2b5dfb9](https://github.com/socketio/engine.io-client-java/commit/2b5dfb99f8f865362ddc0a17f52e8b70269d7572))


1.0.1 / 2020-12-10
==================

### Bug Fixes

* handle responses without content type ([#101](https://github.com/socketio/engine.io-client-java/issues/101)) ([6f065b7](https://github.com/socketio/engine.io-client-java/commit/6f065b7a62603730979d43cec71af0046ca4ab7c))

1.0.0 / 2017-07-14
==================

* compatible with engine.io 3.1.0
* update parser: no strict UTF8 check
* no UTF encodng for payloads which contains string only
* add `transportOptions` option

0.9.0 / 2017-07-11
==================

* compatible with engine.io 1.8.4
* add options for injecting OKHttpClient [@b95505017]
* emit data on `closing` state on onPacket as well
* set default accept header
* update okhttp

0.8.3 / 2016-12-12
==================

* replace okhttp-ws with okhttp (#78) [@b95505017]

0.8.2 / 2016-10-22
==================

* fix IllegalStateException error of websocket [eckovation]

0.8.1 / 2016-09-27
==================

* fix NullPointerException [eckovation]

0.8.0 / 2016-09-19
==================

* update okhttp-ws [VicV]
* proxy support [Eugene-Kudelevsky]
* several code improvements [georgekankava]
* EventThread: log exceptions [Dominik Auf der Maur]
* EventThread: extend deamon property from parent thread [vach]
* Yeast: fix infinite loop bug [erikogenvik]
* WebSocket: close the web socket after sending a close frame [dave-r12]
* WebSocket: fix "must call close()" crash [chendrak]
* Polling: disconnect after closing stream/reader [wzurita]
* UTF8Exception: remove the data property
* test: use TLSv1

0.7.0 / 2016-02-01
==================

* compatible with engine.io 1.6.8
* bump okhttp-ws [b95505017]
* IPv6 support
* use better cache busting id
* add ping and pong events
* improve firing of drain in websocket transport

0.6.3 / 2015-12-23
==================

* bump okhttp-ws [b95505017]

0.6.2 / 2015-10-10
==================

* compatible with engine.io 1.5.4
* bump okhttp-ws [b95505017]
* check lone surrogate
* fix NPE of polling request on Android [ZeroBrain]

0.6.1 / 2015-08-31
==================

* change package name to "io.socket"

0.6.0 / 2015-08-09
==================

* change to take all HTTP HEADER values as lists
* fix NullPointerException

0.5.1 / 2015-06-06
==================

* bump okhttp-ws [b95505017]
* fix parsing url query

0.5.0 / 2015-05-02
==================

* replace WebSocket client with OkHttp WebSocket [b95505017]
* add the hostnameVerifier option [b95505017]
* fix EVENT_TRANSPORT event to fire upon a transport creation
* fix invalid transport null check

0.4.1 / 2015-02-08
==================

* set connect timeout for polling

0.4.0 / 2015-01-25
==================

* compatible with engine.io 1.5.1
* fix default port detection when host is specified
* add `Socket#id()` for retrieving `id`
* make `Socket.priorWebsocketSuccess` private

0.3.1 / 2014-11-04
==================

* compatible with engine.io 1.4.2
* fixed transport close deferring logic
* wait for buffer to be drained before closing
