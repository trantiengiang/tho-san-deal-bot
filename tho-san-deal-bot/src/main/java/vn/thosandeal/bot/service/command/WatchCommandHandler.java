package vn.thosandeal.bot.service.command;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import vn.thosandeal.bot.dto.telegram.TelegramMessage;
import vn.thosandeal.bot.entity.TelegramUserEntity;
import vn.thosandeal.bot.entity.WatchItem;
import vn.thosandeal.bot.enums.CommandType;
import vn.thosandeal.bot.exception.DuplicateWatchItemException;
import vn.thosandeal.bot.exception.InvalidLazadaUrlException;
import vn.thosandeal.bot.parser.ParsedCommand;
import vn.thosandeal.bot.service.telegram.UserSyncService;
import vn.thosandeal.bot.service.watch.WatchService;
import vn.thosandeal.bot.util.MoneyFormatter;
import vn.thosandeal.bot.util.TelegramHtmlEscaper;
import vn.thosandeal.bot.validator.LazadaUrlValidator;

@Component
public class WatchCommandHandler implements CommandHandler {

    private static final Logger log = LoggerFactory.getLogger(WatchCommandHandler.class);

    private final WatchService watchService;
    private final UserSyncService userSyncService;
    private final LazadaUrlValidator lazadaUrlValidator;

    public WatchCommandHandler(WatchService watchService,
                               UserSyncService userSyncService,
                               LazadaUrlValidator lazadaUrlValidator) {
        this.watchService = watchService;
        this.userSyncService = userSyncService;
        this.lazadaUrlValidator = lazadaUrlValidator;
    }

    @Override
    public CommandType supportedCommand() {
        return CommandType.WATCH;
    }

    @Override
    public String handle(TelegramMessage message, ParsedCommand command) {
        if (message.from() == null) {
            return "❌ Không thể xác định người dùng.";
        }

        // Validate URL
        if (command.url() == null || !lazadaUrlValidator.isValid(command.url())) {
            return "❌ Link Lazada không hợp lệ.\n\n" +
                    "Vui lòng gửi link từ:\n" +
                    "• lazada.vn\n• www.lazada.vn\n• s.lazada.vn";
        }

        // Validate price
        if (command.targetPrice() == null) {
            return "❌ Giá mục tiêu không hợp lệ.\n\n" +
                    "Ví dụ giá hợp lệ: <code>1500000</code>, <code>1.5tr</code>, <code>1500k</code>";
        }

        try {
            TelegramUserEntity user = userSyncService.upsertUser(message.from());
            WatchItem item = watchService.addWatchItem(user, command.url(), command.targetPrice());

            String escapedUrl = TelegramHtmlEscaper.escape(item.getOriginalUrl());
            String formattedPrice = MoneyFormatter.format(item.getTargetPrice());

            return "✅ <b>Đã bắt đầu canh giá!</b>\n\n" +
                    "🔗 <b>Sản phẩm:</b>\n" + escapedUrl + "\n\n" +
                    "🎯 <b>Giá mục tiêu:</b>\n" + formattedPrice + "\n\n" +
                    "🔔 Tôi sẽ thông báo khi giá cuối chạm hoặc thấp hơn mức này.";

        } catch (DuplicateWatchItemException e) {
            return "⚠️ Sản phẩm này đã được bạn canh ở đúng mức giá đó rồi.";
        } catch (InvalidLazadaUrlException e) {
            return "❌ Link Lazada không hợp lệ.";
        }
    }
}
