package com.example.Triage.exception;

import java.util.Map;

public abstract class DbDoctorException extends RuntimeException {
    private final String code;
    private final Map<String, Object> context;

    protected DbDoctorException(String code, String message, Throwable cause, Map<String, Object> context) {
        super(message, cause);
        this.code = code;
        this.context = context == null ? Map.of() : context;
    }

    public String code() {
        return code;
    }

    public Map<String, Object> context() {
        return context;
    }
}
