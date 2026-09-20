package vn.thosandeal.bot.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.thosandeal.bot.enums.PriceQuality;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "watch_sku", uniqueConstraints = {
        @UniqueConstraint(name = "uq_watch_sku_item_sku", columnNames = {"watch_item_id", "sku_id"})
})
@Getter
@Setter
@NoArgsConstructor
public class WatchSku {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "watch_item_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_watch_sku_watch_item"))
    private WatchItem watchItem;

    @Column(name = "sku_id", nullable = false, length = 100)
    private String skuId;

    @Column(name = "seller_sku", length = 255)
    private String sellerSku;

    @Column(name = "variant_name", length = 1000)
    private String variantName;

    @Column(name = "available", nullable = false)
    private boolean available = true;

    @Column(name = "stock")
    private Integer stock;

    @Column(name = "original_price", precision = 19, scale = 2)
    private BigDecimal originalPrice;

    @Column(name = "sale_price", precision = 19, scale = 2)
    private BigDecimal salePrice;

    @Column(name = "final_price", precision = 19, scale = 2)
    private BigDecimal finalPrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "price_quality", nullable = false, length = 30)
    private PriceQuality priceQuality = PriceQuality.UNKNOWN;

    @Column(name = "last_checked_price", precision = 19, scale = 2)
    private BigDecimal lastCheckedPrice;

    @Column(name = "last_notified_price", precision = 19, scale = 2)
    private BigDecimal lastNotifiedPrice;

    @Column(name = "last_checked_at")
    private Instant lastCheckedAt;

    @Column(name = "last_notified_at")
    private Instant lastNotifiedAt;

    @Column(name = "last_exact_price", precision = 19, scale = 2)
    private BigDecimal lastExactPrice;

    @Column(name = "last_exact_checked_at")
    private Instant lastExactCheckedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
