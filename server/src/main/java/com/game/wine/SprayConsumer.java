package com.game.wine;

import com.game.goods.GoodsService;
import com.game.goods.OwnedGood;
import com.game.goods.OwnedGoodRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Draws down spray INPUT goods from inventory when a season's copper or sulfur
 * lever is active (BACKEND-DEPTH-SPEC §6 "Sprays consumed" effect).
 *
 * <p><b>Gating rule (§6 golden rule):</b>
 * <ul>
 *   <li>Lever = 0.0 → no consumption; method is a no-op.</li>
 *   <li>Lever &gt; 0 and good present → consumes {@code lever × DOSE_KG_PER_UNIT}
 *       kg from the character's inventory stack.</li>
 *   <li>Lever &gt; 0 but good absent (not owned or quantity = 0) → silently
 *       skipped (v1 spec: "consume if present; do NOT change sim output").</li>
 * </ul>
 *
 * <p>This component intentionally does NOT alter the vineyard's sim output —
 * it is a pure money/goods sink that keeps determinism and all existing tests
 * green.
 *
 * <p>Catalog IDs: {@code "copper_sulfate"} and {@code "sulfur_dust"}.
 */
@Component
public class SprayConsumer {

    /**
     * How many kg of spray are consumed per unit of lever per application.
     * With lever=1.0, one seasonal application consumes 1 kg.
     */
    public static final double DOSE_KG_PER_UNIT = 1.0;

    /** Catalog id for copper sulfate spray. */
    public static final String COPPER_SULFATE_ID = "copper_sulfate";

    /** Catalog id for sulfur dust spray. */
    public static final String SULFUR_DUST_ID    = "sulfur_dust";

    private final OwnedGoodRepository ownedGoodRepository;

    public SprayConsumer(OwnedGoodRepository ownedGoodRepository) {
        this.ownedGoodRepository = ownedGoodRepository;
    }

    /**
     * Attempts to consume copper spray goods for one seasonal application.
     *
     * @param characterId   the character whose inventory is drawn down
     * @param copperLever   the copper spray lever value 0..1 (0 = no consumption)
     */
    @Transactional
    public void consumeCopper(long characterId, double copperLever) {
        consumeSpray(characterId, COPPER_SULFATE_ID, copperLever);
    }

    /**
     * Attempts to consume sulfur spray goods for one seasonal application.
     *
     * @param characterId   the character whose inventory is drawn down
     * @param sulfurLever   the sulfur spray lever value 0..1 (0 = no consumption)
     */
    @Transactional
    public void consumeSulfur(long characterId, double sulfurLever) {
        consumeSpray(characterId, SULFUR_DUST_ID, sulfurLever);
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private void consumeSpray(long characterId, String goodTypeId, double lever) {
        if (lever <= 0.0) {
            return; // lever off → no consumption (default path unchanged)
        }
        double toConsume = lever * DOSE_KG_PER_UNIT;

        Optional<OwnedGood> optGood =
                ownedGoodRepository.findByCharacterIdAndGoodTypeId(characterId, goodTypeId);

        if (optGood.isEmpty()) {
            return; // good not owned → silently skip (v1 spec)
        }

        OwnedGood good = optGood.get();
        if (good.getQuantity() <= 0) {
            return; // nothing to consume
        }

        double remaining = good.getQuantity() - toConsume;
        if (remaining <= 0) {
            // Consume all available and remove the row
            ownedGoodRepository.delete(good);
        } else {
            good.setQuantity(remaining);
            ownedGoodRepository.save(good);
        }
    }
}
