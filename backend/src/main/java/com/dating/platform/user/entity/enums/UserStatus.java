package com.dating.platform.user.entity.enums;

public enum UserStatus {
    /** Registered but has not finished onboarding - cannot appear in discovery. */
    PENDING_ONBOARDING,
    ACTIVE,
    /** User hid their profile; can still log in. */
    PAUSED,
    DEACTIVATED,
    BANNED
}
