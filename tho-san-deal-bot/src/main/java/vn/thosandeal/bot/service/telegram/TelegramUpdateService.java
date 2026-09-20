package vn.thosandeal.bot.service.telegram;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import vn.thosandeal.bot.client.TelegramClient;
import vn.thosandeal.bot.dto.request.SendMessageRequest;
import vn.thosandeal.bot.dto.telegram.TelegramMessage;
import vn.thosandeal.bot.dto.telegram.TelegramUpdate;
import vn.thosandeal.bot.event.TelegramUpdateEvent;
import vn.thosandeal.bot.parser.MessageCommandParser;
import vn.thosandeal.bot.parser.ParsedCommand;
import vn.thosandeal.bot.service.command.CommandDispatcher;

/**
 * Processes Telegram updates asynchronously after they have been durably persisted to the inbox.
 *
 * <p>Flow:
 * 1. TelegramUpdateEvent is published by the controller
 * 2. This listener is invoked on a background thread (@Async)
 * 3. Message text is parsed into a ParsedCommand
 * 4. CommandDispatcher routes to the appropriate handler
 * 5. Reply is sent back via TelegramClient
 *
 * <p>If this method throws, the AsyncUncaughtExceptionHandler in AsyncConfig logs it.
 * The inbox record is NOT updated here — a future recovery job could pick it up.
 */
@Service
public class TelegramUpdateService {

    private static final Logger log = LoggerFactory.getLogger(TelegramUpdateService.class);
    private static final int MAX_MESSAGE_TEXT_LENGTH = 4096;

    private final MessageCommandParser commandParser;
    private final CommandDispatcher commandDispatcher;
    private final TelegramClient telegramClient;
    private final vn.thosandeal.bot.config.TelegramProperties telegramProperties;

    public TelegramUpdateService(MessageCommandParser commandParser,
                                 CommandDispatcher commandDispatcher,
                                 TelegramClient telegramClient,
                                 vn.thosandeal.bot.config.TelegramProperties telegramProperties) {
        this.commandParser = commandParser;
        this.commandDispatcher = commandDispatcher;
        this.telegramClient = telegramClient;
        this.telegramProperties = telegramProperties;
    }

    /**
     * Handles a Telegram update event asynchronously.
     */
    @Async
    @EventListener
    public void handleUpdateEvent(TelegramUpdateEvent event) {
        TelegramUpdate update = event.update();
        TelegramMessage message = update.message();

        if (message == null) {
            log.debug("Update {} has no message — skipping", update.updateId());
            return;
        }

        // Whitelist check: if allowedUserIds is configured, restrict bot access
        if (telegramProperties.allowedUserIds() != null && !telegramProperties.allowedUserIds().isEmpty()) {
            Long senderId = message.from() != null ? message.from().id() : null;
            if (senderId == null || !telegramProperties.allowedUserIds().contains(senderId)) {
                log.warn("Unauthorized access attempt from user_id={} in update_id={}", senderId, update.updateId());
                telegramClient.sendMessage(SendMessageRequest.html(
                        String.valueOf(message.chat().id()),
                        """
                        ⛔ <b>Truy Cập Bị Giới Hạn!</b>

                        Hiện tại bot đang hoạt động ở chế độ quản lý riêng.

                        👉 Để được hỗ trợ thêm sản phẩm cần theo dõi và nhận thông báo giảm giá nhanh nhất, mời bạn tham gia cộng đồng của <b>Thợ Săn Deal</b>:

                        💬 <b>Nhóm thảo luận:</b> <a href="https://t.me/thosandeal_chat">@thosandeal_chat</a>
                        📢 <b>Kênh nhận deal hời:</b> <a href="https://t.me/thosandeal_channel">@thosandeal_channel</a>

                        <i>Cùng vào nhóm săn deal xịn giá tốt mỗi ngày nhé! 🎯🔥</i>"""
                ));
                return;
            }
        }

        String text = message.text();
        if (text == null || text.isBlank()) {
            log.debug("Update {} has empty text — skipping", update.updateId());
            return;
        }

        // Guard against extremely long inputs
        if (text.length() > MAX_MESSAGE_TEXT_LENGTH) {
            log.warn("Message text too long ({} chars) in update {} — truncating",
                    text.length(), update.updateId());
            text = text.substring(0, MAX_MESSAGE_TEXT_LENGTH);
        }

        String chatId = String.valueOf(message.chat().id());
        log.debug("Processing update_id={} chatId={}", update.updateId(), chatId);

        try {
            ParsedCommand command = commandParser.parse(text);
            String reply = commandDispatcher.dispatch(message, command);

            if (reply != null && !reply.isBlank()) {
                telegramClient.sendMessage(SendMessageRequest.html(chatId, reply));
            }
        } catch (Exception e) {
            log.error("Error processing update_id={}: {}", update.updateId(), e.getMessage(), e);
            sendFallbackReply(chatId);
        }
    }

    private void sendFallbackReply(String chatId) {
        try {
            telegramClient.sendMessage(chatId, "⚠️ Hệ thống đang bận, vui lòng thử lại sau.");
        } catch (Exception ex) {
            log.error("Failed to send fallback reply to chatId={}", chatId, ex);
        }
    }
}
