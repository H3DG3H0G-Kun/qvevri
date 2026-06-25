package com.game.contest;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link ContestEntry}.
 */
@Repository
public interface ContestEntryRepository extends JpaRepository<ContestEntry, Long> {

    /**
     * Returns all entries for a given contest.
     * Used by judging and results endpoints.
     */
    List<ContestEntry> findByContestId(Long contestId);

    /**
     * Returns the entry for a specific character in a specific contest.
     * Used to enforce the one-entry-per-character-per-contest guard.
     */
    Optional<ContestEntry> findByContestIdAndCharacterId(Long contestId, Long characterId);
}
