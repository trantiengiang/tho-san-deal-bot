package vn.thosandeal.bot.service.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import vn.thosandeal.bot.client.TelegramApiException;
import vn.thosandeal.bot.client.TelegramClient;
import vn.thosandeal.bot.dto.request.SendMessageRequest;
import vn.thosandeal.bot.entity.NotificationOutbox;
import vn.thosandeal.bot.enums.OutboxStatus;
import vn.thosandeal.bot.repository.NotificationOutboxRepository;

import java.util.List;

/**
 * Polls the notification_outbox table and delivers pending notifications via Telegram.
 *
 * <p>This worker is separate from the price check scheduler to enforce the
 * DB-transaction / HTTP-call separation (MandatoryFix #9).
 *
 * <p>Retry strategy (MandatoryFix #11):
 * - Max 3 attempts per outbox entry
 * - On 429: log and leave as PENDING (will retry on next cycle)
 * - On 5xx/timeout: increment attempt_count, leave as PENDING until max attempts
 * - On 4xx permanent: mark FAILED immediately, no further retries
 * - On max attempts exceeded: mark FAILED
 *
 * <p>Uses fixedDelay so no concurrent runs overlap.
 */
@Component
@ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class NotificationOutboxWorker {

    private static final Logger log = LoggerFactory.getLogger(NotificationOutboxWorker.class);
    private static final int MAX_ATTEMPTS = 3;

    private final NotificationOutboxRepository outboxRepository;
    private final TelegramClient telegramClient;
    private final NotificationOutboxDeliveryService deliveryService;

    public NotificationOutboxWorker(NotificationOutboxRepository outboxRepository,
                                    TelegramClient telegramClient,
                                    NotificationOutboxDeliveryService deliveryService) {
        this.outboxRepository = outboxRepository;
        this.telegramClient = telegramClient;
        this.deliveryService = deliveryService;
    }

    /**
     * Runs every 15 seconds (configurable). Uses fixedDelayString to prevent overlap.
     */
    @Scheduled(fixedDelayString = "${pricing.outbox-interval-ms:15000}")
    public void processOutbox() {
        List<NotificationOutbox> pending = outboxRepository.findByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING);
        if (pending.isEmpty()) {
            return;
        }

        log.debug("NotificationOutboxWorker: processing {} pending entries", pending.size());

        for (NotificationOutbox entry : pending) {
            try {
                processEntry(entry);
            } catch (Exception e) {
                log.error("Failed processing outboxId={}, continuing to next entry", entry.getId(), e);
            }
        }
    }

    private void processEntry(NotificationOutbox entry) {
        if (entry.getAttemptCount() >= MAX_ATTEMPTS) {
            log.warn("Outbox entry id={} exceeded max attempts={}, marking FAILED", 
                    entry.getId(), MAX_ATTEMPTS);
            deliveryService.markPermanentFailure(entry.getId(), "Max attempts exceeded");
            return;
        }

        try {
            SendMessageRequest request = SendMessageRequest.html(
                    entry.getTelegramChatId(),
                    entry.getMessageText()
            );
            telegramClient.sendMessage(request);
            deliveryService.markSent(entry.getId());
            log.info("Sent notification outboxId={} to chatId={}", 
                    entry.getId(), entry.getTelegramChatId());

        } catch (TelegramApiException e) {
            handleTelegramError(entry, e);
        } catch (Exception e) {
            log.error("Unexpected error sending notification outboxId={}", entry.getId(), e);
            deliveryService.markRetryableFailure(entry.getId(), "Unexpected error: " + e.getMessage());
        }
    }

    private void handleTelegramError(NotificationOutbox entry, TelegramApiException e) {
        if (e.getHttpStatus() == 429) {
            log.warn("Telegram rate limited (429) for outboxId={}, will retry next cycle", entry.getId());
            deliveryService.markRetryableFailure(entry.getId(), "Rate limited (429)");
        } else if (!e.isRetryable()) {
            // 4xx permanent error — no point retrying
            log.error("Permanent Telegram error for outboxId={} status={}, marking FAILED",
                    entry.getId(), e.getHttpStatus());
            deliveryService.markPermanentFailure(entry.getId(),
                    "Permanent error status=" + e.getHttpStatus());
        } else {
            // Retryable 5xx or timeout
            log.warn("Retryable Telegram error for outboxId={} status={}, attempt={}/{}",
                    entry.getId(), e.getHttpStatus(), entry.getAttemptCount() + 1, MAX_ATTEMPTS);
            deliveryService.markRetryableFailure(entry.getId(), e.getMessage());
        }
    }
}
