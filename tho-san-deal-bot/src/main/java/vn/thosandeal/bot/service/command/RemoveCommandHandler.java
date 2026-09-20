package vn.thosandeal.bot.service.command;

import org.springframework.stereotype.Component;
import vn.thosandeal.bot.dto.telegram.TelegramMessage;
import vn.thosandeal.bot.enums.CommandType;
import vn.thosandeal.bot.exception.WatchItemNotFoundException;
import vn.thosandeal.bot.parser.ParsedCommand;
import vn.thosandeal.bot.service.watch.WatchService;

/**
 * Handles /remove &lt;id&gt; command.
 * MandatoryFix #23: remove only allowed for the item's owner.
 */
@Component
public class RemoveCommandHandler implements CommandHandler {

    private final WatchService watchService;

    public RemoveCommandHandler(WatchService watchService) {
        this.watchService = watchService;
    }

    @Override
    public CommandType supportedCommand() {
        return CommandType.REMOVE;
    }

    @Override
    public String handle(TelegramMessage message, ParsedCommand command) {
        if (message.from() == null) {
            return "❌ Không thể xác định người dùng.";
        }
        if (command.removeId() == null) {
            return "❌ Vui lòng cung cấp ID sản phẩm.\n\nVí dụ: <code>/remove 15</code>";
        }

        Long telegramUserId = message.from().id();
        Long watchItemId = command.removeId();

        try {
            // Service query includes telegramUserId — prevents cross-user deletion
            watchService.removeWatchItem(watchItemId, telegramUserId);
            return "✅ Đã xoá sản phẩm khỏi danh sách canh.";
        } catch (WatchItemNotFoundException e) {
            return "❌ Không tìm thấy sản phẩm hoặc bạn không có quyền xoá.";
        }
    }
}
