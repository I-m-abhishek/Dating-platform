package com.dating.platform.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/**
 * Uniform envelope returned by every endpoint of the API.
 *
 * <p>Success: {@code {"success":true,"data":{...},"meta":{...}}}<br>
 * Failure: {@code {"success":false,"error":{"code":"...","message":"...","details":[...]}}}
 *
 * <p>Controllers never build this by hand for errors — {@code GlobalExceptionHandler} does.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "ApiResponse", description = "Standard response envelope")
public record ApiResponse<T>(
        boolean success,
        String message,
        T data,
        ApiError error,
        Instant timestamp,
        String traceId
) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, null, data, null, Instant.now(), null);
    }

    public static <T> ApiResponse<T> success(T data, String message) {
        return new ApiResponse<>(true, message, data, null, Instant.now(), null);
    }

    public static ApiResponse<Void> success(String message) {
        return new ApiResponse<>(true, message, null, null, Instant.now(), null);
    }

    public static <T> ApiResponse<T> failure(ApiError error, String traceId) {
        return new ApiResponse<>(false, null, null, error, Instant.now(), traceId);
    }

    /** Machine readable error payload. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(name = "ApiError")
    public record ApiError(
            String code,
            String message,
            List<FieldViolation> details,
            Object context
    ) {
        public static ApiError of(String code, String message) {
            return new ApiError(code, message, null, null);
        }

        public static ApiError of(String code, String message, List<FieldViolation> details) {
            return new ApiError(code, message, details, null);
        }

        public static ApiError of(String code, String message, Object context) {
            return new ApiError(code, message, null, context);
        }
    }

    /** One failed bean-validation constraint. */
    @Schema(name = "FieldViolation")
    public record FieldViolation(String field, String message, Object rejectedValue) {
    }
}
