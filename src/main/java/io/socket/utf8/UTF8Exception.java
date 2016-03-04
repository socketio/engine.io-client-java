package io.socket.utf8;

import java.io.IOException;

public class UTF8Exception extends IOException {

    private String data;

    public UTF8Exception() {
        super();
    }

    public UTF8Exception(String message) {
        super(message);
    }

    public UTF8Exception(String message, Throwable cause) {
        super(message, cause);
    }

    public UTF8Exception(Throwable cause) {
        super(cause);
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }
}
