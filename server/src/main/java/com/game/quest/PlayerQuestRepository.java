package com.game.quest;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * JPA repository for {@link PlayerQuest} entities.
 */
public interface PlayerQuestRepository extends JpaRepository<PlayerQuest, Long> {

    /**
     * Returns all PlayerQuests belonging to the given character, in insertion order.
     *
     * @param characterId the owning character
     * @return list of PlayerQuests (may be empty)
     */
    List<PlayerQuest> findByCharacterId(Long characterId);

    /**
     * Returns the PlayerQuest for a specific character + quest combination.
     *
     * @param characterId the character
     * @param questId     the stable quest catalog id
     * @return Optional containing the record, or empty if not found
     */
    Optional<PlayerQuest> findByCharacterIdAndQuestId(Long characterId, String questId);
}
