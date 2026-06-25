package com.game.achievement;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * JPA repository for {@link PlayerAchievement} entities.
 */
public interface PlayerAchievementRepository extends JpaRepository<PlayerAchievement, Long> {

    /**
     * Returns all PlayerAchievements belonging to the given character,
     * in insertion order.
     *
     * @param characterId the owning character
     * @return list of PlayerAchievements (may be empty)
     */
    List<PlayerAchievement> findByCharacterId(Long characterId);

    /**
     * Returns the PlayerAchievement for a specific character + achievement combination.
     *
     * @param characterId   the character
     * @param achievementId the stable achievement catalog id
     * @return Optional containing the record, or empty if not yet unlocked
     */
    Optional<PlayerAchievement> findByCharacterIdAndAchievementId(
            Long characterId, String achievementId);
}
