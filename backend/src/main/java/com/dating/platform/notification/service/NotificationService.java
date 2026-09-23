package com.dating.platform.notification.service;

import com.dating.platform.common.response.PageResponse;
import com.dating.platform.notification.dto.NotificationResponse;
import com.dating.platform.notification.entity.Notification;
import com.dating.platform.notification.entity.NotificationType;
import com.dating.platform.notification.repository.NotificationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * Persists notifications and pushes them live over STOMP.
 *
 * <p>Creation runs in the background, in its own transaction: a failed notification must
 * never roll back the like, match or message that triggered it. Delivery to a disconnected
 * client is a no-op - the row is still there when they next open the app.
 *
 * <p><b>Only after the caller commits.</b> A notification used to be fired from inside the
 * transaction that created the match (or message), so it could reach the other person
 * before that transaction committed. Their app reacted by reloading its match and chat
 * lists, read the database a few milliseconds too early, and showed nothing new until a
 * manual refresh. Dispatch now waits for the commit - and never fires for a rollback.
 */
@Slf4j
@Service
public class NotificationService {

    public static final String USER_QUEUE = "/queue/notifications";

    private final NotificationRepository notificationRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final Executor executor;
    private final TransactionTemplate transactionTemplate;

    public NotificationService(NotificationRepository notificationRepository,
                               SimpMessagingTemplate messagingTemplate,
                               @Qualifier("applicationTaskExecutor") Executor executor,
                               PlatformTransactionManager transactionManager) {
        this.notificationRepository = notificationRepository;
        this.messagingTemplate = messagingTemplate;
        this.executor = executor;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public void notifyAsync(UUID userId, NotificationType type, String title, String body,
                            UUID actorId, String targetType, UUID targetId, String imageUrl) {
        Runnable dispatch = () -> executor.execute(() -> create(
                userId, type, title, body, actorId, targetType, targetId, imageUrl));

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    dispatch.run();
                }
            });
        } else {
            dispatch.run();
        }
    }

    private void create(UUID userId, NotificationType type, String title, String body,
                        UUID actorId, String targetType, UUID targetId, String imageUrl) {
        try {
            Notification saved = transactionTemplate.execute(status -> notificationRepository.save(Notification.builder()
                    .userId(userId)
                    .type(type)
                    .title(title)
                    .body(body)
                    .actorId(actorId)
                    .targetType(targetType)
                    .targetId(targetId)
                    .imageUrl(imageUrl)
                    .build()));
            if (saved != null) {
                push(userId, NotificationResponse.from(saved));
            }
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
