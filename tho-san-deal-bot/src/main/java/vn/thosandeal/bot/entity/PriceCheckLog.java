package vn.thosandeal.bot.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.thosandeal.bot.enums.PriceCheckStatus;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "price_check_log")
@Getter
@Setter
@NoArgsConstructor
public class PriceCheckLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "watch_item_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_price_check_log_watch_item"))
    private WatchItem watchItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "watch_sku_id",
            foreignKey = @ForeignKey(name = "fk_price_check_log_watch_sku"))
    private WatchSku watchSku;

    @Column(name = "sku_id", length = 100)
    private String skuId;

    @Column(name = "variant_name", length = 1000)
    private String variantName;

    @Enumerated(EnumType.STRING)
    @Column(name = "price_quality", length = 30)
    private vn.thosandeal.bot.enums.PriceQuality priceQuality;

    @Column(name = "checked_price", precision = 19, scale = 2)
    private BigDecimal checkedPrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PriceCheckStatus status;

    @Column(name = "message", length = 1000)
    private String message;

    @Column(name = "checked_at", nullable = false)
    private Instant checkedAt;

    public static PriceCheckLog success(WatchItem item, BigDecimal price) {
        PriceCheckLog log = new PriceCheckLog();
        log.watchItem = item;
        log.checkedPrice = price;
        log.status = PriceCheckStatus.SUCCESS;
        log.checkedAt = Instant.now();
        return log;
    }

    public static PriceCheckLog successSku(WatchItem item, WatchSku sku, BigDecimal price, vn.thosandeal.bot.enums.PriceQuality quality) {
        PriceCheckLog log = new PriceCheckLog();
        log.watchItem = item;
        log.watchSku = sku;
        if (sku != null) {
            log.skuId = sku.getSkuId();
            log.variantName = sku.getVariantName();
        }
        log.priceQuality = quality;
        log.checkedPrice = price;
        log.status = PriceCheckStatus.SUCCESS;
        log.checkedAt = Instant.now();
        return log;
    }

    public static PriceCheckLog failed(WatchItem item, String errorMessage) {
        PriceCheckLog log = new PriceCheckLog();
        log.watchItem = item;
        log.status = PriceCheckStatus.FAILED;
        log.message = errorMessage;
        log.checkedAt = Instant.now();
        return log;
    }

    public static PriceCheckLog failedSku(WatchItem item, WatchSku sku, String errorMessage) {
        PriceCheckLog log = new PriceCheckLog();
        log.watchItem = item;
        log.watchSku = sku;
        if (sku != null) {
            log.skuId = sku.getSkuId();
            log.variantName = sku.getVariantName();
        }
        log.status = PriceCheckStatus.FAILED;
        log.message = errorMessage;
        log.checkedAt = Instant.now();
        return log;
    }

    public static PriceCheckLog notAvailable(WatchItem item, String reason) {
        PriceCheckLog log = new PriceCheckLog();
        log.watchItem = item;
        log.status = PriceCheckStatus.NOT_AVAILABLE;
        log.message = reason;
        log.checkedAt = Instant.now();
        return log;
    }
}
