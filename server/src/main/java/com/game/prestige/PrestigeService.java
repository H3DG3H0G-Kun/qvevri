package com.game.prestige;

import com.game.exception.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Business logic for the prestige lane.
 *
 * <p>Prestige is awarded externally (contests, festivals, wealth thresholds — deferred
 * to the integration pass). In v1, prestige can be awarded via the {@code /award} endpoint
 * for client/dev purposes. The title ladder is deterministic and recomputed on every award.
 *
 * <h2>Title promotion</h2>
 * After adding prestige, the {@code titleRank} is recomputed via
 * {@link TitleLadder#titleFor(long)}, which returns the highest title whose threshold
 * is &lt;= total prestige. Promotion is immediate and never reversible (prestige never goes down).
 *
 * <h2>Deferred (integration pass)</h2>
 * <ul>
 *   <li>Auto-feed prestige from contests, festivals, wealth milestones.</li>
 *   <li>Prestige bonuses applied to live actions (social modifiers, auction premiums).</li>
 * </ul>
 */
@Service
@Transactional
public class PrestigeService {

    private final PrestigeProfileRepository repository;

    public PrestigeService(PrestigeProfileRepository repository) {
        this.repository = repository;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Returns the prestige profile for the given character, lazy-creating one at
     * prestige=0 / title=GLEKHI on first access.
     *
     * @param characterId the character whose profile to fetch or create
     * @return the (possibly newly created) profile
     */
    public PrestigeProfile getOrCreate(Long characterId) {
        return repository.findByCharacterId(characterId)
                .orElseGet(() -> repository.save(new PrestigeProfile(characterId)));
    }

    /**
     * Awards {@code amount} prestige to the character's profile.
     *
     * <p>The profile is lazy-created if it does not exist. After adding prestige,
     * {@code titleRank} is recomputed from the title ladder.
     *
     * @param characterId the target character
     * @param amount      prestige to award; must be &gt; 0
     * @param reason      human-readable reason (for audit; not persisted)
     * @return the updated profile
     * @throws ApiException BAD_REQUEST if {@code amount} is &lt;= 0
     */
    public PrestigeProfile awardPrestige(Long characterId, long amount, String reason) {
        if (amount <= 0) {
            throw ApiException.badRequest(
                    "Prestige award amount must be > 0 (got " + amount + ")");
        }

        PrestigeProfile profile = getOrCreate(characterId);
        long newPrestige = profile.getPrestige() + amount;
        profile.setPrestige(newPrestige);
        profile.setTitleRank(TitleLadder.titleFor(newPrestige).title());
        profile.setUpdatedAt(System.currentTimeMillis());
        return repository.save(profile);
    }
}
