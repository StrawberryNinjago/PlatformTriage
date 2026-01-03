package com.example.Triage.exception;

import java.util.Map;

public class SqlAnalysisException extends DbDoctorException {
    public SqlAnalysisException(String message) {
        super("SQL_ANALYSIS_ERROR", message, null, Map.of());
    }

    public SqlAnalysisException(String message, Throwable cause) {
        super("SQL_ANALYSIS_ERROR", message, cause, Map.of());
    }

    public SqlAnalysisException(String message, Map<String, Object> context) {
        super("SQL_ANALYSIS_ERROR", message, null, context);
    }
}

