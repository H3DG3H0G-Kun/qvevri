package com.game.market;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link TradeRecord}.
 */
@Repository
public interface TradeRecordRepository extends JpaRepository<TradeRecord, Long> {
    // Standard CRUD; query methods added as needed by MQ lane tests.
}
