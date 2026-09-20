package vn.thosandeal.bot.service.pricing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import vn.thosandeal.bot.entity.LazadaSession;
import vn.thosandeal.bot.entity.WatchItem;
import vn.thosandeal.bot.entity.WatchSku;
import vn.thosandeal.bot.enums.LazadaSessionStatus;
import vn.thosandeal.bot.repository.WatchSkuRepository;
import vn.thosandeal.bot.service.notification.DealNotificationService;
import vn.thosandeal.bot.service.pricing.client.LazadaBuyNowClient;
import vn.thosandeal.bot.service.pricing.client.LazadaPdpClient;
import vn.thosandeal.bot.service.pricing.client.LazadaPreviewRateLimiter;
import vn.thosandeal.bot.service.pricing.parser.CheckoutInitDataParser;
import vn.thosandeal.bot.service.pricing.parser.CheckoutParsedResult;
import vn.thosandeal.bot.service.pricing.parser.LazadaProductSnapshot;
import vn.thosandeal.bot.service.pricing.parser.LazadaSkuSnapshot;
import vn.thosandeal.bot.service.pricing.session.LazadaSessionService;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@ConditionalOnProperty(name = "pricing.provider", havingValue = "lazada-auth")
public class AuthenticatedLazadaPriceProvider implements ProductPriceProvider {

    private static final Logger log = LoggerFactory.getLogger(AuthenticatedLazadaPriceProvider.class);

    private final LazadaSessionService sessionService;
    private final LazadaPdpClient pdpClient;
    private final LazadaBuyNowClient buyNowClient;
    private final CheckoutInitDataParser checkoutParser;
    private final LazadaPreviewRateLimiter rateLimiter;
    private final LazadaPreviewBudget previewBudget;
    private final WatchSkuRepository watchSkuRepository;
    private final DealNotificationService dealNotificationService;
    private final long challengeCooldownMinutes;
    private final long exactPriceTtlMinutes;

    public AuthenticatedLazadaPriceProvider(
            LazadaSessionService sessionService,
            LazadaPdpClient pdpClient,
            LazadaBuyNowClient buyNowClient,
            CheckoutInitDataParser checkoutParser,
            LazadaPreviewRateLimiter rateLimiter,
            LazadaPreviewBudget previewBudget,
            WatchSkuRepository watchSkuRepository,
            DealNotificationService dealNotificationService,
            @Value("${pricing.lazada.challenge-cooldown-minutes:30}") long challengeCooldownMinutes,
            @Value("${pricing.lazada.exact-price-ttl-minutes:15}") long exactPriceTtlMinutes) {
        this.sessionService = sessionService;
        this.pdpClient = pdpClient;
        this.buyNowClient = buyNowClient;
        this.checkoutParser = checkoutParser;
        this.rateLimiter = rateLimiter;
        this.previewBudget = previewBudget;
        this.watchSkuRepository = watchSkuRepository;
        this.dealNotificationService = dealNotificationService;
        this.challengeCooldownMinutes = challengeCooldownMinutes;
        this.exactPriceTtlMinutes = exactPriceTtlMinutes;
    }

