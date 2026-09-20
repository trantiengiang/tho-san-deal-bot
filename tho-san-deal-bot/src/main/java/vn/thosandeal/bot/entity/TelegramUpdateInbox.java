package vn.thosandeal.bot.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.thosandeal.bot.enums.InboxStatus;

import java.time.Instant;

/**
 * Durable inbox for Telegram updates. Primary key is update_id from Telegram.
 *
 * <p>Idempotency guarantee:
 * The controller uses "INSERT ... ON CONFLICT (update_id) DO NOTHING" semantics.
 * If the returned affected rows == 0, the update is a duplicate and is skipped.
 * This prevents any business logic from running twice for the same Telegram update.
 *
 * <p>The controller persists this record BEFORE returning HTTP 200 to Telegram.
 * This ensures no update is lost even if the application crashes after acknowledgement.
 *
 * <p>MandatoryFix #1, #2.
 */
@Entity
@Table(name = "telegram_update_inbox")
@Getter
@Setter
@NoArgsConstructor
public class TelegramUpdateInbox {

    /**
     * Telegram's own update_id is the primary key — guarantees uniqueness at DB level.
     */
    @Id
    @Column(name = "update_id", nullable = false)
    private Long updateId;

    @Column(name = "payload", columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private InboxStatus status;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "error_message", length = 2000)
    private String errorMessage;

    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

    public static TelegramUpdateInbox received(Long updateId, String payload) {
        TelegramUpdateInbox inbox = new TelegramUpdateInbox();
        inbox.updateId = updateId;
        inbox.payload = payload;
        inbox.status = InboxStatus.RECEIVED;
        inbox.receivedAt = Instant.now();
        inbox.retryCount = 0;
        return inbox;
    }
}
