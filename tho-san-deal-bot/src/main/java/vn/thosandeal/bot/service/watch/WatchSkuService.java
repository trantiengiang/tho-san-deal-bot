package vn.thosandeal.bot.service.watch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.thosandeal.bot.entity.PriceCheckLog;
import vn.thosandeal.bot.entity.WatchItem;
import vn.thosandeal.bot.entity.WatchSku;
import vn.thosandeal.bot.repository.PriceCheckLogRepository;
import vn.thosandeal.bot.repository.WatchSkuRepository;
import vn.thosandeal.bot.service.notification.DealNotificationService;
import vn.thosandeal.bot.service.notification.NotificationDecisionService;
import vn.thosandeal.bot.service.pricing.LazadaSkuPrice;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Service managing per-SKU price checking, synchronization, and notification state ordering.
 *
 * <p>MandatoryFix #3: SKU NOTIFICATION STATE ORDER
 * 1. load WatchSku
 * 2. previousCheckedPrice = existing lastCheckedPrice, lastNotifiedPrice = existing lastNotifiedPrice
 * 3. newPrice = provider result
 * 4. evaluate shouldNotify using (previousCheckedPrice, newPrice, lastNotifiedPrice, targetPrice)
 * 5. persist lastCheckedPrice = newPrice, lastCheckedAt = now
 * 6. if notification created: update notification state consistently.
 */
@Service
public class WatchSkuService {

    private static final Logger log = LoggerFactory.getLogger(WatchSkuService.class);

    private final WatchSkuRepository watchSkuRepository;
    private final NotificationDecisionService notificationDecisionService;
    private final DealNotificationService dealNotificationService;
    private final PriceCheckLogRepository priceCheckLogRepository;

    public WatchSkuService(
            WatchSkuRepository watchSkuRepository,
            NotificationDecisionService notificationDecisionService,
            DealNotificationService dealNotificationService,
            PriceCheckLogRepository priceCheckLogRepository) {
        this.watchSkuRepository = watchSkuRepository;
        this.notificationDecisionService = notificationDecisionService;
        this.dealNotificationService = dealNotificationService;
        this.priceCheckLogRepository = priceCheckLogRepository;
    }

    /**
     * Synchronizes and evaluates price alerts for all SKU variants of a WatchItem.
     */
    @Transactional
    public List<WatchSku> processSkuPrices(WatchItem watchItem, List<LazadaSkuPrice> skuPrices) {
        List<WatchSku> processed = new ArrayList<>();

        for (LazadaSkuPrice skuPrice : skuPrices) {
            String skuId = skuPrice.skuId();
            if (skuId == null || skuId.isBlank()) {
                continue;
            }

            WatchSku sku = watchSkuRepository.findByWatchItemIdAndSkuId(watchItem.getId(), skuId)
                    .orElseGet(() -> {
                        WatchSku newSku = new WatchSku();
                        newSku.setWatchItem(watchItem);
                        newSku.setSkuId(skuId);
                        return newSku;
                    });

            if (skuPrice.variantName() != null && !skuPrice.variantName().isBlank()) {
                sku.setVariantName(skuPrice.variantName());
            }

            if (!skuPrice.available()) {
                sku.setAvailable(false);
                sku.setStock(skuPrice.stock());
                sku.setLastCheckedAt(Instant.now());
                watchSkuRepository.save(sku);
                priceCheckLogRepository.save(PriceCheckLog.failedSku(watchItem, sku, "SKU unavailable"));
                processed.add(sku);
                continue;
            }

            BigDecimal previousCheckedPrice = sku.getLastCheckedPrice();
            BigDecimal lastNotifiedPrice = sku.getLastNotifiedPrice();
            BigDecimal newPrice = skuPrice.productPayable() != null ? skuPrice.productPayable() : skuPrice.salePrice();

            if (newPrice != null) {
                boolean isLive = skuPrice.freshness() == null || skuPrice.freshness() == LazadaSkuPrice.PriceFreshness.LIVE;

                if (isLive) {
                    // Only LIVE prices can evaluate notification decision and update lastCheckedPrice/lastExactCheckedAt
                    boolean shouldNotify = notificationDecisionService.shouldNotify(
                            watchItem.getTargetPrice(), previousCheckedPrice, lastNotifiedPrice, newPrice);

                    if (shouldNotify) {
                        String triggerReason = notificationDecisionService.determineTriggerReason(
                                watchItem.getTargetPrice(), previousCheckedPrice, lastNotifiedPrice);

                        boolean queued = dealNotificationService.queueSkuDealNotification(
                                watchItem, sku, newPrice, triggerReason);

                        if (queued) {
                            sku.setLastNotifiedPrice(newPrice);
                            sku.setLastNotifiedAt(Instant.now());
                            log.info("Queued SKU deal notification for watchItemId={} skuId={} price={} target={}",
                                    watchItem.getId(), skuId, newPrice, watchItem.getTargetPrice());
                        }
                    }

                    sku.setLastCheckedPrice(newPrice);
                    sku.setLastCheckedAt(Instant.now());

                    if (skuPrice.priceQuality() == vn.thosandeal.bot.enums.PriceQuality.EXACT_ACCOUNT) {
                        sku.setLastExactPrice(newPrice);
                        sku.setLastExactCheckedAt(Instant.now());
                    } else if (skuPrice.errorMessage() != null && !skuPrice.errorMessage().equals("Fallback") && !skuPrice.errorMessage().equals("Budget exhausted")) {
                        // A live preview was actively attempted for this SKU, but returned non-exact (e.g. unselectable/out of stock).
                        // Record checked timestamp to avoid starving the rotation queue.
                        sku.setLastExactCheckedAt(Instant.now());
                    }
                }

                // Persist current price state (both LIVE and CACHED maintain finalPrice and availability)
                sku.setFinalPrice(newPrice);
                sku.setSalePrice(skuPrice.salePrice());
                sku.setPriceQuality(skuPrice.priceQuality());
                sku.setAvailable(true);
                sku.setStock(skuPrice.stock());

                WatchSku saved = watchSkuRepository.save(sku);
                if (isLive) {
                    priceCheckLogRepository.save(PriceCheckLog.successSku(watchItem, saved, newPrice, skuPrice.priceQuality()));
                }
                processed.add(saved);
            } else {
                sku.setLastCheckedAt(Instant.now());
                WatchSku saved = watchSkuRepository.save(sku);
                priceCheckLogRepository.save(PriceCheckLog.failedSku(watchItem, saved, "Price unavailable"));
                processed.add(saved);
            }
        }

        return processed;
    }

    @Transactional(readOnly = true)
    public List<WatchSku> findSkusByWatchItemId(Long watchItemId) {
        return watchSkuRepository.findByWatchItemId(watchItemId);
    }
}
