package vn.thosandeal.bot.service.pricing.parser;

import java.util.List;

public record LazadaProductSnapshot(
        String itemId,
        String productName,
        String canonicalUrl,
        List<LazadaSkuSnapshot> skus
) {}
