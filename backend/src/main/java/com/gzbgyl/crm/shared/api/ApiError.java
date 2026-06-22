package com.gzbgyl.crm.shared.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiError(
        String code,
        String message,
        Instant timestamp,
        String path,
        Map<String, String> fieldErrors) {

    public static ApiError of(String code, String message, String path) {
        return new ApiError(code, message, Instant.now(), path, null);
    }

    public static ApiError validation(String message, String path, Map<String, String> fields) {
        return new ApiError("VALIDATION_FAILED", message, Instant.now(), path, Map.copyOf(fields));
    }
}
