package vn.thosandeal.bot.service.pricing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import vn.thosandeal.bot.config.MockPricingProperties;
import vn.thosandeal.bot.entity.WatchItem;

/**
 * Deterministic mock implementation of PriceProvider for Phase 1.
 *
 * <p>NOT random — always returns a configurable fixed price.
 * Configurable via MOCK_FINAL_PRICE environment variable.
 * Default: 1,490,000.
 *
 * <p>This makes testing and debugging predictable:
 * set MOCK_FINAL_PRICE below any target price to always trigger notifications,
 * or above to never trigger.
 *
 * <p>Phase 2: Replace this bean with LazadaPriceProvider by using @Primary
 * or removing this @Component and adding LazadaPriceProvider.
 * No other class needs to change.
 *
 * <p>MandatoryFix #15.
 */
@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "pricing.provider", havingValue = "mock", matchIfMissing = true)
public class MockPriceProvider implements PriceProvider, ProductPriceProvider {

    private static final Logger log = LoggerFactory.getLogger(MockPriceProvider.class);

    private final MockPricingProperties mockProperties;

    public MockPriceProvider(MockPricingProperties mockProperties) {
        this.mockProperties = mockProperties;
    }

    @Override
    public PriceCheckResult checkPrice(WatchItem watchItem) {
        log.debug("MockPriceProvider: checking price for watchItemId={} returning configuredPrice={}",
                watchItem.getId(), mockProperties.finalPrice());

        return PriceCheckResult.success(
                "MOCK-" + watchItem.getId(),      // productId
                resolveProductName(watchItem),     // productName
                "Màu đen / 128GB",                 // variantName
                mockProperties.finalPrice(),       // currentPrice
                mockProperties.finalPrice(),       // finalPrice
                "VND"                              // currency
        );
    }

    @Override
    public ProductPriceCheckResult checkProductPrices(WatchItem watchItem) {
        log.debug("MockPriceProvider: checking multi-SKU product prices for watchItemId={}", watchItem.getId());
        String productId = "MOCK-" + watchItem.getId();
        String productName = resolveProductName(watchItem);

        LazadaSkuPrice sku1 = LazadaSkuPrice.exactAccount(
                "MOCK-SKU-1",
                "Màu đen / 128GB",
                mockProperties.finalPrice(),
                mockProperties.finalPrice(),
                mockProperties.finalPrice(),
                true,
                10
        );

        return ProductPriceCheckResult.success(productId, productName, java.util.Collections.singletonList(sku1));
    }

    private String resolveProductName(WatchItem watchItem) {
        if (watchItem.getProductName() != null && !watchItem.getProductName().isBlank()) {
            return watchItem.getProductName();
        }
        return "Sản phẩm Lazada #" + watchItem.getId();
    }
}
