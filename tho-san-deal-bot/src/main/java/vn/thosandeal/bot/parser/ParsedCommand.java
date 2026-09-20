package vn.thosandeal.bot.parser;

import vn.thosandeal.bot.enums.CommandType;

import java.math.BigDecimal;

/**
 * Immutable result of parsing a Telegram message text.
 */
public record ParsedCommand(
        CommandType commandType,
        String url,
        BigDecimal targetPrice,
        Long removeId,
        String rawText
) {

    public static ParsedCommand start() {
        return new ParsedCommand(CommandType.START, null, null, null, null);
    }

    public static ParsedCommand help() {
        return new ParsedCommand(CommandType.HELP, null, null, null, null);
    }

    public static ParsedCommand watch(String url, BigDecimal targetPrice, String rawText) {
        return new ParsedCommand(CommandType.WATCH, url, targetPrice, null, rawText);
    }

    public static ParsedCommand list() {
        return new ParsedCommand(CommandType.LIST, null, null, null, null);
    }

    public static ParsedCommand remove(Long id) {
        return new ParsedCommand(CommandType.REMOVE, null, null, id, null);
    }

    public static ParsedCommand lazadaStatus() {
        return new ParsedCommand(CommandType.LAZADA_STATUS, null, null, null, null);
    }

    public static ParsedCommand unknown(String rawText) {
        return new ParsedCommand(CommandType.UNKNOWN, null, null, null, rawText);
    }
}
