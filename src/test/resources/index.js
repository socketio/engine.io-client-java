var engine = require('engine.io');

var port = parseInt(process.argv[2], 10) || 3000
var server = engine.listen(port, function() {
  console.log('Engine.IO server listening on port', port);
});

server.on('connection', function(socket) {
  socket.send('hello client');

  socket.on('message', function(message) {
    socket.send(message);
  });

  socket.on('error', function(err) {
    throw err;
  });
});

var handleRequest = server.handleRequest;
server.handleRequest = function(req, res) {
  // echo a header value
  var value = req.headers['x-engineio'];
  if (value) {
    res.setHeader('X-EngineIO', value);
  }

  handleRequest.call(this, req, res);
};

var headerValue;
var handleUpgrade = server.handleUpgrade;
server.handleUpgrade = function(req, socket, head) {
  // echo a header value for websocket handshake
  headerValue = req.headers['x-engineio'];
  handleUpgrade.call(this, req, socket, head);
};

// FIXME: support parallel requests
server.ws.on('headers', function(headers) {
  if (headerValue) {
    headers.push('X-EngineIO: ' + headerValue);
  }
});
