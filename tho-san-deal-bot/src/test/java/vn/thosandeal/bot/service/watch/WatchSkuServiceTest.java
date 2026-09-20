package vn.thosandeal.bot.service.watch;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.thosandeal.bot.entity.PriceCheckLog;
import vn.thosandeal.bot.entity.WatchItem;
import vn.thosandeal.bot.entity.WatchSku;
import vn.thosandeal.bot.enums.PriceQuality;
import vn.thosandeal.bot.repository.PriceCheckLogRepository;
import vn.thosandeal.bot.repository.WatchSkuRepository;
import vn.thosandeal.bot.service.notification.DealNotificationService;
import vn.thosandeal.bot.service.notification.NotificationDecisionService;
import vn.thosandeal.bot.service.pricing.LazadaSkuPrice;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class WatchSkuServiceTest {

    private WatchSkuRepository watchSkuRepository;
    private NotificationDecisionService notificationDecisionService;
    private DealNotificationService dealNotificationService;
    private PriceCheckLogRepository priceCheckLogRepository;
    private WatchSkuService watchSkuService;

    @BeforeEach
    void setUp() {
        watchSkuRepository = mock(WatchSkuRepository.class);
        notificationDecisionService = mock(NotificationDecisionService.class);
        dealNotificationService = mock(DealNotificationService.class);
        priceCheckLogRepository = mock(PriceCheckLogRepository.class);

        watchSkuService = new WatchSkuService(
                watchSkuRepository,
                notificationDecisionService,
                dealNotificationService,
                priceCheckLogRepository
        );
    }

    @Test
    @DisplayName("Notification is evaluated with previousCheckedPrice BEFORE lastCheckedPrice is updated")
    void testNotificationEvaluatedBeforeStateUpdate() {
        WatchItem item = new WatchItem();
        item.setId(10L);
        item.setTargetPrice(new BigDecimal("1500000"));

        WatchSku existingSku = new WatchSku();
        existingSku.setId(100L);
        existingSku.setWatchItem(item);
        existingSku.setSkuId("SKU-1");
        existingSku.setLastCheckedPrice(new BigDecimal("1600000"));
        existingSku.setLastNotifiedPrice(null);

        when(watchSkuRepository.findByWatchItemIdAndSkuId(10L, "SKU-1"))
                .thenReturn(Optional.of(existingSku));
        when(watchSkuRepository.save(any(WatchSku.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BigDecimal newPrice = new BigDecimal("1490000");
        LazadaSkuPrice skuPrice = LazadaSkuPrice.exactAccount(
                "SKU-1", "Black", new BigDecimal("1600000"), newPrice, newPrice, true, 10);

        when(notificationDecisionService.shouldNotify(
                eq(new BigDecimal("1500000")),
                eq(new BigDecimal("1600000")), // Previous checked price!
                eq(null),                     // Last notified price!
                eq(newPrice)
        )).thenReturn(true);

        when(notificationDecisionService.determineTriggerReason(any(), any(), any())).thenReturn("A");
        when(dealNotificationService.queueSkuDealNotification(eq(item), eq(existingSku), eq(newPrice), eq("A")))
                .thenReturn(true);

        List<WatchSku> result = watchSkuService.processSkuPrices(item, List.of(skuPrice));

        assertThat(result).hasSize(1);
        WatchSku saved = result.get(0);

        // State updated after notification
        assertThat(saved.getLastCheckedPrice()).isEqualByComparingTo(newPrice);
        assertThat(saved.getLastNotifiedPrice()).isEqualByComparingTo(newPrice);
        assertThat(saved.getPriceQuality()).isEqualTo(PriceQuality.EXACT_ACCOUNT);

        verify(notificationDecisionService).shouldNotify(
                eq(new BigDecimal("1500000")),
                eq(new BigDecimal("1600000")),
                eq(null),
                eq(newPrice)
        );
        verify(dealNotificationService).queueSkuDealNotification(item, existingSku, newPrice, "A");
        verify(priceCheckLogRepository).save(any(PriceCheckLog.class));
    }

    @Test
    @DisplayName("Cached exact price does not evaluate notification or create duplicate alert")
    void testCachedPriceDoesNotTriggerNotification() {
        WatchItem item = new WatchItem();
        item.setId(10L);
        item.setTargetPrice(new BigDecimal("1500000"));

        WatchSku existingSku = new WatchSku();
        existingSku.setId(100L);
        existingSku.setWatchItem(item);
        existingSku.setSkuId("SKU-1");
        existingSku.setLastCheckedPrice(new BigDecimal("1490000"));
        existingSku.setLastNotifiedPrice(new BigDecimal("1490000"));

        when(watchSkuRepository.findByWatchItemIdAndSkuId(10L, "SKU-1"))
                .thenReturn(Optional.of(existingSku));
        when(watchSkuRepository.save(any(WatchSku.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // CACHED price from exactAccountCached
        LazadaSkuPrice cachedPrice = LazadaSkuPrice.exactAccountCached(
                "SKU-1", "Black", new BigDecimal("1600000"), new BigDecimal("1490000"), new BigDecimal("1490000"), true, 10);

        List<WatchSku> result = watchSkuService.processSkuPrices(item, List.of(cachedPrice));

        assertThat(result).hasSize(1);
        // shouldNotify is NEVER called for CACHED price!
        verify(notificationDecisionService, never()).shouldNotify(any(), any(), any(), any());
        verify(dealNotificationService, never()).queueSkuDealNotification(any(), any(), any(), any());
    }
}
