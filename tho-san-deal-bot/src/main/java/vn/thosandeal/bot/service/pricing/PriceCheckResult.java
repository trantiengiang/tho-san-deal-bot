package vn.thosandeal.bot.service.pricing;

import java.math.BigDecimal;

/**
 * Result of a price check for a single WatchItem.
 *
 * <p>Phase 1: returned by MockPriceProvider.
 * Phase 2: will be returned by LazadaPriceProvider.
 *
 * <p>Design: WatchService and scheduler depend only on this interface.
 * Adding LazadaPriceProvider requires no changes to those classes.
 */
public record PriceCheckResult(
        boolean success,
        String productId,
        String productName,
        String variantName,
        BigDecimal currentPrice,
        BigDecimal finalPrice,
        String currency,
        String errorMessage
) {
    public static PriceCheckResult success(
            String productId, String productName, String variantName,
            BigDecimal currentPrice, BigDecimal finalPrice, String currency) {
        return new PriceCheckResult(true, productId, productName, variantName,
                currentPrice, finalPrice, currency, null);
    }

    public static PriceCheckResult failure(String errorMessage) {
        return new PriceCheckResult(false, null, null, null, null, null, null, errorMessage);
    }

    public static PriceCheckResult notAvailable() {
        return new PriceCheckResult(false, null, null, null, null, null, null, "Product not available");
    }
}
