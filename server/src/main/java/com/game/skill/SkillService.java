package com.game.skill;

import com.game.character.CharacterService;
import com.game.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Business logic for the Skill lane (LANE SKILL, V27).
 *
 * <h3>Lazy profile creation</h3>
 * <p>On the first call to {@link #getProfile}, a {@link SkillProfile} is created
 * with {@code totalPoints = STARTING_POINTS (5)}, {@code spentPoints = 0}.
 * Earning more points by leveling is deferred to the integration pass.
 *
 * <h3>Prereq enforcement</h3>
 * <p>{@link #learn} checks that any {@code prereqId} talent is already in the
 * character's learned-skill set before allowing the new talent to be learned.
 *
 * <h3>Respec (v1 free)</h3>
 * <p>{@link #respec} deletes all LearnedSkill rows and resets spentPoints to 0.
 * A GEL cost (via adjustWallet) can be added in a future pass — note it here.
 */
@Service
@Transactional
public class SkillService {

    /** Skill points granted to every character on first profile access. */
    static final int STARTING_POINTS = 5;

    private final SkillProfileRepository  profileRepo;
    private final LearnedSkillRepository  learnedRepo;
    private final CharacterService        characterService;

    public SkillService(SkillProfileRepository profileRepo,
                        LearnedSkillRepository learnedRepo,
                        CharacterService characterService) {
        this.profileRepo      = profileRepo;
        this.learnedRepo      = learnedRepo;
        this.characterService = characterService;
    }

    // ── Catalog (static, no auth check) ───────────────────────────────────────

    /**
     * Returns all talents from the static catalog.
     *
     * @return unmodifiable collection of all {@link SkillTalent}s
     */
    @Transactional(readOnly = true)
    public Collection<SkillTalent> getCatalog() {
        return SkillCatalog.all();
    }

    // ── Profile (lazy-created) ────────────────────────────────────────────────

    /**
     * Returns the skill profile view for a character, lazy-creating it at
     * {@code totalPoints = 5, spentPoints = 0} if it does not yet exist.
     *
     * @param characterId the owning character
     * @return the current {@link SkillProfileView}
     */
    public SkillProfileView getProfile(Long characterId) {
        SkillProfile profile = getOrCreateProfile(characterId);
        List<LearnedSkill> learned = learnedRepo.findByCharacterId(characterId);
        return SkillProfileView.of(profile, learned);
    }

    // ── Learn ─────────────────────────────────────────────────────────────────

    /**
     * Learns a talent for the given character.
     *
     * <p>Guards (in order):
     * <ol>
     *   <li>404 if the skillId is unknown.</li>
     *   <li>400 ALREADY_LEARNED if the character has already learned this talent.</li>
     *   <li>400 PREREQ_NOT_MET if the talent has a {@code prereqId} and that prereq
     *       has not been learned by this character.</li>
     *   <li>400 INSUFFICIENT_POINTS if availablePoints &lt; cost.</li>
     * </ol>
     *
     * @param characterId owning character (already ownership-verified by the controller)
     * @param skillId     the catalog talent to learn
     * @return the updated {@link SkillProfileView} after learning the talent
     */
    public SkillProfileView learn(Long characterId, String skillId) {
        // 1. Resolve talent — 404 if unknown
        SkillTalent talent = SkillCatalog.find(skillId);
        if (talent == null) {
            throw ApiException.notFound("Unknown talent: '" + skillId + "'");
        }

        // 2. Already learned?
        learnedRepo.findByCharacterIdAndSkillId(characterId, skillId)
                .ifPresent(existing -> {
                    throw new ApiException(
                            "ALREADY_LEARNED",
                            "Talent '" + skillId + "' has already been learned",
                            HttpStatus.BAD_REQUEST);
                });

        // 3. Prereq check
        if (talent.prereqId() != null) {
            boolean prereqMet = learnedRepo
                    .findByCharacterIdAndSkillId(characterId, talent.prereqId())
                    .isPresent();
            if (!prereqMet) {
                throw new ApiException(
                        "PREREQ_NOT_MET",
                        "Prerequisite '" + talent.prereqId() + "' must be learned before "
                        + "'" + skillId + "'",
                        HttpStatus.BAD_REQUEST);
            }
        }

        // 4. Point check
        SkillProfile profile = getOrCreateProfile(characterId);
        int available = profile.getTotalPoints() - profile.getSpentPoints();
        if (available < talent.cost()) {
            throw new ApiException(
                    "INSUFFICIENT_POINTS",
                    "Not enough skill points: have " + available
                    + " available, need " + talent.cost() + " for '" + skillId + "'",
                    HttpStatus.BAD_REQUEST);
        }

        // 5. Record the learned talent and debit points
        learnedRepo.save(new LearnedSkill(characterId, skillId));
        profile.setSpentPoints(profile.getSpentPoints() + talent.cost());
        profileRepo.save(profile);

        List<LearnedSkill> learned = learnedRepo.findByCharacterId(characterId);
        return SkillProfileView.of(profile, learned);
    }

    // ── Respec ────────────────────────────────────────────────────────────────

    /**
     * Resets all learned talents for the character and frees all spent points.
     *
     * <p>v1: respec is free. A GEL cost (via CharacterService.adjustWallet) can be
     * introduced in a future pass without changing this method's signature.
     *
     * @param characterId owning character (already ownership-verified by the controller)
     * @return the updated {@link SkillProfileView} after respec (learned list will be empty)
     */
    public SkillProfileView respec(Long characterId) {
        // Clear all learned skills
        learnedRepo.deleteByCharacterId(characterId);

        // Reset spent points to 0
        SkillProfile profile = getOrCreateProfile(characterId);
        profile.setSpentPoints(0);
        profileRepo.save(profile);

        return SkillProfileView.of(profile, List.of());
    }

    // ── Bonuses ───────────────────────────────────────────────────────────────

    /**
     * Returns a map of bonusType → summed bonusValue across all learned talents
     * for the given character.
     *
     * <p>If the character has learned no talents, returns an empty map.
     *
     * @param characterId the owning character
     * @return aggregated bonus map (bonusType → total bonusValue)
     */
    @Transactional(readOnly = true)
    public Map<String, Double> getBonuses(Long characterId) {
        List<LearnedSkill> learned = learnedRepo.findByCharacterId(characterId);
        Map<String, Double> bonuses = new LinkedHashMap<>();
        for (LearnedSkill ls : learned) {
            SkillTalent talent = SkillCatalog.find(ls.getSkillId());
            if (talent != null) {
                bonuses.merge(talent.bonusType(), talent.bonusValue(), Double::sum);
            }
        }
        return bonuses;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Returns the SkillProfile for the given character, creating it lazily at
     * {@code totalPoints = STARTING_POINTS, spentPoints = 0} if not yet present.
     */
    private SkillProfile getOrCreateProfile(Long characterId) {
        return profileRepo.findByCharacterId(characterId).orElseGet(() -> {
            SkillProfile newProfile = new SkillProfile(characterId, STARTING_POINTS);
            return profileRepo.save(newProfile);
        });
    }
}
