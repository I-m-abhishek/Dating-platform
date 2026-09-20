package com.dating.platform.comment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

@Schema(name = "CreateCommentRequest")
public record CreateCommentRequest(

        @NotBlank(message = "Write something first")
        @Size(max = 300, message = "Comments are limited to 300 characters")
        String body,

        /** Present when replying to an existing comment. */
        UUID parentCommentId
) {
}
