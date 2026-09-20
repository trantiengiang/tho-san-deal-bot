package vn.thosandeal.bot.service.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.thosandeal.bot.enums.OutboxStatus;
import vn.thosandeal.bot.repository.NotificationOutboxRepository;

import java.time.Instant;

/**
 * Service managing database state transitions for notification outbox delivery.
 * Enforces transaction boundaries via Spring proxy without self-invocation.
 */
@Service
public class NotificationOutboxDeliveryService {

    private static final Logger log = LoggerFactory.getLogger(NotificationOutboxDeliveryService.class);

    private final NotificationOutboxRepository repository;

    public NotificationOutboxDeliveryService(NotificationOutboxRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void markSent(Long outboxId) {
        int rows = repository.updateDeliveryResult(outboxId, OutboxStatus.SENT, Instant.now(), null);
        if (rows == 0) {
            log.warn("Expected 1 row updated for outboxId={}, but got 0", outboxId);
        }
    }

    @Transactional
    public void markPermanentFailure(Long outboxId, String error) {
        int rows = repository.updateDeliveryResult(outboxId, OutboxStatus.FAILED, null, error);
        if (rows == 0) {
            log.warn("Expected 1 row updated for outboxId={}, but got 0", outboxId);
        }
    }

    @Transactional
    public void markRetryableFailure(Long outboxId, String error) {
        int rows = repository.updateDeliveryResult(outboxId, OutboxStatus.PENDING, null, error);
        if (rows == 0) {
            log.warn("Expected 1 row updated for outboxId={}, but got 0", outboxId);
        }
    }
}
