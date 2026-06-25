package com.game.research;

import com.game.character.CharacterService;
import com.game.exception.ApiException;
import com.game.world.clock.WorldClockService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

/**
 * Business logic for the Research lane.
 *
 * <h3>Lazy completion</h3>
 * <p>There is no scheduler. Every call to {@link #getForCharacter} first sweeps
 * all RESEARCHING rows whose {@code readyDay &le; currentAbsoluteDay} and flips
 * them to COMPLETE. This is deterministic and idempotent.
 *
 * <h3>Prereq enforcement</h3>
 * <p>{@link #startResearch} checks that any {@code prereqId} is already COMPLETE
 * for the character before allowing a new node to start.
 */
@Service
@Transactional
public class ResearchService {

    private final PlayerResearchRepository playerResearchRepository;
    private final CharacterService         characterService;
    private final WorldClockService        clock;

    public ResearchService(PlayerResearchRepository playerResearchRepository,
                           CharacterService characterService,
                           WorldClockService clock) {
        this.playerResearchRepository = playerResearchRepository;
        this.characterService         = characterService;
        this.clock                    = clock;
    }

    // ── Catalog (static, no auth check) ───────────────────────────────────────

    /**
     * Returns all research nodes from the static catalog.
     *
     * @return unmodifiable collection of all {@link ResearchNode}s
     */
    @Transactional(readOnly = true)
    public Collection<ResearchNode> getCatalog() {
        return ResearchCatalog.all();
    }

    // ── Per-character queries (with lazy completion) ──────────────────────────

    /**
     * Returns all {@link PlayerResearch} rows for the given character,
     * lazily completing any RESEARCHING rows whose {@code readyDay} has passed.
     *
     * @param characterId the owning character
     * @return list of PlayerResearch rows after lazy completion
     */
    public List<PlayerResearch> getForCharacter(Long characterId) {
        List<PlayerResearch> rows = playerResearchRepository.findByCharacterId(characterId);
        int currentDay = clock.currentAbsoluteDay();
        for (PlayerResearch pr : rows) {
            lazyComplete(pr, currentDay);
        }
        return rows;
    }

    // ── Start research ────────────────────────────────────────────────────────

    /**
     * Starts research on a node for the given character.
     *
     * <p>Guards (in order):
     * <ol>
     *   <li>404 if the nodeId is unknown.</li>
     *   <li>400 if a PlayerResearch row already exists for this (character, node) pair
     *       (regardless of status — cannot re-start completed research).</li>
     *   <li>400 PREREQ_NOT_MET if the node has a {@code prereqId} and that prereq
     *       is not COMPLETE for this character.</li>
     *   <li>400 INSUFFICIENT_FUNDS (via adjustWallet) if the wallet is too low.</li>
     * </ol>
     *
     * @param characterId owning character (already ownership-verified by the controller)
     * @param nodeId      the catalog node to research
     * @return the newly created RESEARCHING PlayerResearch row
     */
    public PlayerResearch startResearch(Long characterId, String nodeId) {
        // 1. Resolve node — 404 if unknown
        ResearchNode node = ResearchCatalog.find(nodeId);
        if (node == null) {
            throw ApiException.notFound("Unknown research node: '" + nodeId + "'");
        }

        // 2. Already started / completed?
        playerResearchRepository.findByCharacterIdAndNodeId(characterId, nodeId)
                .ifPresent(existing -> {
                    throw ApiException.badRequest(
                            "Research '" + nodeId + "' has already been started "
                            + "(status=" + existing.getResearchStatus() + ")");
                });

        // 3. Prereq check — only needed if the node has a prereqId
        if (node.prereqId() != null) {
            PlayerResearch prereq =
                    playerResearchRepository.findByCharacterIdAndNodeId(characterId, node.prereqId())
                            .orElse(null);

            boolean prereqComplete = prereq != null
                    && ResearchStatus.COMPLETE.equals(prereq.getResearchStatus());

            if (!prereqComplete) {
                // Attempt lazy-complete before rejecting (prereq might have just finished)
                if (prereq != null) {
                    int currentDay = clock.currentAbsoluteDay();
                    lazyComplete(prereq, currentDay);
                    prereqComplete = ResearchStatus.COMPLETE.equals(prereq.getResearchStatus());
                }
            }

            if (!prereqComplete) {
                throw new ApiException(
                        "PREREQ_NOT_MET",
                        "Prerequisite '" + node.prereqId() + "' must be COMPLETE before "
                        + "starting '" + nodeId + "'",
                        org.springframework.http.HttpStatus.BAD_REQUEST);
            }
        }

        // 4. Debit wallet — throws 400 INSUFFICIENT_FUNDS if wallet is too low
        characterService.adjustWallet(characterId, -node.costGel());

        // 5. Create RESEARCHING row
        int currentDay = clock.currentAbsoluteDay();
        long readyDay  = currentDay + node.durationDays();

        PlayerResearch pr = new PlayerResearch(characterId, nodeId, currentDay, readyDay);
        return playerResearchRepository.save(pr);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * If the row is RESEARCHING and currentDay &ge; readyDay, flip it to COMPLETE
     * and persist. Idempotent: already-COMPLETE rows are left unchanged.
     */
    private void lazyComplete(PlayerResearch pr, int currentDay) {
        if (ResearchStatus.RESEARCHING.equals(pr.getResearchStatus())
                && currentDay >= pr.getReadyDay()) {
            pr.setResearchStatus(ResearchStatus.COMPLETE);
            playerResearchRepository.save(pr);
        }
    }
}
