package com.dating.platform.common.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Cursor pagination, used for feeds and chat history where offset pagination
 * would drift as new rows arrive.
 */
@Schema(name = "CursorPageResponse")
public record CursorPageResponse<T>(List<T> items, String nextCursor, boolean hasMore) {

    public static <T> CursorPageResponse<T> of(List<T> items, String nextCursor) {
        return new CursorPageResponse<>(items, nextCursor, nextCursor != null);
    }

    public static <T> CursorPageResponse<T> empty() {
        return new CursorPageResponse<>(List.of(), null, false);
    }
}
