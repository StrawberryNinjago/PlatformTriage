package com.example.Triage.exception;

import java.sql.SQLException;

public class PrivilegesCheckFailedException extends RuntimeException {
    public PrivilegesCheckFailedException(String message, Exception e) {
        super(message);
    }
}
