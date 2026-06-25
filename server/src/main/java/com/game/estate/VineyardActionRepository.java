package com.game.estate;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link VineyardAction}.
 */
@Repository
public interface VineyardActionRepository extends JpaRepository<VineyardAction, Long> {

    /**
     * Returns all actions for a vineyard in a given world-clock year,
     * ordered by {@code dayOfYear} ascending so the replay loop can apply
     * them in causal order.
     */
    List<VineyardAction> findByVineyardIdAndYearOrderByDayOfYearAsc(Long vineyardId, int year);
}
