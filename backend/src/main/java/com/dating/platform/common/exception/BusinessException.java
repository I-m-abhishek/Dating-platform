package com.dating.platform.common.exception;

import lombok.Getter;

/**
 * Base class for every expected (non-bug) failure. Carries an {@link ErrorCode}
 * so the exception handler can map it to an HTTP status and a stable code.
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final transient Object context;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, errorCode.getDefaultMessage(), null);
    }

    public BusinessException(ErrorCode errorCode, String message) {
        this(errorCode, message, null);
    }

    public BusinessException(ErrorCode errorCode, String message, Object context) {
        super(message);
        this.errorCode = errorCode;
        this.context = context;
    }
}
