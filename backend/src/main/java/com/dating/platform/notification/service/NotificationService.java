package com.dating.platform.notification.service;

import com.dating.platform.common.response.PageResponse;
import com.dating.platform.notification.dto.NotificationResponse;
import com.dating.platform.notification.entity.Notification;
import com.dating.platform.notification.entity.NotificationType;
import com.dating.platform.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Persists notifications and pushes them live over STOMP.
 *
 * <p>Creation is {@code @Async}: a failed notification must never roll back the like,
 * match or message that triggered it. Delivery to a disconnected client is a no-op - the
 * row is still there when they next open the app.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    public static final String USER_QUEUE = "/queue/notifications";

    private final NotificationRepository notificationRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Async
    @Transactional
    public void notifyAsync(UUID userId, NotificationType type, String title, String body,
                            UUID actorId, String targetType, UUID targetId, String imageUrl) {
        try {
            Notification saved = notificationRepository.save(Notification.builder()
                    .userId(userId)
                    .type(type)
                    .title(title)
                    .body(body)
                    .actorId(actorId)
                    .targetType(targetType)
                    .targetId(targetId)
                    .imageUrl(imageUrl)
                    .build());
            push(userId, NotificationResponse.from(saved));
        } catch (Exception e) {
            log.error("Failed to create notification type={} for user={}", type, userId, e);
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> list(UUID userId, Pageable pageable) {
        return PageResponse.from(
                notificationRepository.findAllByUserIdOrderByCreatedAtDesc(userId, pageable),
                NotificationResponse::from);
    }

    @Transactional(readOnly = true)
    public long unreadCount(UUID userId) {
        return notificationRepository.countByUserIdAndReadFalse(userId);
    }

    @Transactional
    public void markRead(UUID userId, UUID notificationId) {
        notificationRepository.markRead(notificationId, userId);
    }

    @Transactional
    public void markAllRead(UUID userId) {
        notificationRepository.markAllRead(userId);
    }

    /** Fire-and-forget live delivery; used directly for ephemeral events such as typing. */
    public void push(UUID userId, Object payload) {
        try {
            messagingTemplate.convertAndSendToUser(userId.toString(), USER_QUEUE, payload);
        } catch (Exception e) {
            log.debug("Live push to user {} failed: {}", userId, e.getMessage());
        }
    }
}
