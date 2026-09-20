package vn.thosandeal.bot.service.pricing;

import vn.thosandeal.bot.enums.PriceQuality;

import java.math.BigDecimal;

public record LazadaSkuPrice(
        String skuId,
        String variantName,
        BigDecimal salePrice,
        BigDecimal productPayable,
        BigDecimal orderTotalPay,
        PriceQuality priceQuality,
        boolean available,
        Integer stock,
        String errorMessage,
        PriceFreshness freshness
) {
    public enum PriceFreshness {
        LIVE,
        CACHED
    }

    public static LazadaSkuPrice exactAccount(
            String skuId, String variantName, BigDecimal salePrice,
            BigDecimal productPayable, BigDecimal orderTotalPay, boolean available, Integer stock) {
        return new LazadaSkuPrice(skuId, variantName, salePrice, productPayable, orderTotalPay,
                PriceQuality.EXACT_ACCOUNT, available, stock, null, PriceFreshness.LIVE);
    }

    public static LazadaSkuPrice exactAccountCached(
            String skuId, String variantName, BigDecimal salePrice,
            BigDecimal productPayable, BigDecimal orderTotalPay, boolean available, Integer stock) {
        return new LazadaSkuPrice(skuId, variantName, salePrice, productPayable, orderTotalPay,
                PriceQuality.EXACT_ACCOUNT, available, stock, null, PriceFreshness.CACHED);
    }

    public static LazadaSkuPrice salePriceOnly(
            String skuId, String variantName, BigDecimal salePrice, boolean available, Integer stock, String reason) {
        return new LazadaSkuPrice(skuId, variantName, salePrice, salePrice, null,
                PriceQuality.SALE_PRICE_ONLY, available, stock, reason, PriceFreshness.LIVE);
    }

    public static LazadaSkuPrice unavailable(String skuId, String variantName) {
        return new LazadaSkuPrice(skuId, variantName, null, null, null,
                PriceQuality.UNKNOWN, false, 0, "Out of stock / unavailable", PriceFreshness.LIVE);
    }

    public static LazadaSkuPrice error(String skuId, String variantName, String errorMessage) {
        return new LazadaSkuPrice(skuId, variantName, null, null, null,
                PriceQuality.UNKNOWN, false, null, errorMessage, PriceFreshness.LIVE);
    }
}
