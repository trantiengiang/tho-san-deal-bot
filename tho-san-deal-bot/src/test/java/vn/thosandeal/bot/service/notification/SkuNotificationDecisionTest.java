package vn.thosandeal.bot.service.notification;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class SkuNotificationDecisionTest {

    private NotificationDecisionService service;
    private final BigDecimal target = new BigDecimal("1500000");

    @BeforeEach
    void setUp() {
        service = new NotificationDecisionService();
    }

    @Test
    @DisplayName("Test previousPrice notification transition ordering independently per SKU")
    void testNotificationTransitionOrdering() {
        // Step 1: Initial check at 1,600,000 (above target) -> no notify
        BigDecimal prevChecked = null;
        BigDecimal lastNotified = null;
        BigDecimal current1 = new BigDecimal("1600000");
        boolean notify1 = service.shouldNotify(target, prevChecked, lastNotified, current1);
        assertThat(notify1).isFalse();

        // State update after Step 1:
        prevChecked = current1; // 1,600,000

        // Step 2: 1,600,000 -> 1,490,000 -> NOTIFY (Rule A: first time <= target, lastNotified is null)
        BigDecimal current2 = new BigDecimal("1490000");
        boolean notify2 = service.shouldNotify(target, prevChecked, lastNotified, current2);
        assertThat(notify2).isTrue();

        // State update after Step 2 (notification sent):
        prevChecked = current2;   // 1,490,000
        lastNotified = current2;  // 1,490,000

        // Step 3: 1,490,000 -> 1,490,000 -> NO NOTIFY (no further price drop, same as lastNotified)
        BigDecimal current3 = new BigDecimal("1490000");
        boolean notify3 = service.shouldNotify(target, prevChecked, lastNotified, current3);
        assertThat(notify3).isFalse();

        // State update after Step 3:
        prevChecked = current3; // 1,490,000

        // Step 4: 1,490,000 -> 1,480,000 -> NOTIFY (Rule C: 1,480,000 < lastNotified 1,490,000)
        BigDecimal current4 = new BigDecimal("1480000");
        boolean notify4 = service.shouldNotify(target, prevChecked, lastNotified, current4);
        assertThat(notify4).isTrue();

        // State update after Step 4 (notification sent):
        prevChecked = current4;   // 1,480,000
        lastNotified = current4;  // 1,480,000

        // Step 5: 1,480,000 -> 1,510,000 -> NO NOTIFY (price increased above target)
        BigDecimal current5 = new BigDecimal("1510000");
        boolean notify5 = service.shouldNotify(target, prevChecked, lastNotified, current5);
        assertThat(notify5).isFalse();

        // State update after Step 5 (above target, lastNotified stays 1,480,000):
        prevChecked = current5; // 1,510,000

        // Step 6: 1,510,000 -> 1,495,000 -> NOTIFY (Rule B: prevChecked > target, current <= target)
        BigDecimal current6 = new BigDecimal("1495000");
        boolean notify6 = service.shouldNotify(target, prevChecked, lastNotified, current6);
        assertThat(notify6).isTrue();
    }

    @Test
    @DisplayName("Independent state evaluation across multiple SKUs on same product")
    void testIndependentMultiSkuEvaluation() {
        // SKU A: already notified at 1,490,000
        BigDecimal skuA_prevChecked = new BigDecimal("1490000");
        BigDecimal skuA_lastNotified = new BigDecimal("1490000");
        BigDecimal skuA_current = new BigDecimal("1490000");

        // SKU B: never notified, price drops to 1,450,000
        BigDecimal skuB_prevChecked = new BigDecimal("1600000");
        BigDecimal skuB_lastNotified = null;
        BigDecimal skuB_current = new BigDecimal("1450000");

        // SKU A should NOT notify (same price)
        assertThat(service.shouldNotify(target, skuA_prevChecked, skuA_lastNotified, skuA_current)).isFalse();

        // SKU B SHOULD notify (Rule A)
        assertThat(service.shouldNotify(target, skuB_prevChecked, skuB_lastNotified, skuB_current)).isTrue();
    }
}
