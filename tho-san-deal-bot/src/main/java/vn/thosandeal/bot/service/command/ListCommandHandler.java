package vn.thosandeal.bot.service.command;

import org.springframework.stereotype.Component;
import vn.thosandeal.bot.dto.response.WatchItemResponse;
import vn.thosandeal.bot.dto.telegram.TelegramMessage;
import vn.thosandeal.bot.enums.CommandType;
import vn.thosandeal.bot.parser.ParsedCommand;
import vn.thosandeal.bot.service.watch.WatchService;
import vn.thosandeal.bot.util.TelegramHtmlEscaper;

import java.util.List;

/**
 * Handles /list command.
 *
 * <p>Message chunking (MandatoryFix #22):
 * Telegram messages are limited to 4096 characters.
 * If the list is very long, only the first MAX_ITEMS_PER_MESSAGE are shown.
 * This is a safe approach for Phase 1.
 */
@Component
public class ListCommandHandler implements CommandHandler {

    private static final int MAX_ITEMS_PER_MESSAGE = 20;
    private static final int MAX_PRODUCT_NAME_DISPLAY = 50;

    private final WatchService watchService;

    public ListCommandHandler(WatchService watchService) {
        this.watchService = watchService;
    }

    @Override
    public CommandType supportedCommand() {
        return CommandType.LIST;
    }

    @Override
    public String handle(TelegramMessage message, ParsedCommand command) {
        if (message.from() == null) {
            return "❌ Không thể xác định người dùng.";
        }

        Long telegramUserId = message.from().id();
        List<WatchItemResponse> items = watchService.listByTelegramUserId(telegramUserId);

        if (items.isEmpty()) {
            return "Bạn chưa canh sản phẩm nào.\n\n" +
                    "Dùng /watch &lt;link&gt; &lt;giá&gt; để thêm sản phẩm.";
        }

        List<WatchItemResponse> displayItems = items.size() > MAX_ITEMS_PER_MESSAGE
                ? items.subList(0, MAX_ITEMS_PER_MESSAGE)
                : items;

        StringBuilder sb = new StringBuilder();
        sb.append("📋 <b>Danh sách đang canh:</b>\n\n");

        for (int i = 0; i < displayItems.size(); i++) {
            WatchItemResponse item = displayItems.get(i);
            sb.append(i + 1).append(". ");

            String name = item.productName() != null
                    ? TelegramHtmlEscaper.escapeTruncated(item.productName(), MAX_PRODUCT_NAME_DISPLAY)
                    : TelegramHtmlEscaper.escapeTruncated(item.originalUrl(), MAX_PRODUCT_NAME_DISPLAY);
            sb.append(name).append("\n");
            sb.append("🎯 ").append(item.targetPriceFormatted()).append("\n");
            sb.append("📌 Đang theo dõi\n");
            sb.append("ID: ").append(item.id()).append("\n\n");
        }

        if (items.size() > MAX_ITEMS_PER_MESSAGE) {
            sb.append("<i>Chỉ hiển thị ").append(MAX_ITEMS_PER_MESSAGE)
              .append(" sản phẩm đầu tiên trong tổng số ").append(items.size()).append(".</i>");
        }

        return sb.toString().trim();
    }
}
