package com.game.skill;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Spring Data JPA repository for {@link SkillProfile}.
 *
 * <p>Custom queries:
 * <ul>
 *   <li>{@link #findByCharacterId} — returns the single profile for a character
 *       (used for lazy-create on GET /api/skill/{characterId}).</li>
 * </ul>
 */
public interface SkillProfileRepository extends JpaRepository<SkillProfile, Long> {

    /**
     * Returns the skill profile for the given character, if it exists.
     *
     * @param characterId the owning character
     * @return the SkillProfile, or {@link Optional#empty()} if not yet created
     */
    Optional<SkillProfile> findByCharacterId(Long characterId);
}
