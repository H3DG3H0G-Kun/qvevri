package com.game.progression;

import com.game.exception.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Business logic for per-character XP, level, and reputation.
 *
 * <h2>Level curve</h2>
 * <pre>
 *   xpLevel = floor(sqrt(xp / 100.0)) + 1
 * </pre>
 * Examples:
 * <ul>
 *   <li>xp=0    → level 1</li>
 *   <li>xp=99   → level 1  (floor(sqrt(0.99)) + 1 = 0+1 = 1)</li>
 *   <li>xp=100  → level 2  (floor(sqrt(1.0))  + 1 = 1+1 = 2)</li>
 *   <li>xp=399  → level 2  (floor(sqrt(3.99)) + 1 = 1+1 = 2)</li>
 *   <li>xp=400  → level 3  (floor(sqrt(4.0))  + 1 = 2+1 = 3)</li>
 *   <li>xp=900  → level 4</li>
 *   <li>xp=1600 → level 5</li>
 *   <li>xp=2500 → level 6</li>
 * </ul>
 *
 * <p>The curve is monotonically non-decreasing; levels never go backwards.
 * It is deterministic — given the same xp value you always get the same level.
 */
@Service
@Transactional
public class ProgressionService {

    private final ProgressionProfileRepository repository;

    public ProgressionService(ProgressionProfileRepository repository) {
        this.repository = repository;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Returns the profile for the given character, creating one at
     * xp=0 / level=1 / reputation=0 if it does not yet exist.
     *
     * @param characterId the character whose profile to fetch or create
     * @return the (possibly newly created) profile
     */
    public ProgressionProfile getOrCreate(Long characterId) {
        return repository.findByCharacterId(characterId)
                .orElseGet(() -> repository.save(new ProgressionProfile(characterId)));
    }

    /**
     * Awards {@code amount} XP to the character's profile.
     *
     * <p>The profile is lazy-created if it does not exist. After adding the XP,
     * {@code xpLevel} is recomputed from the deterministic level curve.
     *
     * @param characterId the target character
     * @param amount      XP to award; must be &gt; 0
     * @param reason      human-readable reason (logged / for audit; not persisted)
     * @return the updated profile
     * @throws ApiException BAD_REQUEST if {@code amount} is &lt;= 0
     */
    public ProgressionProfile awardXp(Long characterId, long amount, String reason) {
        if (amount <= 0) {
            throw ApiException.badRequest("XP award amount must be > 0 (got " + amount + ")");
        }

        ProgressionProfile profile = getOrCreate(characterId);
        long newXp = profile.getXp() + amount;
        profile.setXp(newXp);
        profile.setXpLevel(computeLevel(newXp));
        profile.setUpdatedAt(System.currentTimeMillis());
        return repository.save(profile);
    }

    /**
     * Adjusts the character's reputation by {@code delta} (positive = gain,
     * negative = loss). No lower bound is enforced at the service level.
     *
     * @param characterId the target character
     * @param delta       the signed reputation change
     * @return the updated profile
     */
    public ProgressionProfile adjustReputation(Long characterId, int delta) {
        ProgressionProfile profile = getOrCreate(characterId);
        profile.setReputation(profile.getReputation() + delta);
        profile.setUpdatedAt(System.currentTimeMillis());
        return repository.save(profile);
    }

    // ── Level-curve logic ─────────────────────────────────────────────────────

    /**
     * Deterministic level from XP.
     *
     * <pre>level = floor(sqrt(xp / 100.0)) + 1</pre>
     *
     * @param xp total experience points (must be &gt;= 0)
     * @return level, minimum 1
     */
    static int computeLevel(long xp) {
        return (int) Math.floor(Math.sqrt(xp / 100.0)) + 1;
    }
}
