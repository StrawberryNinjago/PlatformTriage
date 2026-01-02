package com.example.Triage.exception;

public class PrivilegesCheckFailedException extends RuntimeException {
    public PrivilegesCheckFailedException(String message) {
        super(message);
    }
}
