package vn.thosandeal.bot.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.thosandeal.bot.config.TelegramProperties;
import vn.thosandeal.bot.dto.telegram.TelegramUpdate;
import vn.thosandeal.bot.event.TelegramUpdateEvent;
import vn.thosandeal.bot.service.telegram.TelegramUpdateInboxService;

/**
 * Webhook endpoint for Telegram Bot API.
 *
 * <p>Controller responsibilities (thin):
 * 1. Validate X-Telegram-Bot-Api-Secret-Token header
 * 2. Durably persist update to inbox (before returning 200!)
 * 3. Return HTTP 200 immediately
 * 4. Publish TelegramUpdateEvent for async processing
 *
 * <p>NO business logic here. No DB queries beyond inbox insert.
 *
 * <p>Security (MandatoryFix #18):
 * - Secret compared using constant-time comparison to prevent timing attacks
 * - Secret is never logged
 * - 401 returned for invalid/missing secret
 *
 * <p>Idempotency (MandatoryFix #1, #2):
 * - Inbox insert is atomic (ON CONFLICT DO NOTHING)
 * - Duplicate update_id → 200 returned but no event published
 */
@RestController
@RequestMapping("/api/telegram")
public class TelegramWebhookController {

    private static final Logger log = LoggerFactory.getLogger(TelegramWebhookController.class);

    private final TelegramProperties telegramProperties;
    private final TelegramUpdateInboxService inboxService;
    private final ApplicationEventPublisher eventPublisher;

    public TelegramWebhookController(TelegramProperties telegramProperties,
                                     TelegramUpdateInboxService inboxService,
                                     ApplicationEventPublisher eventPublisher) {
        this.telegramProperties = telegramProperties;
        this.inboxService = inboxService;
        this.eventPublisher = eventPublisher;
    }

    /**
     * POST /api/telegram/webhook
     * Telegram sends all updates to this endpoint.
     */
    @PostMapping("/webhook")
    public ResponseEntity<Void> handleWebhook(
            @RequestHeader(value = "X-Telegram-Bot-Api-Secret-Token", required = false) String secretToken,
            @RequestBody TelegramUpdate update) {

        // Validate secret (MandatoryFix #18)
        if (!isValidSecret(secretToken)) {
            log.warn("Received webhook request with invalid or missing secret token");
            return ResponseEntity.status(401).build();
        }

        if (update == null || update.updateId() == null) {
            log.warn("Received null or invalid Telegram update");
            return ResponseEntity.ok().build();
        }

        log.debug("Received Telegram update_id={}", update.updateId());

        // Durable persist BEFORE returning 200 (MandatoryFix #2)
        // If this is a duplicate, persistIfNew returns false — no event published
        boolean isNew = inboxService.persistIfNew(update);

        // Always return 200 to Telegram (MandatoryFix #2)
        // This prevents Telegram from retrying endlessly
        if (isNew) {
            eventPublisher.publishEvent(new TelegramUpdateEvent(update));
        }

        return ResponseEntity.ok().build();
    }

    /**
     * Constant-time comparison to prevent timing attacks.
     * Never logs actual secret values.
     */
    private boolean isValidSecret(String provided) {
        String expected = telegramProperties.webhookSecret();
        if (provided == null || expected == null) {
            return false;
        }
        if (provided.length() != expected.length()) {
            return false;
        }
        // Constant-time comparison
        int diff = 0;
        for (int i = 0; i < provided.length(); i++) {
            diff |= (provided.charAt(i) ^ expected.charAt(i));
        }
        return diff == 0;
    }
}
