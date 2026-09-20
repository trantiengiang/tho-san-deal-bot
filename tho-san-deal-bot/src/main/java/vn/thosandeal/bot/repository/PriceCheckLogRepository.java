package vn.thosandeal.bot.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.thosandeal.bot.entity.PriceCheckLog;

import java.util.List;

@Repository
public interface PriceCheckLogRepository extends JpaRepository<PriceCheckLog, Long> {

    List<PriceCheckLog> findByWatchItem_IdOrderByCheckedAtDesc(Long watchItemId);
}
