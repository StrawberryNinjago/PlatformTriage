package com.example.Triage.exception;

public class InvalidTableException extends RuntimeException {
    public InvalidTableException(String message) {
        super(message);
    }
}
