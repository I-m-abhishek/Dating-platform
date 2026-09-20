package com.dating.platform.match.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(name = "UnmatchRequest")
public record UnmatchRequest(

        @Size(max = 200)
        String reason,

        /** Also block the other person, the common case when unmatching for safety. */
        boolean alsoBlock
) {
}
