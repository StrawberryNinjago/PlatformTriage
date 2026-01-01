package com.example.Triage.model.errorhandling;

public class ConnectionNotFoundException extends RuntimeException {
    public ConnectionNotFoundException(String message) {
        super(message);
    }
}
