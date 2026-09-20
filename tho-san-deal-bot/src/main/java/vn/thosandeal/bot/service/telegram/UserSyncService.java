package vn.thosandeal.bot.service.telegram;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.thosandeal.bot.dto.telegram.TelegramUserDto;
import vn.thosandeal.bot.entity.TelegramUserEntity;
import vn.thosandeal.bot.repository.TelegramUserRepository;

/**
 * Syncs Telegram user data to the local DB on every received message.
 *
 * <p>Uses atomic PostgreSQL upsert to prevent duplicate user creation
 * when concurrent messages arrive from the same user.
 * MandatoryFix #5.
 */
@Service
public class UserSyncService {

    private static final Logger log = LoggerFactory.getLogger(UserSyncService.class);

    private final TelegramUserRepository userRepository;

    public UserSyncService(TelegramUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Upserts the Telegram user into the DB.
     * Returns the current DB entity after upsert.
     */
    @Transactional
    public TelegramUserEntity upsertUser(TelegramUserDto dto) {
        if (dto == null || dto.id() == null) {
            throw new IllegalArgumentException("TelegramUserDto must have a non-null id");
        }

        // Atomic upsert — safe under concurrency
        userRepository.upsertUser(
                dto.id(),
                dto.username(),
                dto.firstName(),
                dto.lastName()
        );

        // Load the entity to return it
        return userRepository.findByTelegramUserId(dto.id())
                .orElseThrow(() -> {
                    log.error("User not found after upsert — telegramUserId={}", dto.id());
                    return new IllegalStateException("User not found after upsert: " + dto.id());
                });
    }
}
