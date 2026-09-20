package vn.thosandeal.bot.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.thosandeal.bot.entity.TelegramUserEntity;

import java.util.Optional;

@Repository
public interface TelegramUserRepository extends JpaRepository<TelegramUserEntity, Long> {

    Optional<TelegramUserEntity> findByTelegramUserId(Long telegramUserId);

    /**
     * Atomic upsert using PostgreSQL ON CONFLICT DO UPDATE.
     * Prevents duplicate users when concurrent messages arrive from the same user.
     * MandatoryFix #5.
     */
    @Modifying
    @Query(value = """
            INSERT INTO telegram_user (telegram_user_id, username, first_name, last_name, created_at, updated_at)
            VALUES (:telegramUserId, :username, :firstName, :lastName, NOW(), NOW())
            ON CONFLICT (telegram_user_id) DO UPDATE
              SET username   = EXCLUDED.username,
                  first_name = EXCLUDED.first_name,
                  last_name  = EXCLUDED.last_name,
                  updated_at = NOW()
            """,
            nativeQuery = true)
    void upsertUser(
            @Param("telegramUserId") Long telegramUserId,
            @Param("username") String username,
            @Param("firstName") String firstName,
            @Param("lastName") String lastName);
}
