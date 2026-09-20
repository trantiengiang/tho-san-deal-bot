package vn.thosandeal.bot.service.command;

import org.springframework.stereotype.Component;
import vn.thosandeal.bot.dto.telegram.TelegramMessage;
import vn.thosandeal.bot.enums.CommandType;
import vn.thosandeal.bot.parser.ParsedCommand;

@Component
public class HelpCommandHandler implements CommandHandler {

    @Override
    public CommandType supportedCommand() {
        return CommandType.HELP;
    }

    @Override
    public String handle(TelegramMessage message, ParsedCommand command) {
        return """
                📖 <b>Hướng dẫn sử dụng Thợ Săn Deal</b>

                <b>1. Thêm sản phẩm cần canh:</b>
                /watch &lt;link Lazada&gt; &lt;giá mục tiêu&gt;

                Ví dụ:
                <code>/watch https://s.lazada.vn/xxxxx 1500000</code>

                Hoặc gửi trực tiếp:
                <code>https://s.lazada.vn/xxxxx 1500000</code>

                <b>Định dạng giá hỗ trợ:</b>
                • <code>1500000</code>
                • <code>1.500.000</code>
                • <code>1,500,000</code>
                • <code>1500k</code>
                • <code>1.5tr</code>
                • <code>1tr5</code>

                <b>2. Xem danh sách đang canh:</b>
                /list

                <b>3. Xoá sản phẩm:</b>
                /remove &lt;ID&gt;

                (ID lấy từ danh sách /list)

                <b>Lưu ý:</b>
                • Bot chỉ nhận link từ lazada.vn
                • Giá mục tiêu phải lớn hơn 0 và không quá 1.000.000.000đ
                • Bot sẽ thông báo vào channel khi giá chạm mục tiêu""";
    }
}
