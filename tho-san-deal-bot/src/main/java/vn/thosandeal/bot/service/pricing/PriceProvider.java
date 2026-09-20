package vn.thosandeal.bot.service.pricing;

import vn.thosandeal.bot.entity.WatchItem;

/**
 * Abstraction for price checking.
 *
 * <p>Phase 1: MockPriceProvider implements this.
 * Phase 2: LazadaPriceProvider will implement this.
 *
 * <p>WatchService, scheduler, and NotificationDecisionService depend on this interface.
 * Switching implementations requires only a Spring @Primary annotation change or profile config.
 *
 * <p>Implementations must NOT hold DB transactions or network connections open between calls.
 * Each checkPrice() call is independent.
 */
public interface PriceProvider {

    /**
     * Checks the current price for a watch item.
     *
     * @param watchItem the item to check
     * @return a PriceCheckResult — never returns null
     */
    PriceCheckResult checkPrice(WatchItem watchItem);
}
