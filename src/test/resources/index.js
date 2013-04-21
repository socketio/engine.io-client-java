var engine = require('engine.io')
  , port = parseInt(process.argv[2], 10) || 3000
  , server = engine.listen(port, function() {
      console.log('Engine.IO server listening on port', port);
    });

server.on('connection', function(socket) {
  socket.send('hello client');

  socket.on('packet', function(packet) {
    console.log('packet:', packet);
  });

  socket.on('packetCreate', function(packet) {
    console.log('packetCreate:', packet);
  });

  socket.on('message', function(message) {
    console.log('message:', message);
    socket.send(message);
  });

  socket.on('close', function(reason, desc) {
    console.log('close:', reason, desc);
  });

  socket.on('error', function(err) {
    console.log('error:', err);
  });
});

