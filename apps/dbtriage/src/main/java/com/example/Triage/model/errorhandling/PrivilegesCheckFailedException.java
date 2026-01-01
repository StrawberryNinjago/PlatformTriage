package com.example.Triage.model.errorhandling;

public class PrivilegesCheckFailedException extends RuntimeException {
    public PrivilegesCheckFailedException(String message) {
        super(message);
    }
}
