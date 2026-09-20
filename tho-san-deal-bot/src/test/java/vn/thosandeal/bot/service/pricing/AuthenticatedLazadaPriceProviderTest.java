package vn.thosandeal.bot.service.pricing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.thosandeal.bot.entity.LazadaSession;
import vn.thosandeal.bot.entity.WatchItem;
import vn.thosandeal.bot.entity.WatchSku;
import vn.thosandeal.bot.enums.LazadaSessionStatus;
import vn.thosandeal.bot.enums.PriceQuality;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AuthenticatedLazadaPriceProviderTest {

    private LazadaSessionService sessionService;
    private LazadaPdpClient pdpClient;
    private LazadaBuyNowClient buyNowClient;
    private CheckoutInitDataParser checkoutParser;
    private LazadaPreviewRateLimiter rateLimiter;
    private LazadaPreviewBudget previewBudget;
    private WatchSkuRepository watchSkuRepository;
    private DealNotificationService dealNotificationService;
    private AuthenticatedLazadaPriceProvider provider;

    private LazadaSession activeSession;

    @BeforeEach
    void setUp() {
        sessionService = mock(LazadaSessionService.class);
        pdpClient = mock(LazadaPdpClient.class);
        buyNowClient = mock(LazadaBuyNowClient.class);
        checkoutParser = mock(CheckoutInitDataParser.class);
        rateLimiter = mock(LazadaPreviewRateLimiter.class);
        previewBudget = mock(LazadaPreviewBudget.class);
        watchSkuRepository = mock(WatchSkuRepository.class);
        dealNotificationService = mock(DealNotificationService.class);

        activeSession = new LazadaSession();
        activeSession.setId(1L);
        activeSession.setStatus(LazadaSessionStatus.ACTIVE);
        when(sessionService.getLatestSession()).thenReturn(Optional.of(activeSession));
        when(sessionService.getActiveSessionCookieHeader()).thenReturn(Optional.of("lzd_sid=mock; lzd_uid=mock; cna=mock"));
        when(previewBudget.tryAcquireSlot()).thenReturn(true);
        when(watchSkuRepository.findByWatchItemId(any())).thenReturn(List.of());

        provider = new AuthenticatedLazadaPriceProvider(
                sessionService, pdpClient, buyNowClient, checkoutParser,
                rateLimiter, previewBudget, watchSkuRepository, dealNotificationService,
                30L, 15L);
    }

    @Test
    @DisplayName("Session expiring in the middle of multi-SKU batch halts further checkout calls and marks session EXPIRED")
    void testSessionExpiresMidBatchHaltsCheckout() {
        WatchItem item = new WatchItem();
        item.setId(100L);
        item.setOriginalUrl("https://www.lazada.vn/products/keyboard-i2498224535.html");
        item.setTargetPrice(new BigDecimal("1500000"));

        String cookieHeader = "lzd_sid=mock; lzd_uid=mock; cna=mock";

        LazadaSkuSnapshot sku1 = new LazadaSkuSnapshot("SKU-1", "seller-1", "Black", new BigDecimal("750000"), new BigDecimal("800000"), 10, true);
        LazadaSkuSnapshot sku2 = new LazadaSkuSnapshot("SKU-2", "seller-2", "White", new BigDecimal("790000"), new BigDecimal("850000"), 5, true);
        LazadaSkuSnapshot sku3 = new LazadaSkuSnapshot("SKU-3", "seller-3", "Grey", new BigDecimal("820000"), new BigDecimal("900000"), 8, true);

        LazadaProductSnapshot snapshot = new LazadaProductSnapshot(
                "2498224535", "Mechanical Keyboard", "https://www.lazada.vn/products/i2498224535.html",
                List.of(sku1, sku2, sku3));
        when(pdpClient.fetchProductSnapshot(anyString(), anyString())).thenReturn(snapshot);

        when(buyNowClient.requestCheckoutPreview("2498224535", "SKU-1", cookieHeader)).thenReturn("<html>valid checkout sku1</html>");
        when(checkoutParser.parse("<html>valid checkout sku1</html>", "2498224535", "SKU-1"))
                .thenReturn(CheckoutParsedResult.success("SKU-1", "Black", new BigDecimal("750000"), new BigDecimal("700000"), new BigDecimal("720000")));

        when(buyNowClient.requestCheckoutPreview("2498224535", "SKU-2", cookieHeader)).thenReturn("<html>login redirect</html>");
        when(checkoutParser.parse("<html>login redirect</html>", "2498224535", "SKU-2"))
                .thenReturn(CheckoutParsedResult.error(CheckoutParsedResult.Status.SESSION_EXPIRED, "Session expired"));

        ProductPriceCheckResult result = provider.checkProductPrices(item);

        assertThat(result.success()).isTrue();
        assertThat(result.skuPrices()).hasSize(3);

        verify(sessionService).markSessionStatus(eq(LazadaSessionStatus.EXPIRED), anyString());
        verify(buyNowClient, never()).requestCheckoutPreview("2498224535", "SKU-3", cookieHeader);
    }

    @Test
    @DisplayName("CAPTCHA challenge marks session CHALLENGED (not EXPIRED), enters cooldown, halts batch, and notifies admin")
    void testCaptchaChallengeMarksSessionChallengedAndHaltsBatch() {
        WatchItem item = new WatchItem();
        item.setId(100L);
        item.setOriginalUrl("https://www.lazada.vn/products/keyboard-i2498224535.html");

        String cookieHeader = "lzd_sid=mock; lzd_uid=mock; cna=mock";

        LazadaSkuSnapshot sku1 = new LazadaSkuSnapshot("SKU-1", "seller-1", "Black", new BigDecimal("750000"), new BigDecimal("800000"), 10, true);
        LazadaSkuSnapshot sku2 = new LazadaSkuSnapshot("SKU-2", "seller-2", "White", new BigDecimal("790000"), new BigDecimal("850000"), 5, true);

        LazadaProductSnapshot snapshot = new LazadaProductSnapshot(
                "2498224535", "Mechanical Keyboard", "https://www.lazada.vn/products/i2498224535.html",
                List.of(sku1, sku2));
        when(pdpClient.fetchProductSnapshot(anyString(), anyString())).thenReturn(snapshot);

        when(buyNowClient.requestCheckoutPreview("2498224535", "SKU-1", cookieHeader)).thenReturn("<html>punish captcha</html>");
        when(checkoutParser.parse("<html>punish captcha</html>", "2498224535", "SKU-1"))
                .thenReturn(CheckoutParsedResult.error(CheckoutParsedResult.Status.CAPTCHA_REQUIRED, "CAPTCHA required"));

        Instant cooldownUntil = Instant.now().plus(Duration.ofMinutes(30));
        when(sessionService.markChallenge(eq("CAPTCHA required"), any(Duration.class)))
                .thenReturn(new LazadaSessionService.ChallengeResult(true, 1L, 1L, cooldownUntil));

        ProductPriceCheckResult result = provider.checkProductPrices(item);

        assertThat(result.success()).isTrue();
        // Session marked CHALLENGED, NEVER markSessionStatus(EXPIRED)
        verify(sessionService, never()).markSessionStatus(eq(LazadaSessionStatus.EXPIRED), anyString());
        verify(sessionService).markChallenge(eq("CAPTCHA required"), eq(Duration.ofMinutes(30)));
        // Admin notification queued
        verify(dealNotificationService).queueAdminChallengeNotification(1L, 1L, cooldownUntil);
        // SKU 2 halted
        verify(buyNowClient, never()).requestCheckoutPreview("2498224535", "SKU-2", cookieHeader);
    }

    @Test
    @DisplayName("During challenge cooldown, no authenticated previews are sent, PDP sale prices used only")
    void testDuringCooldownNoAuthenticatedPreviewsAreSent() {
        WatchItem item = new WatchItem();
        item.setId(100L);
        item.setOriginalUrl("https://www.lazada.vn/products/keyboard-i2498224535.html");

        LazadaSession challengedSession = new LazadaSession();
        challengedSession.setId(1L);
        challengedSession.setStatus(LazadaSessionStatus.CHALLENGED);
        challengedSession.setCooldownUntil(Instant.now().plusSeconds(600)); // still 10 min left
        when(sessionService.getLatestSession()).thenReturn(Optional.of(challengedSession));

        LazadaSkuSnapshot sku1 = new LazadaSkuSnapshot("SKU-1", "seller-1", "Black", new BigDecimal("750000"), new BigDecimal("800000"), 10, true);
        LazadaProductSnapshot snapshot = new LazadaProductSnapshot(
                "2498224535", "Mechanical Keyboard", "https://www.lazada.vn/products/i2498224535.html",
                List.of(sku1));
        when(pdpClient.fetchProductSnapshot(eq(item.getOriginalUrl()), eq(""))).thenReturn(snapshot);

        ProductPriceCheckResult result = provider.checkProductPrices(item);

        assertThat(result.success()).isTrue();
        assertThat(result.skuPrices().get(0).priceQuality()).isEqualTo(PriceQuality.SALE_PRICE_ONLY);
        verify(buyNowClient, never()).requestCheckoutPreview(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Global cycle budget limit is enforced across previews")
    void testGlobalPreviewBudgetEnforced() {
        WatchItem item = new WatchItem();
        item.setId(100L);
        item.setOriginalUrl("https://www.lazada.vn/products/keyboard-i2498224535.html");

        String cookieHeader = "lzd_sid=mock; lzd_uid=mock; cna=mock";

        // 3 SKUs
        LazadaSkuSnapshot sku1 = new LazadaSkuSnapshot("SKU-1", "s1", "B1", new BigDecimal("100"), new BigDecimal("100"), 1, true);
        LazadaSkuSnapshot sku2 = new LazadaSkuSnapshot("SKU-2", "s2", "B2", new BigDecimal("200"), new BigDecimal("200"), 1, true);
        LazadaSkuSnapshot sku3 = new LazadaSkuSnapshot("SKU-3", "s3", "B3", new BigDecimal("300"), new BigDecimal("300"), 1, true);

        when(pdpClient.fetchProductSnapshot(anyString(), anyString()))
                .thenReturn(new LazadaProductSnapshot("item1", "Keyboard", "url", List.of(sku1, sku2, sku3)));

        // Budget only allows 1 slot
        when(previewBudget.tryAcquireSlot()).thenReturn(true, false, false);

        when(buyNowClient.requestCheckoutPreview("item1", "SKU-1", cookieHeader)).thenReturn("<html>ok</html>");
        when(checkoutParser.parse("<html>ok</html>", "item1", "SKU-1"))
                .thenReturn(CheckoutParsedResult.success("SKU-1", "B1", new BigDecimal("100"), new BigDecimal("90"), new BigDecimal("90")));

        ProductPriceCheckResult result = provider.checkProductPrices(item);

        // Only SKU-1 got deep preview
        verify(buyNowClient, times(1)).requestCheckoutPreview(anyString(), anyString(), anyString());
        // SKU-2 and SKU-3 used fallback
        assertThat(result.skuPrices().get(0).priceQuality()).isEqualTo(PriceQuality.EXACT_ACCOUNT);
        assertThat(result.skuPrices().get(1).priceQuality()).isEqualTo(PriceQuality.SALE_PRICE_ONLY);
        assertThat(result.skuPrices().get(2).priceQuality()).isEqualTo(PriceQuality.SALE_PRICE_ONLY);
    }

    @Test
    @DisplayName("Valid exact price within TTL prevents redundant checkout preview request")
    void testExactPriceTtlPreventsRedundantPreviews() {
        WatchItem item = new WatchItem();
        item.setId(100L);
        item.setOriginalUrl("https://www.lazada.vn/products/keyboard-i2498224535.html");

        LazadaSkuSnapshot sku1 = new LazadaSkuSnapshot("SKU-1", "s1", "B1", new BigDecimal("100000"), new BigDecimal("100000"), 5, true);
        when(pdpClient.fetchProductSnapshot(anyString(), anyString()))
                .thenReturn(new LazadaProductSnapshot("item1", "Keyboard", "url", List.of(sku1)));

        // Existing WatchSku with fresh exact price (checked 5 minutes ago, TTL is 15 min)
        WatchSku cachedSku = new WatchSku();
        cachedSku.setSkuId("SKU-1");
        cachedSku.setLastExactPrice(new BigDecimal("85000"));
        cachedSku.setLastExactCheckedAt(Instant.now().minus(Duration.ofMinutes(5)));
        when(watchSkuRepository.findByWatchItemId(100L)).thenReturn(List.of(cachedSku));

        ProductPriceCheckResult result = provider.checkProductPrices(item);

        // Never requested deep checkout preview!
        verify(buyNowClient, never()).requestCheckoutPreview(anyString(), anyString(), anyString());

        // Served from cache with CACHED freshness
        assertThat(result.skuPrices().get(0).priceQuality()).isEqualTo(PriceQuality.EXACT_ACCOUNT);
        assertThat(result.skuPrices().get(0).freshness()).isEqualTo(LazadaSkuPrice.PriceFreshness.CACHED);
        assertThat(result.skuPrices().get(0).productPayable()).isEqualByComparingTo(new BigDecimal("85000"));
    }

    @Test
    @DisplayName("SKU rotation prioritizes never-checked SKUs and oldest-checked SKUs, preventing starvation")
    void testFairSkuRotationPreventsStarvation() {
        WatchItem item = new WatchItem();
        item.setId(100L);
        item.setOriginalUrl("https://www.lazada.vn/products/keyboard-i2498224535.html");

        String cookieHeader = "lzd_sid=mock; lzd_uid=mock; cna=mock";

        // 14 SKUs
        List<LazadaSkuSnapshot> skus = new ArrayList<>();
        for (int i = 1; i <= 14; i++) {
            String skuId = String.format("SKU-%02d", i);
            skus.add(new LazadaSkuSnapshot(skuId, "s" + i, "V" + i, new BigDecimal("100000"), new BigDecimal("100000"), 5, true));
        }
        when(pdpClient.fetchProductSnapshot(anyString(), anyString()))
                .thenReturn(new LazadaProductSnapshot("item1", "Keyboard", "url", skus));

        // Let's say SKU-01, SKU-02, SKU-03 were checked 20 mins ago (expired TTL)
        // SKU-04 has NEVER been checked
        // SKU-05 has NEVER been checked
        WatchSku w1 = new WatchSku();
        w1.setSkuId("SKU-01");
        w1.setLastExactPrice(new BigDecimal("80000"));
        w1.setLastExactCheckedAt(Instant.now().minus(Duration.ofMinutes(25)));

        WatchSku w2 = new WatchSku();
        w2.setSkuId("SKU-02");
        w2.setLastExactPrice(new BigDecimal("80000"));
        w2.setLastExactCheckedAt(Instant.now().minus(Duration.ofMinutes(20)));

        when(watchSkuRepository.findByWatchItemId(100L)).thenReturn(List.of(w1, w2));

        // Budget allows 2 previews
        when(previewBudget.tryAcquireSlot()).thenReturn(true, true, false);

        when(buyNowClient.requestCheckoutPreview(anyString(), anyString(), anyString())).thenReturn("<html>ok</html>");
        when(checkoutParser.parse(anyString(), anyString(), anyString()))
                .thenReturn(CheckoutParsedResult.success("SKU", "V", new BigDecimal("100"), new BigDecimal("80"), new BigDecimal("80")));

        provider.checkProductPrices(item);

        // Never-checked SKUs have highest priority! SKU-03, SKU-04, ... were never checked.
        // SKU-03 and SKU-04 tie-break by skuId ascending and get previewed before expired SKU-01/SKU-02
        verify(buyNowClient).requestCheckoutPreview("item1", "SKU-03", cookieHeader);
        verify(buyNowClient).requestCheckoutPreview("item1", "SKU-04", cookieHeader);
        verify(buyNowClient, never()).requestCheckoutPreview("item1", "SKU-01", cookieHeader);
    }

    @Test
    @DisplayName("Probe recovery: after cooldown expiry, single probe checkout is sent, restoring ACTIVE on success")
    void testProbeRecoveryAfterCooldownExpiry() {
        WatchItem item = new WatchItem();
        item.setId(100L);
        item.setOriginalUrl("https://www.lazada.vn/products/keyboard-i2498224535.html");

        LazadaSession challengedSession = new LazadaSession();
        challengedSession.setId(1L);
        challengedSession.setStatus(LazadaSessionStatus.CHALLENGED);
        challengedSession.setCooldownUntil(Instant.now().minusSeconds(10)); // Cooldown expired!
        when(sessionService.getLatestSession()).thenReturn(Optional.of(challengedSession));
        when(sessionService.tryBeginProbe(eq(1L), any(Instant.class))).thenReturn(true);

        LazadaSkuSnapshot sku1 = new LazadaSkuSnapshot("SKU-1", "s1", "B1", new BigDecimal("100000"), new BigDecimal("100000"), 5, true);
        LazadaSkuSnapshot sku2 = new LazadaSkuSnapshot("SKU-2", "s2", "B2", new BigDecimal("100000"), new BigDecimal("100000"), 5, true);
        when(pdpClient.fetchProductSnapshot(anyString(), anyString()))
                .thenReturn(new LazadaProductSnapshot("item1", "Keyboard", "url", List.of(sku1, sku2)));

        String cookieHeader = "lzd_sid=mock; lzd_uid=mock; cna=mock";
        when(buyNowClient.requestCheckoutPreview("item1", "SKU-1", cookieHeader)).thenReturn("<html>ok</html>");
        when(checkoutParser.parse("<html>ok</html>", "item1", "SKU-1"))
                .thenReturn(CheckoutParsedResult.success("SKU-1", "B1", new BigDecimal("100000"), new BigDecimal("80000"), new BigDecimal("80000")));

        ProductPriceCheckResult result = provider.checkProductPrices(item);

        // Exactly 1 probe preview was sent! SKU-2 was not sent.
        verify(buyNowClient, times(1)).requestCheckoutPreview(anyString(), anyString(), anyString());
        verify(sessionService).recordSuccessfulPreview();
    }
}
