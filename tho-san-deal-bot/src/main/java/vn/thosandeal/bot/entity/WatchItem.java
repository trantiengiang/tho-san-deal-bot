package vn.thosandeal.bot.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import vn.thosandeal.bot.enums.WatchStatus;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Represents a user's price-watch request for a Lazada product.
 *
 * <p>Duplicate prevention:
 * <ul>
 *   <li>Application layer: check before insert, return friendly message if duplicate</li>
 *   <li>Database layer: partial unique index on (user_id, normalized_url, target_price) WHERE active = true</li>
 * </ul>
 *
 * <p>@Version provides optimistic locking to prevent concurrent notification state corruption
 * (MandatoryFix #8).
 */
@Entity
@Table(name = "watch_item")
@Getter
@Setter
@NoArgsConstructor
public class WatchItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_watch_item_user"))
    private TelegramUserEntity user;

    @Column(name = "original_url", nullable = false, length = 2048)
    private String originalUrl;

    @Column(name = "normalized_url", length = 2048)
    private String normalizedUrl;

    @Column(name = "product_id", length = 255)
    private String productId;

    @Column(name = "product_name", length = 1000)
    private String productName;

    /**
     * Money stored as NUMERIC(19,2). Never use double/float.
     */
    @Column(name = "target_price", nullable = false, precision = 19, scale = 2)
    private BigDecimal targetPrice;

    @Column(name = "last_checked_price", precision = 19, scale = 2)
    private BigDecimal lastCheckedPrice;

    /**
     * The price at which the last notification was sent.
     * Used by NotificationDecisionService to prevent spam (MandatoryFix #7).
     */
    @Column(name = "last_notified_price", precision = 19, scale = 2)
    private BigDecimal lastNotifiedPrice;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private WatchStatus status = WatchStatus.ACTIVE;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "last_checked_at")
    private Instant lastCheckedAt;

    @Column(name = "last_notified_at")
    private Instant lastNotifiedAt;

    /**
     * Optimistic lock version. Prevents concurrent notification state corruption.
     * Any concurrent update will throw ObjectOptimisticLockingFailureException.
     */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public WatchItem(TelegramUserEntity user, String originalUrl, String normalizedUrl, BigDecimal targetPrice) {
        this.user = user;
        this.originalUrl = originalUrl;
        this.normalizedUrl = normalizedUrl;
        this.targetPrice = targetPrice;
        this.active = true;
        this.status = WatchStatus.ACTIVE;
    }
}
