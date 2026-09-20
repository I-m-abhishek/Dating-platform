package com.dating.platform.interaction.entity;

public enum LikeStatus {
    /** Waiting for the recipient to decide. */
    PENDING,
    /** Recipient liked back - a Match row exists. */
    MATCHED,
    /** Recipient passed. */
    DECLINED,
    /** Sender withdrew it. */
    WITHDRAWN
}
