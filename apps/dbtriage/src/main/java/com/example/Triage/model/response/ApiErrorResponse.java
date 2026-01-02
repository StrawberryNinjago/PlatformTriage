package com.example.Triage.model.response;

import com.example.Triage.exception.ApiError;

import lombok.Builder;

@Builder
public record ApiErrorResponse(ApiError error) {
}