    @Override
    public ProductPriceCheckResult checkProductPrices(WatchItem watchItem) {
        String url = watchItem.getOriginalUrl();
        log.info("Checking product prices for watchItemId={} url={}", watchItem.getId(), url);

        Optional<LazadaSession> latestSessionOpt = sessionService.getLatestSession();
        if (latestSessionOpt.isEmpty()) {
            log.warn("No Lazada session found in database. Checking PDP sale prices only.");
            return checkPdpOnly(url, "No Lazada session configured");
        }

        LazadaSession session = latestSessionOpt.get();
        LazadaSessionStatus status = session.getStatus();
        Instant now = Instant.now();

        boolean isProbeClaimed = false;

        if (status == LazadaSessionStatus.CHALLENGED) {
            Instant cooldownUntil = session.getCooldownUntil();
            if (cooldownUntil != null && now.isBefore(cooldownUntil)) {
                log.info("Lazada session id={} is in challenge cooldown until {}. Checking PDP sale prices only.",
                        session.getId(), cooldownUntil);
                return checkPdpOnly(url, "Session in challenge cooldown until " + cooldownUntil);
            }

            // Cooldown expired: attempt concurrency-safe atomic claim of single probe
            isProbeClaimed = sessionService.tryBeginProbe(session.getId(), now);
            if (!isProbeClaimed) {
                log.info("Probe already claimed or cooldown still active for session id={}. Checking PDP sale prices only.",
                        session.getId());
                return checkPdpOnly(url, "Probe already claimed or cooldown active");
            }
            log.info("Successfully claimed PROBING probe for session id={}", session.getId());
        } else if (status == LazadaSessionStatus.PROBING) {
            log.info("Session id={} is already PROBING. Checking PDP sale prices only.", session.getId());
            return checkPdpOnly(url, "Session probe already in progress");
        } else if (status != LazadaSessionStatus.ACTIVE) {
            log.warn("Lazada session id={} is in non-active status: {}. Checking PDP sale prices only.",
                    session.getId(), status);
            return checkPdpOnly(url, "Session status is " + status);
        }

        // Get decrypted cookie header
        Optional<String> cookieHeaderOpt = sessionService.getActiveSessionCookieHeader();
        if (cookieHeaderOpt.isEmpty()) {
            log.warn("Unable to decrypt active/probing session cookie header. Checking PDP sale prices only.");
            return checkPdpOnly(url, "Unable to decrypt session cookie");
        }
        String cookieHeader = cookieHeaderOpt.get();

        // Fetch PDP once per product
        LazadaProductSnapshot snapshot;
        try {
            snapshot = pdpClient.fetchProductSnapshot(url, cookieHeader);
        } catch (Exception e) {
            log.error("Failed to fetch PDP snapshot for watchItemId={}: {}", watchItem.getId(), e.getMessage());
            return ProductPriceCheckResult.failure("Failed to fetch PDP: " + e.getMessage());
        }

        String itemId = snapshot.itemId();
        if (itemId == null || itemId.isBlank()) {
            itemId = watchItem.getProductId();
        }

        // Load existing WatchSku rows to inspect cached exact prices and check history
        Map<String, WatchSku> existingSkus = watchItem.getId() != null
                ? watchSkuRepository.findByWatchItemId(watchItem.getId()).stream()
                        .collect(Collectors.toMap(WatchSku::getSkuId, s -> s, (a, b) -> a))
                : Collections.emptyMap();

        List<LazadaSkuSnapshot> availableSkus = snapshot.skus().stream()
                .filter(LazadaSkuSnapshot::available)
                .toList();

        // Determine which SKUs need a fresh preview check
        List<LazadaSkuSnapshot> candidatesNeedingPreview = new ArrayList<>();
        Set<String> freshCachedSkuIds = new HashSet<>();

        for (LazadaSkuSnapshot skuSnapshot : availableSkus) {
            WatchSku existing = existingSkus.get(skuSnapshot.skuId());
            boolean cacheValid = false;
            if (existing != null && existing.getLastExactPrice() != null && existing.getLastExactCheckedAt() != null) {
                long ageMinutes = Duration.between(existing.getLastExactCheckedAt(), now).toMinutes();
                if (ageMinutes < exactPriceTtlMinutes) {
                    cacheValid = true;
                    freshCachedSkuIds.add(skuSnapshot.skuId());
                }
            }
            if (!cacheValid) {
                candidatesNeedingPreview.add(skuSnapshot);
            }
        }

        // Sort candidates needing preview:
        // 1. exact cache missing (never checked) first
        // 2. oldest lastExactCheckedAt first
        // 3. tie-break skuId ascending
        candidatesNeedingPreview.sort((s1, s2) -> {
            WatchSku w1 = existingSkus.get(s1.skuId());
            WatchSku w2 = existingSkus.get(s2.skuId());
            Instant t1 = w1 != null ? w1.getLastExactCheckedAt() : null;
            Instant t2 = w2 != null ? w2.getLastExactCheckedAt() : null;

            if (t1 == null && t2 != null) return -1;
            if (t1 != null && t2 == null) return 1;
            if (t1 != null && t2 != null) {
                int cmp = t1.compareTo(t2);
                if (cmp != 0) return cmp;
            }
            return s1.skuId().compareTo(s2.skuId());
        });

        // If in probe mode, we only allow 1 probe preview!
        int previewQuota = isProbeClaimed ? 1 : candidatesNeedingPreview.size();

        // Phase 1: Iterate candidates in sorted rotation order (never-checked first, oldest first, skuId tiebreak).
        // Budget slots are consumed in this priority order — not in snapshot order.
        Map<String, LazadaSkuPrice> resolvedPrices = new HashMap<>();
        boolean sessionHalted = false;
        int previewsDone = 0;

        for (int i = 0; i < Math.min(candidatesNeedingPreview.size(), previewQuota) && !sessionHalted; i++) {
            LazadaSkuSnapshot skuSnapshot = candidatesNeedingPreview.get(i);
            String skuId = skuSnapshot.skuId();
            String variantName = skuSnapshot.variantName();
            BigDecimal salePrice = skuSnapshot.salePrice();
            int stock = skuSnapshot.stock() != null ? skuSnapshot.stock() : 0;

            if (!previewBudget.tryAcquireSlot()) {
                log.info("Cycle preview budget exhausted. SkuId={} will use cached/sale price.", skuId);
                // remaining candidates will be handled in Phase 2 fallback
                break;
            }

            // Enforce pacing before checkout preview
            rateLimiter.acquire();

            try {
                String checkoutHtml = buyNowClient.requestCheckoutPreview(itemId, skuId, cookieHeader);
                CheckoutParsedResult parsed = checkoutParser.parse(checkoutHtml, itemId, skuId);

                if (parsed.isSuccess()) {
                    sessionService.recordSuccessfulPreview();
                    resolvedPrices.put(skuId, LazadaSkuPrice.exactAccount(
                            skuId, variantName, parsed.currentPrice(), parsed.productPayable(),
                            parsed.orderTotalPay(), true, stock));
                    previewsDone++;
                } else if (parsed.status() == CheckoutParsedResult.Status.CAPTCHA_REQUIRED
                        || parsed.status() == CheckoutParsedResult.Status.WAF_BLOCKED) {
                    log.error("Challenge detected ({}) checking skuId={} item={}. Entering cooldown and halting batch!",
                            parsed.status(), skuId, itemId);
                    LazadaSessionService.ChallengeResult cr = sessionService.markChallenge(
                            parsed.errorMessage(), Duration.ofMinutes(challengeCooldownMinutes));
                    if (cr.shouldNotifyAdmin()) {
                        dealNotificationService.queueAdminChallengeNotification(
                                cr.sessionId(), cr.challengeGeneration(), cr.cooldownUntil());
                    }
                    sessionHalted = true;
                    fallbackSku(resolvedPrices, skuSnapshot, existingSkus.get(skuId));
                } else if (parsed.status() == CheckoutParsedResult.Status.SESSION_EXPIRED) {
                    log.error("Fatal session expired checking skuId={} item={}. Halting batch!", skuId, itemId);
                    sessionService.markSessionStatus(LazadaSessionStatus.EXPIRED, parsed.errorMessage());
                    sessionHalted = true;
                    fallbackSku(resolvedPrices, skuSnapshot, existingSkus.get(skuId));
                } else {
                    // SKU-scoped failure (INVALID_PREVIEW_RESPONSE, SKU_MISMATCH, etc.)
                    log.warn("Checkout preview returned non-success ({}) for skuId={}: {}",
                            parsed.status(), skuId, parsed.errorMessage());
                    resolvedPrices.put(skuId, LazadaSkuPrice.salePriceOnly(
                            skuId, variantName, salePrice, true, stock, parsed.errorMessage()));
                    previewsDone++;

                    if (parsed.errorMessage() != null && parsed.errorMessage().contains("LZD_BUY_RENDER_PC_000")) {
                        log.info("Lazada PC checkout render unsupported for item {}. Using PDP sale prices for remaining SKUs.", itemId);
                        break;
                    }
                }
            } catch (Exception e) {
                log.warn("Error checking SKU {} for item {}: {}", skuId, itemId, e.getMessage());
                resolvedPrices.put(skuId, LazadaSkuPrice.salePriceOnly(
                        skuId, variantName, salePrice, true, stock, e.getMessage()));
                previewsDone++;
            }
        }

        // Phase 2: Fill fallback for all available SKUs not resolved in Phase 1.
        for (LazadaSkuSnapshot skuSnapshot : availableSkus) {
            if (!resolvedPrices.containsKey(skuSnapshot.skuId())) {
                fallbackSku(resolvedPrices, skuSnapshot, existingSkus.get(skuSnapshot.skuId()));
            }
        }

        List<LazadaSkuPrice> finalSkuPrices = new ArrayList<>();
        for (LazadaSkuSnapshot skuSnapshot : snapshot.skus()) {
            if (!skuSnapshot.available()) {
                finalSkuPrices.add(LazadaSkuPrice.unavailable(skuSnapshot.skuId(), skuSnapshot.variantName()));
            } else {
                LazadaSkuPrice price = resolvedPrices.get(skuSnapshot.skuId());
                if (price != null) {
                    finalSkuPrices.add(price);
                } else {
                    finalSkuPrices.add(LazadaSkuPrice.salePriceOnly(
                            skuSnapshot.skuId(), skuSnapshot.variantName(), skuSnapshot.salePrice(), true, skuSnapshot.stock(), "Fallback"));
                }
            }
        }

        return ProductPriceCheckResult.success(itemId, snapshot.productName(), finalSkuPrices);
    }

