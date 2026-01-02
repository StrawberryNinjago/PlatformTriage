package com.example.Triage.exception;

public record ApiError(String code, String message) {
    public ApiError(String code, String message) {
        this.code = code;
        this.message = message;
    }
}
