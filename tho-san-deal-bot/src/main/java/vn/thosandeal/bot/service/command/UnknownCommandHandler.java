package vn.thosandeal.bot.service.command;

import org.springframework.stereotype.Component;
import vn.thosandeal.bot.dto.telegram.TelegramMessage;
import vn.thosandeal.bot.enums.CommandType;
import vn.thosandeal.bot.parser.ParsedCommand;

@Component
public class UnknownCommandHandler implements CommandHandler {

    @Override
    public CommandType supportedCommand() {
        return CommandType.UNKNOWN;
    }

    @Override
    public String handle(TelegramMessage message, ParsedCommand command) {
        return """
                ❓ Tôi không hiểu lệnh này.

                Dùng /help để xem hướng dẫn.

                Hoặc gửi trực tiếp:
                <code>https://s.lazada.vn/... giá</code>""";
    }
}
