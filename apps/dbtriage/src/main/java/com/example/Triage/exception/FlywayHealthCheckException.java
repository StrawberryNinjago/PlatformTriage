package com.example.Triage.exception;

public class FlywayHealthCheckException extends RuntimeException {
    public FlywayHealthCheckException(String message, Throwable cause) {
        super(message, cause);
    }
}
