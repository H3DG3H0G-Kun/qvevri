package com.game.ranking;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link RankingSnapshot}.
 */
@Repository
public interface RankingSnapshotRepository extends JpaRepository<RankingSnapshot, Long> {

    /**
     * Returns all snapshot rows for the given board, ordered by rank position ascending.
     * The "most recent" snapshot set is all rows with the latest simDay on that board;
     * callers wishing to filter by day may post-filter, but the spec calls for returning
     * all persisted rows for history/trends.
     *
     * @param board one of "WEALTH", "VINTNER", "GUILD"
     * @return rows ordered by rankPos asc
     */
    List<RankingSnapshot> findByBoardOrderByRankPosAsc(String board);
}
