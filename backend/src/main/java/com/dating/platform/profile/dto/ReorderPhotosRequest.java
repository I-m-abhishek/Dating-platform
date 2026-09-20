package com.dating.platform.profile.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

@Schema(name = "ReorderPhotosRequest")
public record ReorderPhotosRequest(

        @NotEmpty(message = "Send the photo ids in their new order")
        @Size(max = 9, message = "A profile holds at most 9 photos")
        List<UUID> photoIdsInOrder
) {
}
