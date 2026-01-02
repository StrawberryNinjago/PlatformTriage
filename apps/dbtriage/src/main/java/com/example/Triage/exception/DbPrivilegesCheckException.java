package com.example.Triage.exception;

import java.util.Map;

public class DbPrivilegesCheckException extends DbDoctorException {
    public DbPrivilegesCheckException(String code, Throwable cause, Map<String, Object> context) {
        super(code, "Privileges check failed", cause, context);
    }
}
