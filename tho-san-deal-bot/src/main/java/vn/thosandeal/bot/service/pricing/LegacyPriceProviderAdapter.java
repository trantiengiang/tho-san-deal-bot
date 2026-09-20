package vn.thosandeal.bot.service.pricing;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import vn.thosandeal.bot.entity.WatchItem;

import java.math.BigDecimal;
import java.util.Comparator;

/**
 * Adapter allowing legacy Phase 1 components that depend on PriceProvider
 * to work seamlessly with Phase 2 ProductPriceProvider.
 *
 * MandatoryFix #2: Keeps AuthenticatedLazadaPriceProvider focused solely on
 * the multi-SKU ProductPriceProvider contract.
 */
@Component
@Primary
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "pricing.provider", havingValue = "lazada-auth")
public class LegacyPriceProviderAdapter implements PriceProvider {

    private final ProductPriceProvider productPriceProvider;

    public LegacyPriceProviderAdapter(ProductPriceProvider productPriceProvider) {
        this.productPriceProvider = productPriceProvider;
    }

    @Override
    public PriceCheckResult checkPrice(WatchItem watchItem) {
        ProductPriceCheckResult result = productPriceProvider.checkProductPrices(watchItem);
        if (!result.success()) {
            return PriceCheckResult.failure(result.errorMessage());
        }

        // Return the lowest productPayable price among available SKUs
        return result.skuPrices().stream()
                .filter(LazadaSkuPrice::available)
                .filter(sku -> sku.productPayable() != null)
                .min(Comparator.comparing(LazadaSkuPrice::productPayable))
                .map(bestSku -> PriceCheckResult.success(
                        result.productId(),
                        result.productName(),
                        bestSku.variantName(),
                        bestSku.salePrice(),
                        bestSku.productPayable(),
                        "VND"
                ))
                .orElseGet(() -> PriceCheckResult.notAvailable());
    }
}