    private void fallbackSku(Map<String, LazadaSkuPrice> resolvedPrices, LazadaSkuSnapshot skuSnapshot, WatchSku existing) {
        String skuId = skuSnapshot.skuId();
        String variantName = skuSnapshot.variantName();
        BigDecimal salePrice = skuSnapshot.salePrice();
        int stock = skuSnapshot.stock() != null ? skuSnapshot.stock() : 0;

        if (existing != null && existing.getLastExactPrice() != null) {
            resolvedPrices.put(skuId, LazadaSkuPrice.exactAccountCached(
                    skuId, variantName, salePrice, existing.getLastExactPrice(), null, true, stock));
        } else {
            resolvedPrices.put(skuId, LazadaSkuPrice.salePriceOnly(
                    skuId, variantName, salePrice, true, stock, "Fallback"));
        }
    }

    private ProductPriceCheckResult checkPdpOnly(String url, String reason) {
        try {
            LazadaProductSnapshot snapshot = pdpClient.fetchProductSnapshot(url, "");
            List<LazadaSkuPrice> skuPrices = new ArrayList<>();
            for (LazadaSkuSnapshot sku : snapshot.skus()) {
                if (sku.available()) {
                    skuPrices.add(LazadaSkuPrice.salePriceOnly(
                            sku.skuId(), sku.variantName(), sku.salePrice(), true, sku.stock(), reason));
                } else {
                    skuPrices.add(LazadaSkuPrice.unavailable(sku.skuId(), sku.variantName()));
                }
            }
            return ProductPriceCheckResult.success(snapshot.itemId(), snapshot.productName(), skuPrices);
        } catch (Exception e) {
            return ProductPriceCheckResult.failure("PDP fallback failed: " + e.getMessage());
        }
    }
}
