package vn.thosandeal.bot.service.command;

import vn.thosandeal.bot.dto.telegram.TelegramMessage;
import vn.thosandeal.bot.enums.CommandType;

/**
 * Strategy interface for command handling.
 * Each command has its own handler implementation (MandatoryFix #4).
 * CommandDispatcher selects the appropriate handler.
 */
public interface CommandHandler {

    /**
     * Returns the command type this handler is responsible for.
     */
    CommandType supportedCommand();

    /**
     * Handles the command and returns a reply text (HTML format).
     * If null is returned, no reply is sent.
     */
    String handle(TelegramMessage message, vn.thosandeal.bot.parser.ParsedCommand command);
}
