package vn.thosandeal.bot.service.telegram;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.thosandeal.bot.dto.telegram.TelegramUpdate;
import vn.thosandeal.bot.repository.TelegramUpdateInboxRepository;

import java.time.Instant;

/**
 * Handles durable storage of Telegram updates in the inbox table.
 *
 * <p>The core idempotency guarantee:
 * {@code insertIfAbsent} uses PostgreSQL INSERT ON CONFLICT DO NOTHING.
 * If the update_id already exists (Telegram retry), the insert returns 0 rows
 * and we return false — no further processing occurs.
 *
 * <p>MandatoryFix #1, #2.
 */
@Service
public class TelegramUpdateInboxService {

    private static final Logger log = LoggerFactory.getLogger(TelegramUpdateInboxService.class);

    private final TelegramUpdateInboxRepository inboxRepository;
    private final ObjectMapper objectMapper;

    public TelegramUpdateInboxService(TelegramUpdateInboxRepository inboxRepository,
                                      ObjectMapper objectMapper) {
        this.inboxRepository = inboxRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Atomically persists a Telegram update to the inbox.
     *
     * @return true if this is a new update and should be processed,
     *         false if it is a duplicate (already seen update_id)
     */
    @Transactional
    public boolean persistIfNew(TelegramUpdate update) {
        Long updateId = update.updateId();
        if (updateId == null) {
            log.warn("Received Telegram update with null update_id — skipping");
            return false;
        }

        String payload = serializePayload(update);
        int insertedRows = inboxRepository.insertIfAbsent(updateId, payload, Instant.now());

        if (insertedRows == 0) {
            log.info("Duplicate Telegram update_id={} received — skipping processing", updateId);
            return false;
        }

        log.debug("Persisted new Telegram update_id={} to inbox", updateId);
        return true;
    }

    private String serializePayload(TelegramUpdate update) {
        try {
            return objectMapper.writeValueAsString(update);
        } catch (Exception e) {
            log.warn("Failed to serialize update payload for inbox — storing null", e);
            return null;
        }
    }
}
