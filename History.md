0.5.1 / 2015-05-06
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
