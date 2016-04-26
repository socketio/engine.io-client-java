package io.socket.engineio.client;

public class CloseException extends Exception {

    public CloseException() {
        super();
    }

    public CloseException(String message) {
        super(message);
    }

    public CloseException(String message, Throwable cause) {
        super(message, cause);
    }

    public CloseException(Throwable cause) {
        super(cause);
    }
}
