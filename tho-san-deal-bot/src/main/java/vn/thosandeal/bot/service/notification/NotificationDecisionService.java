package vn.thosandeal.bot.service.notification;

import org.springframework.stereotype.Service;
import vn.thosandeal.bot.entity.WatchItem;

import java.math.BigDecimal;

/**
 * Determines whether a notification should be sent based on current and previous prices.
 *
 * <p>Exact notification rules (MandatoryFix #7):
 * <pre>
 * Let:
 *   target       = watchItem.targetPrice
 *   current      = finalPrice from current check
 *   prevChecked  = watchItem.lastCheckedPrice (before this check)
 *   lastNotified = watchItem.lastNotifiedPrice
 *
 * Notify when ANY of:
 *   A: lastNotified IS NULL AND current <= target
 *   B: prevChecked > target AND current <= target
 *   C: lastNotified IS NOT NULL AND current < lastNotified AND current <= target
 *
 * Examples (target = 1,500,000):
 *   prev=N/A,       current=1,600,000, lastNotified=null    → NO  (above target)
 *   prev=1,600,000, current=1,490,000, lastNotified=null    → YES (rule A)
 *   prev=1,490,000, current=1,490,000, lastNotified=1490000 → NO  (same as lastNotified)
 *   prev=1,490,000, current=1,480,000, lastNotified=1490000 → YES (rule C: 1,480,000 < 1,490,000)
 *   prev=1,480,000, current=1,510,000, lastNotified=1480000 → NO  (above target)
 *   prev=1,510,000, current=1,495,000, lastNotified=1480000 → YES (rule B: prev > target, current <= target)
 * </pre>
 */
@Service
public class NotificationDecisionService {

    /**
     * Determines if a price event should trigger a notification.
     *
     * @param item         the watch item (with current state before update)
     * @param finalPrice   the newly checked price
     * @return true if notification should be sent
     */
    public boolean shouldNotify(WatchItem item, BigDecimal finalPrice) {
        return shouldNotify(item.getTargetPrice(), item.getLastCheckedPrice(), item.getLastNotifiedPrice(), finalPrice);
    }

    /**
     * Overloaded method operating on explicit values, enabling independent evaluation for WatchSku.
     */
    public boolean shouldNotify(BigDecimal target, BigDecimal prevChecked, BigDecimal lastNotified, BigDecimal currentPrice) {
        if (target == null || currentPrice == null) {
            return false;
        }

        // Price must be at or below target for any notification
        if (currentPrice.compareTo(target) > 0) {
            return false;
        }

        // Rule A: Never notified before AND now at or below target
        if (lastNotified == null) {
            return true;
        }

        // Rule B: Price was above target previously, now dropped to or below target
        if (prevChecked != null && prevChecked.compareTo(target) > 0) {
            return true;
        }

        // Rule C: Price has dropped lower than the last notification price (still below target)
        return currentPrice.compareTo(lastNotified) < 0;
    }

    /**
     * Determines trigger reason (A, B, or C) for notification fingerprint.
     */
    public String determineTriggerReason(BigDecimal target, BigDecimal prevChecked, BigDecimal lastNotified) {
        if (lastNotified == null) {
            return "A";
        }
        if (prevChecked != null && prevChecked.compareTo(target) > 0) {
            return "B";
        }
        return "C";
    }
}
