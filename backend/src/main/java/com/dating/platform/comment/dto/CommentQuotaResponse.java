package com.dating.platform.comment.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * Shown above the comment box: "2 of 5 left today".
 *
 * <p>The client reads the limit from here rather than hardcoding 5, so changing the free
 * allowance is a configuration change, not a release.
 */
@Schema(name = "CommentQuotaResponse")
public record CommentQuotaResponse(
        int limit,
        int used,
        int remaining,
        boolean unlimited,
        Instant resetsAt,
        String upgradeHint
) {
}
