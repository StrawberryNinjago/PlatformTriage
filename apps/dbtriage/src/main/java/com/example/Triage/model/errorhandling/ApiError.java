package com.example.Triage.model.errorhandling;

public record ApiError(String code, String message) {
    public ApiError(String code, String message) {
        this.code = code;
        this.message = message;
    }
}