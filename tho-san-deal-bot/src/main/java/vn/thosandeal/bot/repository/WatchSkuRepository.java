package vn.thosandeal.bot.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.thosandeal.bot.entity.WatchSku;

import java.util.List;
import java.util.Optional;

@Repository
public interface WatchSkuRepository extends JpaRepository<WatchSku, Long> {

    List<WatchSku> findByWatchItemId(Long watchItemId);

    List<WatchSku> findByWatchItemIdAndAvailableTrue(Long watchItemId);

    Optional<WatchSku> findByWatchItemIdAndSkuId(Long watchItemId, String skuId);

    void deleteByWatchItemId(Long watchItemId);
}
