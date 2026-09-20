package com.dating.platform.common.exception;

public class ConflictException extends BusinessException {

    public ConflictException(String message) {
        super(ErrorCode.CONFLICT, message);
    }

    public ConflictException(ErrorCode code, String message) {
        super(code, message);
    }
}
