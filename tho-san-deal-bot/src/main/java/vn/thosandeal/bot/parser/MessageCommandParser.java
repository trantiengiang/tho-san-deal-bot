package vn.thosandeal.bot.parser;

import org.springframework.stereotype.Component;
import vn.thosandeal.bot.exception.InvalidPriceException;

import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses raw Telegram message text into a ParsedCommand.
 *
 * <p>Supported input forms:
 * <ul>
 *   <li>/start or /start@thosandeal_bot</li>
 *   <li>/help or /help@thosandeal_bot</li>
 *   <li>/watch https://s.lazada.vn/abc 1500000</li>
 *   <li>/watch@thosandeal_bot https://s.lazada.vn/abc 1500000</li>
 *   <li>https://s.lazada.vn/abc 1500000 (raw URL + price)</li>
 *   <li>/list or /list@thosandeal_bot</li>
 *   <li>/remove 15 or /remove@thosandeal_bot 15</li>
 * </ul>
 */
@Component
public class MessageCommandParser {

    private final PriceParser priceParser;

    // Matches Telegram commands with optional @bot_username suffix
    // e.g. /start, /watch@thosandeal_bot, /lazada_status
    private static final Pattern COMMAND_PATTERN =
            Pattern.compile("^/([a-zA-Z_]+)(?:@[a-zA-Z0-9_]+)?(?:\\s+(.*))?$", Pattern.DOTALL);

    // URL pattern for raw URL input (without /watch prefix)
    private static final Pattern URL_PATTERN =
            Pattern.compile("^(https?://[^\\s]+)\\s+(.+)$", Pattern.DOTALL);

    public MessageCommandParser(PriceParser priceParser) {
        this.priceParser = priceParser;
    }

    /**
     * Parses message text into a ParsedCommand.
     * Never throws — invalid inputs result in UNKNOWN or error embedded in ParsedCommand.
     */
    public ParsedCommand parse(String text) {
        if (text == null || text.isBlank()) {
            return ParsedCommand.unknown("");
        }

        String trimmed = text.trim();

        // Try command pattern first
        Matcher commandMatcher = COMMAND_PATTERN.matcher(trimmed);
        if (commandMatcher.matches()) {
            String command = commandMatcher.group(1).toLowerCase();
            String args = commandMatcher.group(2);
            return parseCommand(command, args, trimmed);
        }

        // Try raw URL + price pattern
        Matcher urlMatcher = URL_PATTERN.matcher(trimmed);
        if (urlMatcher.matches()) {
            String url = urlMatcher.group(1).trim();
            String priceStr = urlMatcher.group(2).trim();
            return parseWatchArgs(url, priceStr, trimmed);
        }

        return ParsedCommand.unknown(trimmed);
    }

    private ParsedCommand parseCommand(String command, String args, String rawText) {
        return switch (command) {
            case "start" -> ParsedCommand.start();
            case "help" -> ParsedCommand.help();
            case "list" -> ParsedCommand.list();
            case "watch" -> parseWatchCommand(args, rawText);
            case "remove" -> parseRemoveCommand(args, rawText);
            case "lazada_status" -> ParsedCommand.lazadaStatus();
            default -> ParsedCommand.unknown(rawText);
        };
    }

    private ParsedCommand parseWatchCommand(String args, String rawText) {
        if (args == null || args.isBlank()) {
            return ParsedCommand.unknown(rawText);
        }
        String trimmedArgs = args.trim();

        // Split on first whitespace to separate URL from price
        int spaceIdx = findFirstWhitespace(trimmedArgs);
        if (spaceIdx < 0) {
            return ParsedCommand.unknown(rawText);
        }

        String url = trimmedArgs.substring(0, spaceIdx).trim();
        String priceStr = trimmedArgs.substring(spaceIdx + 1).trim();
        return parseWatchArgs(url, priceStr, rawText);
    }

    private ParsedCommand parseWatchArgs(String url, String priceStr, String rawText) {
        try {
            BigDecimal price = priceParser.parse(priceStr);
            return ParsedCommand.watch(url, price, rawText);
        } catch (InvalidPriceException e) {
            // Return WATCH command with null price so handler can give a specific error
            return new ParsedCommand(
                    vn.thosandeal.bot.enums.CommandType.WATCH,
                    url, null, null, rawText);
        }
    }

    private ParsedCommand parseRemoveCommand(String args, String rawText) {
        if (args == null || args.isBlank()) {
            return ParsedCommand.unknown(rawText);
        }
        try {
            Long id = Long.parseLong(args.trim());
            return ParsedCommand.remove(id);
        } catch (NumberFormatException e) {
            return ParsedCommand.unknown(rawText);
        }
    }

    private int findFirstWhitespace(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (Character.isWhitespace(s.charAt(i))) return i;
        }
        return -1;
    }
}
