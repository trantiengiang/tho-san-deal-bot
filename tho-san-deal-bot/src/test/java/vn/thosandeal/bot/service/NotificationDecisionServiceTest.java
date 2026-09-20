package vn.thosandeal.bot.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.thosandeal.bot.entity.WatchItem;
import vn.thosandeal.bot.entity.TelegramUserEntity;
import vn.thosandeal.bot.service.notification.NotificationDecisionService;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests the exact notification rules A, B, C from MandatoryFix #7.
 *
 * target = 1,500,000
 *
 * Sequence documented in spec:
 *   1,600,000 → NO  (above target)
 *   1,490,000 → YES (rule A: never notified, below target)
 *   1,490,000 → NO  (same as lastNotified)
 *   1,480,000 → YES (rule C: below lastNotified=1,490,000 AND below target)
 *   1,480,000 → NO  (same as lastNotified)
 *   1,510,000 → NO  (above target)
 *   1,495,000 → YES (rule B: prevChecked=1,510,000 > target, now 1,495,000 <= target)
 */
class NotificationDecisionServiceTest {

    private NotificationDecisionService service;

    private static final BigDecimal TARGET = new BigDecimal("1500000");

    @BeforeEach
    void setUp() {
        service = new NotificationDecisionService();
    }

    private WatchItem makeItem(BigDecimal lastChecked, BigDecimal lastNotified) {
        TelegramUserEntity user = new TelegramUserEntity(1L, "u", "F", "L");
        WatchItem item = new WatchItem(user, "https://s.lazada.vn/x", "https://s.lazada.vn/x", TARGET);
        item.setLastCheckedPrice(lastChecked);
        item.setLastNotifiedPrice(lastNotified);
        return item;
    }

    // -----------------------------------------------------------------------
    // Rule A: Never notified, now at or below target
    // -----------------------------------------------------------------------

    @Test
    void ruleA_shouldNotifyWhenNeverNotifiedAndBelowTarget() {
        WatchItem item = makeItem(new BigDecimal("1600000"), null);
        assertThat(service.shouldNotify(item, new BigDecimal("1490000"))).isTrue();
    }

    @Test
    void ruleA_shouldNotifyWhenNeverNotifiedAndExactlyAtTarget() {
        WatchItem item = makeItem(new BigDecimal("1600000"), null);
        assertThat(service.shouldNotify(item, TARGET)).isTrue();
    }

    @Test
    void ruleA_shouldNotNotifyWhenAboveTargetAndNeverNotified() {
        WatchItem item = makeItem(null, null);
        assertThat(service.shouldNotify(item, new BigDecimal("1600000"))).isFalse();
    }

    // -----------------------------------------------------------------------
    // Rule B: Price was above target, now dropped to/below target
    // -----------------------------------------------------------------------

    @Test
    void ruleB_shouldNotifyWhenPrevWasAboveTargetNowBelow() {
        // prevChecked=1,510,000 > target=1,500,000; now 1,495,000 <= target
        WatchItem item = makeItem(new BigDecimal("1510000"), new BigDecimal("1480000"));
        assertThat(service.shouldNotify(item, new BigDecimal("1495000"))).isTrue();
    }

    @Test
    void ruleB_shouldNotNotifyWhenPrevWasAlreadyBelowTarget() {
        // prev was already below target, current is also below but equal to lastNotified
        WatchItem item = makeItem(new BigDecimal("1490000"), new BigDecimal("1490000"));
        assertThat(service.shouldNotify(item, new BigDecimal("1490000"))).isFalse();
    }

    // -----------------------------------------------------------------------
    // Rule C: Price dropped below last notification price (still <= target)
    // -----------------------------------------------------------------------

    @Test
    void ruleC_shouldNotifyWhenDropsBelowLastNotified() {
        // lastNotified=1,490,000; now 1,480,000 < 1,490,000
        WatchItem item = makeItem(new BigDecimal("1490000"), new BigDecimal("1490000"));
        assertThat(service.shouldNotify(item, new BigDecimal("1480000"))).isTrue();
    }

    @Test
    void ruleC_shouldNotNotifyWhenSameAsLastNotified() {
        // Identical to last notified price — no spam
        WatchItem item = makeItem(new BigDecimal("1490000"), new BigDecimal("1490000"));
        assertThat(service.shouldNotify(item, new BigDecimal("1490000"))).isFalse();
    }

    @Test
    void ruleC_shouldNotNotifyWhenAboveTargetEvenIfBelowLastNotified() {
        // lastNotified=1,600,000 (above target — unusual state, but let's test)
        // current=1,550,000 < lastNotified but STILL above target
        WatchItem item = makeItem(new BigDecimal("1600000"), new BigDecimal("1600000"));
        assertThat(service.shouldNotify(item, new BigDecimal("1550000"))).isFalse();
    }

    // -----------------------------------------------------------------------
    // Full sequence from spec
    // -----------------------------------------------------------------------

    @Test
    void shouldFollowSpecSequence() {
        WatchItem item = makeItem(null, null);

        // 1,600,000 → NO
        assertThat(service.shouldNotify(item, new BigDecimal("1600000"))).isFalse();

        // Simulate: prevChecked=1,600,000, lastNotified=null
        item.setLastCheckedPrice(new BigDecimal("1600000"));

        // 1,490,000 → YES (rule A)
        assertThat(service.shouldNotify(item, new BigDecimal("1490000"))).isTrue();

        // Simulate update after notification
        item.setLastNotifiedPrice(new BigDecimal("1490000"));
        item.setLastCheckedPrice(new BigDecimal("1490000"));

        // 1,490,000 → NO (same as lastNotified)
        assertThat(service.shouldNotify(item, new BigDecimal("1490000"))).isFalse();

        // 1,480,000 → YES (rule C: < lastNotified=1,490,000 AND <= target)
        assertThat(service.shouldNotify(item, new BigDecimal("1480000"))).isTrue();

        item.setLastNotifiedPrice(new BigDecimal("1480000"));
        item.setLastCheckedPrice(new BigDecimal("1480000"));

        // 1,480,000 → NO
        assertThat(service.shouldNotify(item, new BigDecimal("1480000"))).isFalse();

        // 1,510,000 → NO (above target)
        item.setLastCheckedPrice(new BigDecimal("1510000"));
        assertThat(service.shouldNotify(item, new BigDecimal("1510000"))).isFalse();

        // 1,495,000 → YES (rule B: prevChecked=1,510,000 > target, now 1,495,000 <= target)
        assertThat(service.shouldNotify(item, new BigDecimal("1495000"))).isTrue();
    }
}
