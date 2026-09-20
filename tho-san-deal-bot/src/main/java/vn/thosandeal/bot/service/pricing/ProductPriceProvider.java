package vn.thosandeal.bot.service.pricing;

import vn.thosandeal.bot.entity.WatchItem;

/**
 * Phase 2 contract for checking multi-SKU product prices.
 *
 * Checks all SKUs for a product and returns independent prices for each variant.
 */
public interface ProductPriceProvider {

    /**
     * Checks prices for all SKUs of a watched product.
     *
     * @param watchItem the item being tracked
     * @return ProductPriceCheckResult with a list of per-SKU prices
     */
    ProductPriceCheckResult checkProductPrices(WatchItem watchItem);
}
