package vn.thosandeal.bot.enums;

/**
 * Lifecycle status of an encrypted Lazada account session.
 */
public enum LazadaSessionStatus {
    ACTIVE,
    CHALLENGED,
    PROBING,
    EXPIRED,
    INVALID,
    NEEDS_LOGIN
}
