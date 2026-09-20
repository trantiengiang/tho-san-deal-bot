package vn.thosandeal.bot.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.thosandeal.bot.entity.LazadaSession;
import vn.thosandeal.bot.enums.LazadaSessionStatus;

import java.util.Optional;

@Repository
public interface LazadaSessionRepository extends JpaRepository<LazadaSession, Long> {

    Optional<LazadaSession> findFirstByStatusOrderByIdDesc(LazadaSessionStatus status);

    Optional<LazadaSession> findFirstByOrderByIdDesc();

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("UPDATE LazadaSession s SET s.status = :probingStatus, s.updatedAt = :now " +
           "WHERE s.id = :id AND s.status = :challengedStatus AND s.cooldownUntil <= :now")
    int claimProbeLock(
            @org.springframework.data.repository.query.Param("id") Long id,
            @org.springframework.data.repository.query.Param("now") java.time.Instant now,
            @org.springframework.data.repository.query.Param("challengedStatus") LazadaSessionStatus challengedStatus,
            @org.springframework.data.repository.query.Param("probingStatus") LazadaSessionStatus probingStatus);
}
