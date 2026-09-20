package vn.thosandeal.bot.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import vn.thosandeal.bot.entity.PriceCheckLog;
import vn.thosandeal.bot.entity.WatchItem;
import vn.thosandeal.bot.repository.PriceCheckLogRepository;
import vn.thosandeal.bot.repository.WatchItemRepository;
import vn.thosandeal.bot.service.pricing.ProductPriceProvider;
import vn.thosandeal.bot.service.watch.WatchSkuService;

import java.time.Instant;
import java.util.List;

/**
 * Scheduler that periodically checks prices for all active watch items.
 *
 * <p>Design decisions:
 * <ul>
 *   <li>fixedDelay: next run starts AFTER previous completes — no concurrent runs (MandatoryFix #12)</li>
 *   <li>Each item isolated in try/catch — one failure doesn't crash the batch (MandatoryFix #13)</li>
 *   <li>Outbox pattern: notification queuing in same TX as WatchItem update (MandatoryFix #9)</li>
 *   <li>OptimisticLockException handled: skip notification this cycle, retry next (MandatoryFix #8)</li>
 * </ul>
 *
 * <p>Single-instance limitation:
 * This scheduler is NOT distributed-lock-protected.
 * Running multiple app instances will result in duplicate price checks.
 * To fix: add ShedLock dependency and annotate with @SchedulerLock.
 * Architecture is designed to support this without major refactoring.
 */
@Component
@ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class PriceWatchScheduler {

    private static final Logger log = LoggerFactory.getLogger(PriceWatchScheduler.class);

    private final WatchItemRepository watchItemRepository;
    private final PriceCheckLogRepository priceCheckLogRepository;
    private final ProductPriceProvider productPriceProvider;
    private final WatchSkuService watchSkuService;
    private final java.util.Optional<vn.thosandeal.bot.service.pricing.LazadaPreviewBudget> previewBudget;

    public PriceWatchScheduler(
            WatchItemRepository watchItemRepository,
            PriceCheckLogRepository priceCheckLogRepository,
            ProductPriceProvider productPriceProvider,
            WatchSkuService watchSkuService,
            java.util.Optional<vn.thosandeal.bot.service.pricing.LazadaPreviewBudget> previewBudget) {
        this.watchItemRepository = watchItemRepository;
        this.priceCheckLogRepository = priceCheckLogRepository;
        this.productPriceProvider = productPriceProvider;
        this.watchSkuService = watchSkuService;
        this.previewBudget = previewBudget;
    }

    /**
     * fixedDelay: waits for previous run to complete before starting next.
     * Configurable via PRICE_CHECK_INTERVAL_MS env variable.
     */
    @Scheduled(fixedDelayString = "${pricing.check-interval-ms:60000}")
    public void checkPrices() {
        List<WatchItem> activeItems = watchItemRepository.findByActiveTrue();
        if (activeItems.isEmpty()) {
            log.debug("PriceWatchScheduler: no active watch items");
            return;
        }

        previewBudget.ifPresent(vn.thosandeal.bot.service.pricing.LazadaPreviewBudget::startCycle);
        log.info("PriceWatchScheduler: checking {} active items", activeItems.size());

        for (WatchItem item : activeItems) {
            try {
                checkItem(item);
            } catch (Exception e) {
                // MandatoryFix #13: isolate failures — one bad item must not stop others
                // NOTE: item.getUser() is a lazy proxy; accessing it here (outside a Hibernate session)
                // would throw LazyInitializationException. Use a safe extractor instead.
                String userId = safeUserId(item);
                log.error("Error checking watchItemId={} userId={}: {}",
                        item.getId(), userId, e.getMessage(), e);
            }
        }

        log.info("PriceWatchScheduler: completed batch of {} items", activeItems.size());
    }

    /**
     * Checks a single item: fetches multi-SKU prices, evaluates notifications per SKU,
     * updates SKU states and WatchItem summary.
     */
    protected void checkItem(WatchItem item) {
        // 1. Check price — NO transaction held during this HTTP call
        vn.thosandeal.bot.service.pricing.ProductPriceCheckResult result = productPriceProvider.checkProductPrices(item);

        // 2. Save price check log if overall check failed
        if (!result.success()) {
            log.warn("Price check failed for watchItemId={}: {}", item.getId(), result.errorMessage());
            priceCheckLogRepository.save(PriceCheckLog.failed(item, result.errorMessage()));
            return;
        }

        // 3. Process all SKUs independently (MandatoryFix #3)
        List<vn.thosandeal.bot.entity.WatchSku> processedSkus = watchSkuService.processSkuPrices(item, result.skuPrices());

        // 4. Update WatchItem summary with lowest available price
        processedSkus.stream()
                .filter(vn.thosandeal.bot.entity.WatchSku::isAvailable)
                .filter(s -> s.getFinalPrice() != null)
                .map(vn.thosandeal.bot.entity.WatchSku::getFinalPrice)
                .min(java.math.BigDecimal::compareTo)
                .ifPresent(minPrice -> {
                    item.setLastCheckedPrice(minPrice);
                    item.setLastCheckedAt(Instant.now());
                });

        try {
            watchItemRepository.save(item);
        } catch (ObjectOptimisticLockingFailureException e) {
            log.warn("Optimistic lock conflict for watchItemId={} — skipping summary update", item.getId());
        }
    }

    /**
     * Safely extracts the Telegram user ID from a WatchItem for logging purposes.
     *
     * <p>The {@code user} association is LAZY — calling {@code item.getUser()} outside an
     * active Hibernate session triggers a {@link org.hibernate.LazyInitializationException}.
     * This helper checks whether the proxy is already initialized before accessing it,
     * and falls back to {@code "unknown"} otherwise.
     */
    private static String safeUserId(WatchItem item) {
        try {
            var user = item.getUser();
            if (user == null) return "unknown";
            // Hibernate.isInitialized() avoids touching a detached proxy
            if (!org.hibernate.Hibernate.isInitialized(user)) return "unknown";
            Long telegramId = user.getTelegramUserId();
            return telegramId != null ? telegramId.toString() : "unknown";
        } catch (Exception ignored) {
            return "unknown";
        }
    }
}
