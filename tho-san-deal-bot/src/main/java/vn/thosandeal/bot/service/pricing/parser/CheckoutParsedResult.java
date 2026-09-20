package vn.thosandeal.bot.service.pricing.parser;

import vn.thosandeal.bot.enums.PriceQuality;

import java.math.BigDecimal;

public record CheckoutParsedResult(
        Status status,
        String skuId,
        String skuText,
        BigDecimal currentPrice,
        BigDecimal productPayable,
        BigDecimal orderTotalPay,
        PriceQuality priceQuality,
        String errorMessage
) {
    public enum Status {
        SUCCESS,
        SESSION_EXPIRED,
        WAF_BLOCKED,
        CAPTCHA_REQUIRED,
        INVALID_PREVIEW_RESPONSE,
        SKU_MISMATCH,
        PARSE_FAILED
    }

    public static CheckoutParsedResult success(
            String skuId, String skuText, BigDecimal currentPrice,
            BigDecimal productPayable, BigDecimal orderTotalPay) {
        return new CheckoutParsedResult(
                Status.SUCCESS, skuId, skuText, currentPrice,
                productPayable, orderTotalPay, PriceQuality.EXACT_ACCOUNT, null);
    }

    public static CheckoutParsedResult error(Status status, String message) {
        return new CheckoutParsedResult(status, null, null, null, null, null, PriceQuality.UNKNOWN, message);
    }

    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }

    public boolean isFatalSessionError() {
        return status == Status.SESSION_EXPIRED || status == Status.WAF_BLOCKED || status == Status.CAPTCHA_REQUIRED;
    }
}
