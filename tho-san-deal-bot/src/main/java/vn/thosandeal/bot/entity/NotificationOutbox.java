package vn.thosandeal.bot.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.thosandeal.bot.enums.OutboxStatus;

import java.time.Instant;

/**
 * Notification outbox for reliable Telegram channel delivery.
 *
 * <p>Design rationale (MandatoryFix #9, #10):
 * The scheduler writes outbox entries inside the same DB transaction as WatchItem updates.
 * A separate worker reads PENDING entries and sends them via Telegram API.
 * This decouples DB transactions from HTTP calls — DB is never locked during network I/O.
 *
 * <p>Deduplication:
 * trigger_fingerprint is UNIQUE. A duplicate price trigger for the same item and price
 * will hit this constraint and be ignored (on conflict do nothing).
 * Fingerprint = SHA-256(watchItemId + finalPrice + triggerReason).
 *
 * <p>MandatoryFix #10.
 */
@Entity
@Table(name = "notification_outbox")
@Getter
@Setter
@NoArgsConstructor
public class NotificationOutbox {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "watch_item_id",
            foreignKey = @ForeignKey(name = "fk_notification_outbox_watch_item"))
    private WatchItem watchItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "watch_sku_id",
            foreignKey = @ForeignKey(name = "fk_notification_outbox_watch_sku"))
    private WatchSku watchSku;

    @Column(name = "telegram_chat_id", nullable = false, length = 100)
    private String telegramChatId;

    @Column(name = "message_text", nullable = false, columnDefinition = "TEXT")
    private String messageText;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OutboxStatus status = OutboxStatus.PENDING;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount = 0;

    @Column(name = "last_error", length = 2000)
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    /**
     * Fingerprint for deduplication. UNIQUE constraint in DB.
     * Format: SHA-256 hex of "watchItemId:finalPrice:triggerReason"
     */
    @Column(name = "trigger_fingerprint", nullable = false, unique = true, length = 64)
    private String triggerFingerprint;

    public static NotificationOutbox create(
            WatchItem watchItem,
            String telegramChatId,
            String messageText,
            String triggerFingerprint) {
        return createSku(watchItem, null, telegramChatId, messageText, triggerFingerprint);
    }

    public static NotificationOutbox createSku(
            WatchItem watchItem,
            WatchSku watchSku,
            String telegramChatId,
            String messageText,
            String triggerFingerprint) {
        NotificationOutbox outbox = new NotificationOutbox();
        outbox.watchItem = watchItem;
        outbox.watchSku = watchSku;
        outbox.telegramChatId = telegramChatId;
        outbox.messageText = messageText;
        outbox.status = OutboxStatus.PENDING;
        outbox.attemptCount = 0;
        outbox.createdAt = Instant.now();
        outbox.triggerFingerprint = triggerFingerprint;
        return outbox;
    }

    public static NotificationOutbox createAdminAlert(
            String telegramChatId,
            String messageText,
            String triggerFingerprint) {
        NotificationOutbox outbox = new NotificationOutbox();
        outbox.watchItem = null;
        outbox.watchSku = null;
        outbox.telegramChatId = telegramChatId;
        outbox.messageText = messageText;
        outbox.status = OutboxStatus.PENDING;
        outbox.attemptCount = 0;
        outbox.createdAt = Instant.now();
        outbox.triggerFingerprint = triggerFingerprint;
        return outbox;
    }
}
