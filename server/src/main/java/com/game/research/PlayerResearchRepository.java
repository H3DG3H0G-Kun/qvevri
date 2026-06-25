package com.game.research;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link PlayerResearch}.
 *
 * <p>Custom queries:
 * <ul>
 *   <li>{@link #findByCharacterId} — returns all research rows for a character
 *       (used by GET /api/research/{characterId}).</li>
 *   <li>{@link #findByCharacterIdAndNodeId} — returns the single row for a
 *       (character, node) pair (used for start/prereq checks).</li>
 * </ul>
 */
public interface PlayerResearchRepository extends JpaRepository<PlayerResearch, Long> {

    /**
     * Returns all research rows belonging to the given character, in any order.
     *
     * @param characterId the owning character
     * @return list of PlayerResearch rows (may be empty)
     */
    List<PlayerResearch> findByCharacterId(Long characterId);

    /**
     * Returns the single research row for the (characterId, nodeId) pair.
     *
     * @param characterId the owning character
     * @param nodeId      stable catalog node id
     * @return the row, or {@link Optional#empty()} if not started
     */
    Optional<PlayerResearch> findByCharacterIdAndNodeId(Long characterId, String nodeId);
}
