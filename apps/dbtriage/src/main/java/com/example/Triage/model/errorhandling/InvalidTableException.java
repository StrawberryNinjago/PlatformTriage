package com.example.Triage.model.errorhandling;

public class InvalidTableException extends RuntimeException {
    public InvalidTableException(String message) {
        super(message);
    }
}
