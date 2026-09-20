package vn.thosandeal.bot.enums;

/**
 * Quality level of a checked price.
 *
 * EXACT_ACCOUNT: Checkout preview belongs to the requested SKU, discountPrice is successfully
 *                parsed, session is authenticated, and response is not login/error/WAF.
 * SALE_PRICE_ONLY: PDP sale price exists, but checkout preview was unavailable/skipped.
 * UNKNOWN: Cannot establish price.
 */
public enum PriceQuality {
    EXACT_ACCOUNT,
    SALE_PRICE_ONLY,
    UNKNOWN
}
