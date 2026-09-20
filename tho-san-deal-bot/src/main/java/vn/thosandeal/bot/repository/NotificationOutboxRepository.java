package vn.thosandeal.bot.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.thosandeal.bot.entity.NotificationOutbox;
import vn.thosandeal.bot.enums.OutboxStatus;

import java.time.Instant;
import java.util.List;

@Repository
public interface NotificationOutboxRepository extends JpaRepository<NotificationOutbox, Long> {

    /**
     * Load all PENDING outbox entries for the notification worker to process.
     * Ordered by creation time for FIFO delivery.
     */
    List<NotificationOutbox> findByStatusOrderByCreatedAtAsc(OutboxStatus status);

    boolean existsByTriggerFingerprint(String triggerFingerprint);

    /**
     * Atomic status transition for the outbox worker.
     * Updates to SENT with sent_at timestamp.
     */
    @org.springframework.transaction.annotation.Transactional
    @Modifying
    @Query("""
            UPDATE NotificationOutbox o
            SET o.status = :status,
                o.sentAt = :sentAt,
                o.attemptCount = o.attemptCount + 1,
                o.lastError = :lastError
            WHERE o.id = :id
            """)
    int updateDeliveryResult(
            @Param("id") Long id,
            @Param("status") OutboxStatus status,
            @Param("sentAt") Instant sentAt,
            @Param("lastError") String lastError);
}
