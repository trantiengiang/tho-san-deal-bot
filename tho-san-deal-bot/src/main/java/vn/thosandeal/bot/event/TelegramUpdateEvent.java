package vn.thosandeal.bot.event;

import vn.thosandeal.bot.dto.telegram.TelegramUpdate;

/**
 * Spring application event published after a Telegram update has been durably persisted
 * to the inbox. Consumed asynchronously by TelegramUpdateService.
 *
 * <p>Design: Controller publishes this event AFTER the inbox record is committed.
 * If the async processing fails, the inbox record can be retried by an admin/recovery job.
 */
public record TelegramUpdateEvent(TelegramUpdate update) {
}
