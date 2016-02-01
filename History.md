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
