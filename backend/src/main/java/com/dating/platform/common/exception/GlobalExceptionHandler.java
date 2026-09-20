package com.dating.platform.common.exception;

import com.dating.platform.common.response.ApiResponse;
import com.dating.platform.common.response.ApiResponse.ApiError;
import com.dating.platform.common.response.ApiResponse.FieldViolation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.List;
import java.util.UUID;

/**
 * Translates every exception into the {@link ApiResponse} envelope.
 *
 * <p>Rules:
 * <ul>
 *   <li>Expected failures ({@link BusinessException}) are logged at WARN without a stack trace.</li>
 *   <li>Unexpected failures are logged at ERROR with a generated {@code traceId} that is also
 *       returned to the client, so a user can quote it in a support ticket.</li>
 *   <li>Internal details (SQL, class names, stack traces) never reach the response body.</li>
 * </ul>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleBodyValidation(MethodArgumentNotValidException ex) {
        List<FieldViolation> violations = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new FieldViolation(fe.getField(), fe.getDefaultMessage(), fe.getRejectedValue()))
                .toList();
        List<FieldViolation> globals = ex.getBindingResult().getGlobalErrors().stream()
                .map(ge -> new FieldViolation(ge.getObjectName(), ge.getDefaultMessage(), null))
                .toList();
        List<FieldViolation> all = java.util.stream.Stream.concat(violations.stream(), globals.stream()).toList();
        return build(ErrorCode.VALIDATION_FAILED, ErrorCode.VALIDATION_FAILED.getDefaultMessage(), all, null, null);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleParamValidation(ConstraintViolationException ex) {
        List<FieldViolation> details = ex.getConstraintViolations().stream()
                .map(cv -> new FieldViolation(lastNode(cv.getPropertyPath().toString()),
                        cv.getMessage(), cv.getInvalidValue()))
                .toList();
        return build(ErrorCode.VALIDATION_FAILED, ErrorCode.VALIDATION_FAILED.getDefaultMessage(), details, null, null);
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleRateLimit(RateLimitExceededException ex) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()));
        return build(ex.getErrorCode(), ex.getMessage(), null, ex.getContext(), headers);
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex, HttpServletRequest request) {
        log.warn("Business rule rejected {} {} -> {}: {}",
                request.getMethod(), request.getRequestURI(), ex.getErrorCode(), ex.getMessage());
        return build(ex.getErrorCode(), ex.getMessage(), null, ex.getContext(), null);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthentication(AuthenticationException ex) {
        return build(ErrorCode.UNAUTHORIZED, ErrorCode.UNAUTHORIZED.getDefaultMessage(), null, null, null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        return build(ErrorCode.FORBIDDEN, ErrorCode.FORBIDDEN.getDefaultMessage(), null, null, null);
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            HttpRequestMethodNotSupportedException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleMalformed(Exception ex) {
        return build(ErrorCode.BAD_REQUEST, ErrorCode.BAD_REQUEST.getDefaultMessage(), null, null, null);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleUploadTooLarge(MaxUploadSizeExceededException ex) {
        return build(ErrorCode.FILE_TOO_LARGE, ErrorCode.FILE_TOO_LARGE.getDefaultMessage(), null, null, null);
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoHandler(NoHandlerFoundException ex) {
        return build(ErrorCode.NOT_FOUND, "No endpoint matches that path", null, null, null);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrity(DataIntegrityViolationException ex) {
        String traceId = UUID.randomUUID().toString();
        log.error("Data integrity violation [traceId={}]", traceId, ex);
        return build(ErrorCode.CONFLICT, "That operation conflicts with existing data", null, null, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex, HttpServletRequest request) {
        String traceId = UUID.randomUUID().toString();
        log.error("Unhandled exception on {} {} [traceId={}]",
                request.getMethod(), request.getRequestURI(), traceId, ex);
        ApiError error = ApiError.of(ErrorCode.INTERNAL_ERROR.name(), ErrorCode.INTERNAL_ERROR.getDefaultMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.failure(error, traceId));
    }

    private ResponseEntity<ApiResponse<Void>> build(ErrorCode code,
                                                    String message,
                                                    List<FieldViolation> details,
                                                    Object context,
                                                    HttpHeaders headers) {
        ApiError error = new ApiError(code.name(), message, details, context);
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(code.getStatus());
        if (headers != null) {
            builder.headers(headers);
        }
        return builder.body(ApiResponse.failure(error, null));
    }

    private String lastNode(String propertyPath) {
        int idx = propertyPath.lastIndexOf('.');
        return idx >= 0 ? propertyPath.substring(idx + 1) : propertyPath;
    }
}
