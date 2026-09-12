package com.siva.jobscheduler.dto;

import java.time.Instant;

public record ErrorResponse(
        String code,
        String message,
        String timestamp,
        String path
) {
    public static ErrorResponse of(String code, String message, String path) {
        return new ErrorResponse(code, message, Instant.now().toString(), path);
    }
}
