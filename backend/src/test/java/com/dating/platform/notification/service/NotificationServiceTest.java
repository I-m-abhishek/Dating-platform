package com.dating.platform.notification.service;

import com.dating.platform.notification.entity.NotificationType;
import com.dating.platform.notification.repository.NotificationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class NotificationServiceTest {

    private final List<Runnable> submitted = new ArrayList<>();
    private final NotificationService service = new NotificationService(
            mock(NotificationRepository.class), new SimpMessagingTemplate((message, timeout) -> true),
            submitted::add, mock(PlatformTransactionManager.class));

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    /**
     * The recipient reloads their match list the moment the push arrives, so it must not
     * arrive before the match is committed.
     */
    @Test
    void insideATransactionTheNotificationWaitsForTheCommit() {
        TransactionSynchronizationManager.initSynchronization();

        notifyMatch();
        assertThat(submitted).isEmpty();

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        assertThat(submitted).hasSize(1);
    }

    @Test
    void aRolledBackTransactionNeverNotifies() {
        TransactionSynchronizationManager.initSynchronization();

        notifyMatch();
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        assertThat(submitted).isEmpty();
    }

    @Test
    void outsideATransactionItDispatchesImmediately() {
        notifyMatch();
        assertThat(submitted).hasSize(1);
    }

    private void notifyMatch() {
        service.notifyAsync(UUID.randomUUID(), NotificationType.NEW_MATCH, "It is a match",
                "Say hello", UUID.randomUUID(), "MATCH", UUID.randomUUID(), null);
    }
}
