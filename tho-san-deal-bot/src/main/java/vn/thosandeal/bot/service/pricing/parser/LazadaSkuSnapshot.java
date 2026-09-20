package vn.thosandeal.bot.service.pricing.parser;

import java.math.BigDecimal;

public record LazadaSkuSnapshot(
        String skuId,
        String sellerSku,
        String variantName,
        BigDecimal salePrice,
        BigDecimal originalPrice,
        Integer stock,
        boolean available
) {}
