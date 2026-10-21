package com.example.securebackend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        String timestamp,
        int status,
        String error,
        String code,
        String message,
        String path,
        List<String> details) {

    public static ErrorResponse of(HttpStatus status, String code, String message, String path) {
        return of(status, code, message, path, null);
    }

    public static ErrorResponse of(HttpStatus status, String code, String message, String path, List<String> details) {
        return new ErrorResponse(
                Instant.now().toString(),
                status.value(),
                status.getReasonPhrase(),
                code,
                message,
                path,
                details);
    }
}
