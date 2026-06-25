package com.game.achievement;

import com.game.character.CharacterService;
import com.game.exception.ApiException;
import com.game.goods.GoodsService;
import com.game.world.clock.WorldClockService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Business logic for the Achievement lane.
 *
 * <p>The unlock path is {@code @Transactional} so the wallet adjustment, goods
 * grant, and PlayerAchievement persist are fully atomic.
 *
 * <p>Idempotent unlock: if a character already has the achievement unlocked the
 * method throws 400 ALREADY_UNLOCKED — the reward is never double-granted.
 *
 * <p>v1 grants achievements on demand (criteria auto-satisfied). Real
 * criteria-checking against other systems is a later integration pass per the
 * CONTEST-ACHIEVEMENT-CHAT-SPEC.
 */
@Service
@Transactional
public class AchievementService {

    private final PlayerAchievementRepository achievementRepository;
    private final CharacterService            characterService;
    private final GoodsService                goodsService;
    private final WorldClockService           worldClockService;

    public AchievementService(
            PlayerAchievementRepository achievementRepository,
            CharacterService characterService,
            GoodsService goodsService,
            WorldClockService worldClockService) {
        this.achievementRepository = achievementRepository;
        this.characterService      = characterService;
        this.goodsService          = goodsService;
        this.worldClockService     = worldClockService;
    }

    // ── Catalog (read-only, no ownership check) ────────────────────────────────

    /**
     * Returns all achievement definitions from the static catalog.
     * No ownership check required here — auth is validated at the controller level.
     */
    @Transactional(readOnly = true)
    public List<AchievementDefinition> getCatalog() {
        return List.copyOf(AchievementCatalog.all());
    }

    // ── Per-character queries ──────────────────────────────────────────────────

    /**
     * Returns all PlayerAchievement rows for the given character.
     *
     * @param characterId the character id
     * @return list of PlayerAchievements (may be empty)
     */
    @Transactional(readOnly = true)
    public List<PlayerAchievement> getForCharacter(Long characterId) {
        return achievementRepository.findByCharacterId(characterId);
    }

    // ── Unlock ─────────────────────────────────────────────────────────────────

    /**
     * Unlocks an achievement for the given character and grants the reward.
     *
     * <p><b>Idempotent guard:</b> if the achievement is already unlocked for
     * this character the method throws 400 ALREADY_UNLOCKED rather than
     * re-granting the reward, so the wallet and goods stack can never be
     * double-paid by a retry.
     *
     * <p>v1: criteria are auto-satisfied on request (no validation against
     * other game systems). Real criteria-checking is a future integration pass.
     *
     * @param characterId   the character unlocking the achievement
     * @param achievementId the stable achievement catalog id
     * @return the newly persisted {@link PlayerAchievement}
     * @throws ApiException 404 if achievementId is unknown
     * @throws ApiException 400 ALREADY_UNLOCKED if already unlocked for this character
     */
    public PlayerAchievement unlock(Long characterId, String achievementId) {
        AchievementDefinition def = resolveAchievement(achievementId);

        // Idempotent guard — already unlocked: do NOT re-grant reward
        achievementRepository
                .findByCharacterIdAndAchievementId(characterId, achievementId)
                .ifPresent(existing -> {
                    throw new ApiException(
                            "ALREADY_UNLOCKED",
                            "Achievement '" + achievementId
                            + "' is already unlocked for this character; "
                            + "reward was already granted on sim-day "
                            + existing.getUnlockedDay(),
                            org.springframework.http.HttpStatus.BAD_REQUEST);
                });

        long simDay = worldClockService.currentAbsoluteDay();
        PlayerAchievement pa = new PlayerAchievement(characterId, achievementId, simDay);
        pa = achievementRepository.save(pa);

        // Grant GEL reward
        characterService.adjustWallet(characterId, def.getRewardGel());

        // Grant goods reward (if defined)
        if (def.getRewardGoodTypeId() != null) {
            goodsService.grant(characterId, def.getRewardGoodTypeId(), def.getRewardGoodQty());
        }

        return pa;
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private AchievementDefinition resolveAchievement(String achievementId) {
        AchievementDefinition def = AchievementCatalog.find(achievementId);
        if (def == null) {
            throw ApiException.notFound("Unknown achievementId: '" + achievementId + "'");
        }
        return def;
    }
}
