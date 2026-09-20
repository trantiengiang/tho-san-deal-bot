package vn.thosandeal.bot.service.command;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import vn.thosandeal.bot.dto.telegram.TelegramMessage;
import vn.thosandeal.bot.enums.CommandType;
import vn.thosandeal.bot.parser.ParsedCommand;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Dispatches parsed commands to the appropriate handler.
 *
 * <p>Design: Each CommandHandler registers itself by implementing supportedCommand().
 * Spring auto-wires all implementations — no giant switch statement (MandatoryFix #4).
 * Adding a new command requires only a new handler class.
 */
@Service
public class CommandDispatcher {

    private static final Logger log = LoggerFactory.getLogger(CommandDispatcher.class);

    private final Map<CommandType, CommandHandler> handlerMap;
    private final UnknownCommandHandler unknownHandler;

    public CommandDispatcher(List<CommandHandler> handlers, UnknownCommandHandler unknownHandler) {
        this.handlerMap = new EnumMap<>(CommandType.class);
        this.unknownHandler = unknownHandler;
        for (CommandHandler handler : handlers) {
            handlerMap.put(handler.supportedCommand(), handler);
        }
        log.debug("CommandDispatcher registered {} handlers: {}", handlerMap.size(), handlerMap.keySet());
    }

    /**
     * Finds and invokes the appropriate handler for the parsed command.
     *
     * @return the reply text, or null if no reply should be sent
     */
    public String dispatch(TelegramMessage message, ParsedCommand command) {
        CommandHandler handler = handlerMap.getOrDefault(command.commandType(), unknownHandler);
        try {
            return handler.handle(message, command);
        } catch (Exception e) {
            log.error("Command handler error: command={} handler={}",
                    command.commandType(), handler.getClass().getSimpleName(), e);
            return "⚠️ Hệ thống đang bận, vui lòng thử lại sau.";
        }
    }
}
