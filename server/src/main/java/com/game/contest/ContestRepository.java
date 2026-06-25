package com.game.contest;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link Contest}.
 */
@Repository
public interface ContestRepository extends JpaRepository<Contest, Long> {

    /**
     * Returns all contests with the given status.
     * Primary use: {@code findByContestStatus("OPEN")} in listOpen / lazy-judge pass.
     */
    List<Contest> findByContestStatus(String contestStatus);
}
