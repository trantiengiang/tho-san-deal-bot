package vn.thosandeal.bot.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.thosandeal.bot.entity.TelegramUpdateInbox;
import vn.thosandeal.bot.enums.InboxStatus;

import java.time.Instant;

@Repository
public interface TelegramUpdateInboxRepository extends JpaRepository<TelegramUpdateInbox, Long> {

    /**
     * Atomic idempotent insert using PostgreSQL ON CONFLICT DO NOTHING.
     * Returns the number of rows inserted (1 = new, 0 = duplicate).
     * This is the core of the inbox deduplication strategy (MandatoryFix #1).
     */
    @Modifying
    @Query(value = """
            INSERT INTO telegram_update_inbox
              (update_id, payload, status, received_at, retry_count)
            VALUES
              (:updateId, CAST(:payload AS TEXT), 'RECEIVED', :receivedAt, 0)
            ON CONFLICT (update_id) DO NOTHING
            """,
            nativeQuery = true)
    int insertIfAbsent(
            @Param("updateId") Long updateId,
            @Param("payload") String payload,
            @Param("receivedAt") Instant receivedAt);

    @Modifying
    @Query("""
            UPDATE TelegramUpdateInbox i
            SET i.status = :status,
                i.processedAt = :processedAt,
                i.errorMessage = :errorMessage
            WHERE i.updateId = :updateId
            """)
    void updateStatus(
            @Param("updateId") Long updateId,
            @Param("status") InboxStatus status,
            @Param("processedAt") Instant processedAt,
            @Param("errorMessage") String errorMessage);
}
