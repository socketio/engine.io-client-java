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
