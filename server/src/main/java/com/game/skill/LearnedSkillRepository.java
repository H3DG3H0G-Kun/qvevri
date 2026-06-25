package com.game.skill;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link LearnedSkill}.
 *
 * <p>Custom queries:
 * <ul>
 *   <li>{@link #findByCharacterId} — returns all learned skills for a character
 *       (used by GET /api/skill/{characterId} and POST /api/skill/respec).</li>
 *   <li>{@link #findByCharacterIdAndSkillId} — returns the single row for a
 *       (character, skill) pair (used for already-learned and prereq checks).</li>
 * </ul>
 */
public interface LearnedSkillRepository extends JpaRepository<LearnedSkill, Long> {

    /**
     * Returns all learned-skill rows belonging to the given character, in any order.
     *
     * @param characterId the owning character
     * @return list of LearnedSkill rows (may be empty)
     */
    List<LearnedSkill> findByCharacterId(Long characterId);

    /**
     * Returns the single learned-skill row for the (characterId, skillId) pair.
     *
     * @param characterId the owning character
     * @param skillId     stable catalog talent id
     * @return the row, or {@link Optional#empty()} if this talent has not been learned
     */
    Optional<LearnedSkill> findByCharacterIdAndSkillId(Long characterId, String skillId);

    /**
     * Deletes all learned-skill rows for the given character.
     * Used by respec to wipe the talent slate clean.
     *
     * @param characterId the owning character
     */
    void deleteByCharacterId(Long characterId);
}
