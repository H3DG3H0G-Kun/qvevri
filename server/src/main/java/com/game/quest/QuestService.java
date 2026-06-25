package com.game.quest;

import com.game.character.CharacterService;
import com.game.exception.ApiException;
import com.game.goods.GoodsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Business logic for the Quest lane.
 *
 * <p>The accept/complete paths are {@code @Transactional} so wallet + goods
 * updates are fully atomic with the PlayerQuest state change.
 *
 * <p>Idempotent completion: if a quest is already COMPLETED the method returns
 * the existing record immediately without re-granting any reward (throws 400).
 */
@Service
@Transactional
public class QuestService {

    private final PlayerQuestRepository playerQuestRepository;
    private final CharacterService      characterService;
    private final GoodsService          goodsService;

    public QuestService(PlayerQuestRepository playerQuestRepository,
                        CharacterService characterService,
                        GoodsService goodsService) {
        this.playerQuestRepository = playerQuestRepository;
        this.characterService      = characterService;
        this.goodsService          = goodsService;
    }

    // ── Catalog (read-only, no ownership check) ────────────────────────────────

    /**
     * Returns all quest definitions from the static catalog.
     * No authentication check required here — ownership is validated at the
     * controller level when accessing per-character data.
     */
    @Transactional(readOnly = true)
    public List<QuestDefinition> getCatalog() {
        return List.copyOf(QuestCatalog.all());
    }

    // ── Per-character queries ──────────────────────────────────────────────────

    /**
     * Returns all PlayerQuest rows for the given character.
     *
     * @param characterId the character id
     * @return list of PlayerQuests (may be empty)
     */
    @Transactional(readOnly = true)
    public List<PlayerQuest> getForCharacter(Long characterId) {
        return playerQuestRepository.findByCharacterId(characterId);
    }

    // ── Accept ─────────────────────────────────────────────────────────────────

    /**
     * Accepts a quest for the given character.
     *
     * <p>Creates a new {@link PlayerQuest} in ACTIVE state.
     *
     * @param characterId the accepting character
     * @param questId     the stable quest catalog id
     * @return the newly persisted ACTIVE PlayerQuest
     * @throws ApiException 400 if questId is unknown
     * @throws ApiException 400 if the character already has this quest in any state
     */
    public PlayerQuest accept(Long characterId, String questId) {
        QuestDefinition def = resolveQuest(questId);

        playerQuestRepository.findByCharacterIdAndQuestId(characterId, questId)
                .ifPresent(existing -> {
                    throw ApiException.badRequest(
                            "Quest '" + questId + "' has already been accepted "
                            + "(status=" + existing.getQuestStatus() + ")");
                });

        PlayerQuest pq = new PlayerQuest(characterId, def.getId());
        return playerQuestRepository.save(pq);
    }

    // ── Complete ───────────────────────────────────────────────────────────────

    /**
     * Completes a quest for the given character and grants the reward.
     *
     * <p><b>Idempotent guard:</b> if the PlayerQuest is already COMPLETED this
     * method throws a 400 rather than re-granting the reward, so the wallet and
     * goods stack can never be double-paid by a retry.
     *
     * <p>v1 auto-satisfies the objective on complete (progress set to objectiveCount).
     * Deeper objective tracking is deferred to a future integration pass.
     *
     * @param characterId the character completing the quest
     * @param questId     the stable quest catalog id
     * @return the updated COMPLETED PlayerQuest
     * @throws ApiException 400 if questId is unknown
     * @throws ApiException 400 if no ACTIVE PlayerQuest exists (not yet accepted, or already COMPLETED)
     */
    public PlayerQuest complete(Long characterId, String questId) {
        QuestDefinition def = resolveQuest(questId);

        PlayerQuest pq = playerQuestRepository
                .findByCharacterIdAndQuestId(characterId, questId)
                .orElseThrow(() -> ApiException.badRequest(
                        "Quest '" + questId + "' has not been accepted by this character"));

        // Idempotent guard — already completed: do NOT re-grant reward
        if (QuestStatus.COMPLETED.equals(pq.getQuestStatus())) {
            throw ApiException.badRequest(
                    "Quest '" + questId + "' is already completed; reward was already granted");
        }

        // v1: auto-satisfy objective
        pq.setProgress(def.getObjectiveCount());
        pq.setQuestStatus(QuestStatus.COMPLETED);
        pq.setCompletedAt(System.currentTimeMillis());

        // Grant GEL reward — adjustWallet handles character lookup internally
        characterService.adjustWallet(characterId, def.getRewardGel());

        // Grant goods reward (if defined)
        if (def.getRewardGoodTypeId() != null) {
            goodsService.grant(characterId, def.getRewardGoodTypeId(), def.getRewardGoodQty());
        }

        return playerQuestRepository.save(pq);
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private QuestDefinition resolveQuest(String questId) {
        QuestDefinition def = QuestCatalog.find(questId);
        if (def == null) {
            throw ApiException.badRequest("Unknown questId: '" + questId + "'");
        }
        return def;
    }
}
