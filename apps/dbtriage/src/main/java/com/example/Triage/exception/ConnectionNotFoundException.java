package com.example.Triage.exception;

public class ConnectionNotFoundException extends RuntimeException {
    public ConnectionNotFoundException(String message) {
        super(message);
    }
}
