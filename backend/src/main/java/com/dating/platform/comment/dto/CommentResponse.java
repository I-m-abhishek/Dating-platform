package com.dating.platform.comment.dto;

import com.dating.platform.user.dto.UserSummaryResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(name = "CommentResponse")
public record CommentResponse(
        UUID id,
        UUID photoId,
        UserSummaryResponse author,
        String body,
        UUID parentCommentId,
        int replyCount,
        List<CommentResponse> replies,
        boolean canDelete,
        Instant createdAt
) {
}
