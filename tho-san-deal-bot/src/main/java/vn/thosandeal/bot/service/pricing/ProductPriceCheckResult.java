package vn.thosandeal.bot.service.pricing;

import java.util.Collections;
import java.util.List;

public record ProductPriceCheckResult(
        boolean success,
        String productId,
        String productName,
        List<LazadaSkuPrice> skuPrices,
        String errorMessage
) {
    public static ProductPriceCheckResult success(String productId, String productName, List<LazadaSkuPrice> skuPrices) {
        return new ProductPriceCheckResult(true, productId, productName,
                skuPrices != null ? skuPrices : Collections.emptyList(), null);
    }

    public static ProductPriceCheckResult failure(String errorMessage) {
        return new ProductPriceCheckResult(false, null, null, Collections.emptyList(), errorMessage);
    }
}
