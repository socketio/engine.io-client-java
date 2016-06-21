var fs = require('fs');
var engine = require('engine.io');

var http;
if (process.env.SSL) {
  http = require('https').createServer({
    key: fs.readFileSync(__dirname + '/key.pem'),
    cert: fs.readFileSync(__dirname + '/cert.pem')
  });
} else {
  http = require('http').createServer();
}

var server = engine.attach(http, {pingInterval: 500});

var port = process.env.PORT || 3000
http.listen(port, function() {
  console.log('Engine.IO server listening on port', port);
});

server.on('connection', function(socket) {
  socket.send('hi');

  socket.on('message', function(message) {
    socket.send(message);
  });

  socket.on('error', function(err) {
    throw err;
  });
}).on('error', function(err) {
  console.error(err);
});


function before(context, name, fn) {
  var method = context[name];
  context[name] = function() {
    fn.apply(this, arguments);
    return method.apply(this, arguments);
  };
}

before(server, 'handleRequest', function(req, res) {
  // echo a header value
  var value = req.headers['x-engineio'];
  if (!value) return;
  res.setHeader('X-EngineIO', ['hi', value]);
});

before(server, 'handleUpgrade', function(req, socket, head) {
  // echo a header value for websocket handshake
  var value = req.headers['x-engineio'];
  if (!value) return;
  this.ws.once('headers', function(headers) {
    headers.push('X-EngineIO: hi');
    headers.push('X-EngineIO: ' + value);
  });
});
