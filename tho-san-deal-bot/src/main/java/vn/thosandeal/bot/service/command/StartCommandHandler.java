package vn.thosandeal.bot.service.command;

import org.springframework.stereotype.Component;
import vn.thosandeal.bot.dto.telegram.TelegramMessage;
import vn.thosandeal.bot.enums.CommandType;
import vn.thosandeal.bot.parser.ParsedCommand;

@Component
public class StartCommandHandler implements CommandHandler {

    @Override
    public CommandType supportedCommand() {
        return CommandType.START;
    }

    @Override
    public String handle(TelegramMessage message, ParsedCommand command) {
        String userIdStr = (message.from() != null) ? String.valueOf(message.from().id()) : "N/A";
        return """
                👋 <b>Chào mừng đến với Thợ Săn Deal!</b>

                Tôi sẽ giúp bạn canh giá sản phẩm và thông báo khi giá chạm mức mong muốn.

                🆔 <b>ID Telegram của bạn:</b> <code>""" + userIdStr + """
                </code>

                📌 <b>Cách dùng:</b>
                Gửi:
                <code>&lt;link Lazada&gt; &lt;giá muốn mua&gt;</code>

                Ví dụ:
                <code>https://s.lazada.vn/xxxxx 1500000</code>

                <b>Các lệnh:</b>
                /watch - thêm sản phẩm cần canh
                /list - xem danh sách đang canh
                /remove - xoá sản phẩm
                /help - hướng dẫn""";
    }
}
