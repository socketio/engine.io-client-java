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
  var header = req.headers['x-engineio'];
  if (header) {
    res.setHeader('X-EngineIO', header);
  }

  handleRequest.call(this, req, res);
};
