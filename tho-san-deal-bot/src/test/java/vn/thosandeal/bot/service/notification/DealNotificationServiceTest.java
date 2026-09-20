package vn.thosandeal.bot.service.notification;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.dao.DataIntegrityViolationException;
import vn.thosandeal.bot.config.TelegramProperties;
import vn.thosandeal.bot.entity.NotificationOutbox;
import vn.thosandeal.bot.entity.WatchItem;
import vn.thosandeal.bot.entity.WatchSku;
import vn.thosandeal.bot.repository.NotificationOutboxRepository;

import java.math.BigDecimal;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DealNotificationServiceTest {

    private NotificationOutboxRepository outboxRepository;
    private TelegramProperties telegramProperties;
    private DealNotificationService dealNotificationService;

    @BeforeEach
    void setUp() {
        outboxRepository = mock(NotificationOutboxRepository.class);
        telegramProperties = new TelegramProperties("test-bot-token", "test-secret", "-1001234567890", Collections.singletonList(12345L));
        dealNotificationService = new DealNotificationService(outboxRepository, telegramProperties);
    }

    @Test
    @DisplayName("Canonical BigDecimal representations produce identical SHA-256 fingerprints")
    void testCanonicalBigDecimalFingerprint() {
        Long watchItemId = 100L;
        String skuId = "SKU-999";
        String triggerReason = "A";

        BigDecimal price1 = new BigDecimal("738600");
        BigDecimal price2 = new BigDecimal("738600.00");
        BigDecimal price3 = new BigDecimal("738600.0000");

        String fp1 = dealNotificationService.computeFingerprint(watchItemId, skuId, price1, triggerReason);
        String fp2 = dealNotificationService.computeFingerprint(watchItemId, skuId, price2, triggerReason);
        String fp3 = dealNotificationService.computeFingerprint(watchItemId, skuId, price3, triggerReason);

        assertThat(fp1).isEqualTo(fp2);
        assertThat(fp2).isEqualTo(fp3);

        // Different price should produce different fingerprint
        BigDecimal differentPrice = new BigDecimal("738601");
        String fpDiffPrice = dealNotificationService.computeFingerprint(watchItemId, skuId, differentPrice, triggerReason);
        assertThat(fp1).isNotEqualTo(fpDiffPrice);

        // Different skuId should produce different fingerprint
        String fpDiffSku = dealNotificationService.computeFingerprint(watchItemId, "SKU-888", price1, triggerReason);
        assertThat(fp1).isNotEqualTo(fpDiffSku);
    }

    @Test
    @DisplayName("Duplicate outbox event under simulated concurrent insertion is handled without throwing")
    void testConcurrentDuplicateInsertHandledGracefully() {
        WatchItem item = new WatchItem();
        item.setId(1L);
        item.setTargetPrice(new BigDecimal("1500000"));
        item.setOriginalUrl("https://www.lazada.vn/products/item-i1.html");

        WatchSku sku = new WatchSku();
        sku.setId(10L);
        sku.setSkuId("SKU-1");
        sku.setVariantName("Black");

        // Simulate database unique constraint violation (DataIntegrityViolationException)
        when(outboxRepository.existsByTriggerFingerprint(any())).thenReturn(false);
        when(outboxRepository.saveAndFlush(any(NotificationOutbox.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint"));

        boolean result = dealNotificationService.queueSkuDealNotification(item, sku, new BigDecimal("1490000"), "A");

        // Should return false and NOT propagate a fatal exception
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Pre-existing outbox fingerprint skips insert cleanly")
    void testPreExistingOutboxFingerprintSkipped() {
        WatchItem item = new WatchItem();
        item.setId(1L);
        item.setTargetPrice(new BigDecimal("1500000"));
        item.setOriginalUrl("https://www.lazada.vn/products/item-i1.html");

        WatchSku sku = new WatchSku();
        sku.setId(10L);
        sku.setSkuId("SKU-1");

        // Simulate outbox entry already existing
        when(outboxRepository.existsByTriggerFingerprint(any())).thenReturn(true);

        boolean result = dealNotificationService.queueSkuDealNotification(item, sku, new BigDecimal("1490000"), "A");

        assertThat(result).isFalse();
        verify(outboxRepository, never()).saveAndFlush(any());
    }
}
