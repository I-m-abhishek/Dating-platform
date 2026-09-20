package com.dating.platform.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Single source of truth for every error the API can return.
 * Frontend switches on {@link #name()} - never on the human readable message.
 */
@Getter
public enum ErrorCode {

    // ---- generic -------------------------------------------------------
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Request validation failed"),
    BAD_REQUEST(HttpStatus.BAD_REQUEST, "Malformed request"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Authentication required"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Email or password is incorrect"),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "Session expired, please sign in again"),
    TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "Invalid authentication token"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "You are not allowed to perform this action"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "Resource not found"),
    CONFLICT(HttpStatus.CONFLICT, "Resource already exists"),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "Too many requests, slow down"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong on our side"),

    // ---- account -------------------------------------------------------
    EMAIL_ALREADY_REGISTERED(HttpStatus.CONFLICT, "That email is already registered"),
    ACCOUNT_DISABLED(HttpStatus.FORBIDDEN, "This account has been disabled"),
    ACCOUNT_BANNED(HttpStatus.FORBIDDEN, "This account has been banned"),
    UNDERAGE(HttpStatus.BAD_REQUEST, "You must be at least 18 years old"),
    PROFILE_INCOMPLETE(HttpStatus.PRECONDITION_REQUIRED, "Complete your profile to continue"),

    // ---- media ---------------------------------------------------------
    UNSUPPORTED_MEDIA(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "That file type is not supported"),
    FILE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "That file is too large"),
    PHOTO_LIMIT_REACHED(HttpStatus.CONFLICT, "You have reached the maximum number of photos"),

    // ---- interaction ---------------------------------------------------
    SELF_INTERACTION(HttpStatus.BAD_REQUEST, "You cannot do that to your own profile"),
    ALREADY_LIKED(HttpStatus.CONFLICT, "You already liked this profile"),
    USER_BLOCKED(HttpStatus.FORBIDDEN, "This interaction is not available"),
    NOT_MATCHED(HttpStatus.FORBIDDEN, "You need to match before you can do that"),

    // ---- chat ----------------------------------------------------------
    OPENER_LIMIT_REACHED(HttpStatus.FORBIDDEN, "Wait for a reply before sending more messages"),
    MESSAGE_TOO_LONG(HttpStatus.BAD_REQUEST, "Message is too long"),
    CONVERSATION_CLOSED(HttpStatus.FORBIDDEN, "This conversation is no longer available"),

    // ---- quota / plans -------------------------------------------------
    QUOTA_EXCEEDED(HttpStatus.PAYMENT_REQUIRED, "You have used your daily allowance"),
    PREMIUM_REQUIRED(HttpStatus.PAYMENT_REQUIRED, "This feature requires a paid plan"),
    PLAN_NOT_FOUND(HttpStatus.NOT_FOUND, "Plan not found"),
    SUBSCRIPTION_ACTIVE(HttpStatus.CONFLICT, "You already have an active subscription"),

    // ---- calls ---------------------------------------------------------
    CALL_NOT_ALLOWED(HttpStatus.FORBIDDEN, "Calling is not available for this conversation"),
    CALL_ALREADY_ACTIVE(HttpStatus.CONFLICT, "A call is already in progress");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }
}
