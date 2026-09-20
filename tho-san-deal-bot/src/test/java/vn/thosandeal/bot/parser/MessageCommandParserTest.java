package vn.thosandeal.bot.parser;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import vn.thosandeal.bot.enums.CommandType;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

class MessageCommandParserTest {

    private MessageCommandParser parser;

    @BeforeEach
    void setUp() {
        parser = new MessageCommandParser(new PriceParser());
    }

    // -----------------------------------------------------------------------
    // /start command
    // -----------------------------------------------------------------------

    @ParameterizedTest(name = "start: \"{0}\"")
    @ValueSource(strings = {"/start", "/start@thosandeal_bot", "/START", "/Start@SomeBot"})
    void shouldParseStartCommand(String text) {
        ParsedCommand cmd = parser.parse(text);
        assertThat(cmd.commandType()).isEqualTo(CommandType.START);
    }

    // -----------------------------------------------------------------------
    // /help command
    // -----------------------------------------------------------------------

    @ParameterizedTest(name = "help: \"{0}\"")
    @ValueSource(strings = {"/help", "/help@thosandeal_bot"})
    void shouldParseHelpCommand(String text) {
        ParsedCommand cmd = parser.parse(text);
        assertThat(cmd.commandType()).isEqualTo(CommandType.HELP);
    }

    // -----------------------------------------------------------------------
    // /list command
    // -----------------------------------------------------------------------

    @ParameterizedTest(name = "list: \"{0}\"")
    @ValueSource(strings = {"/list", "/list@thosandeal_bot"})
    void shouldParseListCommand(String text) {
        ParsedCommand cmd = parser.parse(text);
        assertThat(cmd.commandType()).isEqualTo(CommandType.LIST);
    }

    // -----------------------------------------------------------------------
    // /watch command
    // -----------------------------------------------------------------------

    @Test
    void shouldParseWatchCommandWithSlashPrefix() {
        ParsedCommand cmd = parser.parse("/watch https://s.lazada.vn/abc 1500000");
        assertThat(cmd.commandType()).isEqualTo(CommandType.WATCH);
        assertThat(cmd.url()).isEqualTo("https://s.lazada.vn/abc");
        assertThat(cmd.targetPrice()).isEqualByComparingTo(new BigDecimal("1500000"));
    }

    @Test
    void shouldParseWatchCommandWithBotUsername() {
        ParsedCommand cmd = parser.parse("/watch@thosandeal_bot https://s.lazada.vn/abc 1500000");
        assertThat(cmd.commandType()).isEqualTo(CommandType.WATCH);
        assertThat(cmd.url()).isEqualTo("https://s.lazada.vn/abc");
        assertThat(cmd.targetPrice()).isEqualByComparingTo(new BigDecimal("1500000"));
    }

    @Test
    void shouldParseRawUrlWithPrice() {
        ParsedCommand cmd = parser.parse("https://s.lazada.vn/abc 1500000");
        assertThat(cmd.commandType()).isEqualTo(CommandType.WATCH);
        assertThat(cmd.url()).isEqualTo("https://s.lazada.vn/abc");
        assertThat(cmd.targetPrice()).isEqualByComparingTo(new BigDecimal("1500000"));
    }

    @Test
    void shouldParseRawUrlWithTrFormat() {
        ParsedCommand cmd = parser.parse("https://s.lazada.vn/abc 1.5tr");
        assertThat(cmd.commandType()).isEqualTo(CommandType.WATCH);
        assertThat(cmd.targetPrice()).isEqualByComparingTo(new BigDecimal("1500000"));
    }

    @Test
    void shouldReturnWatchWithNullPriceOnInvalidPrice() {
        // Parser doesn't throw — returns WATCH with null price
        ParsedCommand cmd = parser.parse("/watch https://s.lazada.vn/abc invalidprice");
        assertThat(cmd.commandType()).isEqualTo(CommandType.WATCH);
        assertThat(cmd.url()).isEqualTo("https://s.lazada.vn/abc");
        assertThat(cmd.targetPrice()).isNull();
    }

    // -----------------------------------------------------------------------
    // /remove command
    // -----------------------------------------------------------------------

    @Test
    void shouldParseRemoveCommand() {
        ParsedCommand cmd = parser.parse("/remove 15");
        assertThat(cmd.commandType()).isEqualTo(CommandType.REMOVE);
        assertThat(cmd.removeId()).isEqualTo(15L);
    }

    @Test
    void shouldParseRemoveCommandWithBotUsername() {
        ParsedCommand cmd = parser.parse("/remove@thosandeal_bot 18");
        assertThat(cmd.commandType()).isEqualTo(CommandType.REMOVE);
        assertThat(cmd.removeId()).isEqualTo(18L);
    }

    @Test
    void shouldReturnUnknownForRemoveWithInvalidId() {
        ParsedCommand cmd = parser.parse("/remove abc");
        assertThat(cmd.commandType()).isEqualTo(CommandType.UNKNOWN);
    }

    // -----------------------------------------------------------------------
    // Unknown/invalid
    // -----------------------------------------------------------------------

    @ParameterizedTest(name = "unknown: \"{0}\"")
    @ValueSource(strings = {"hello world", "random text", "/unknown_command", ""})
    void shouldReturnUnknownForUnrecognizedInput(String text) {
        ParsedCommand cmd = parser.parse(text);
        assertThat(cmd.commandType()).isEqualTo(CommandType.UNKNOWN);
    }

    @Test
    void shouldReturnUnknownForNullInput() {
        ParsedCommand cmd = parser.parse(null);
        assertThat(cmd.commandType()).isEqualTo(CommandType.UNKNOWN);
    }
}
