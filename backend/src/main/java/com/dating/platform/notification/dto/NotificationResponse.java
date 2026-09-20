package com.dating.platform.notification.dto;

import com.dating.platform.notification.entity.Notification;
import com.dating.platform.notification.entity.NotificationType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(name = "NotificationResponse")
public record NotificationResponse(
        UUID id,
        NotificationType type,
        String title,
        String body,
        UUID actorId,
        String targetType,
        UUID targetId,
        String imageUrl,
        boolean read,
        Instant createdAt
) {

    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(
                n.getId(), n.getType(), n.getTitle(), n.getBody(), n.getActorId(),
                n.getTargetType(), n.getTargetId(), n.getImageUrl(), n.isRead(), n.getCreatedAt());
    }
}
