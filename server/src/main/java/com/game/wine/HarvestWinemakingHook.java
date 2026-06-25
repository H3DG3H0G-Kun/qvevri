package com.game.wine;

import com.game.estate.Vineyard;
import com.game.goods.GoodCategory;
import com.game.goods.GoodType;
import com.game.goods.GoodsCatalog;
import com.game.goods.OwnedGood;
import com.game.goods.OwnedGoodRepository;
import com.game.market.CellarItem;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Gated harvest-time integration hooks for the §6 pipeline effects (BACKEND-DEPTH-SPEC
 * §6, WINE lane).
 *
 * <p>This component applies all additive effects that the spec requires at harvest
 * time, <em>without touching the existing instant-harvest code path at all</em>.
 * Each effect is individually gated:
 *
 * <ol>
 *   <li><b>Certified vine stock</b> — if the character owns a certified VINE_STOCK
 *       good for this vineyard's variety, the vineyard's {@code ownRoots} flag is
 *       updated to reflect the stock's {@code ownRoots} attribute. If no stock is
 *       found, the existing flag is left unchanged (default path unaffected).</li>
 *   <li><b>Press tier</b> — if the character owns a press OwnedGood (EQUIPMENT
 *       category with a {@code qualityTier} attribute), a quality nudge is applied
 *       to the CellarItem proportional to the tier. No press owned → no change.</li>
 *   <li><b>Spray consumption</b> — if the vineyard's copper/sulfur levers are {@code > 0}
 *       and the character owns the matching INPUT good, those goods are drawn down.
 *       The sim output (quality/yield) is NOT altered (v1 spec).</li>
 * </ol>
 *
 * <p>All effects are additive. The CellarItem passed in is the one returned by
 * {@link com.game.estate.VineyardReplayService#harvest} (not yet persisted). The
 * caller (VineyardController) must persist the item after this hook returns.
 *
 * <p><b>Golden rule:</b> absent equipment → zero-delta → today's exact output.
 */
@Component
public class HarvestWinemakingHook {

    private static final String COPPER_SULFATE_ID = "copper_sulfate";
    private static final String SULFUR_DUST_ID    = "sulfur_dust";

    private final OwnedGoodRepository ownedGoodRepository;

    public HarvestWinemakingHook(OwnedGoodRepository ownedGoodRepository) {
        this.ownedGoodRepository = ownedGoodRepository;
    }

    /**
     * Applies all §6 gated harvest-time effects to the (not-yet-persisted) CellarItem.
     *
     * <p>Safe to call unconditionally from the harvest controller — it is a no-op
     * when no relevant goods are owned.
     *
     * @param vineyard   the source vineyard (for variety, levers)
     * @param characterId the owning character's id
     * @param item       the CellarItem produced by the harvest replay (mutated in-place)
     */
    @Transactional
    public void apply(Vineyard vineyard, long characterId, CellarItem item) {
        applyPressTier(characterId, item);
        consumeSprays(vineyard, characterId);
        // Note: certified vine stock is applied at PLANTING time (see applyVineStock),
        // not at harvest. The Vineyard's ownRoots flag already reflects any
        // certified stock used when the vineyard was created.
    }

    /**
     * Apply certified vine stock effect at vineyard creation time.
     * Call from the plant endpoint after creating the Vineyard (before saving).
     *
     * <p>If the character owns a certified VINE_STOCK good matching the vineyard's
     * variety, the vineyard's {@code ownRoots} flag is updated to match the
     * stock's {@code ownRoots} attribute. Absent stock → existing default stands.
     *
     * @param vineyard    the new Vineyard (not yet saved; mutated in-place)
     * @param characterId the planting character
     */
    public void applyVineStock(Vineyard vineyard, long characterId) {
        String varietyName = vineyard.getVariety() != null
                ? vineyard.getVariety().name() : null;
        if (varietyName == null) {
            return; // no variety → no-op
        }

        // Find VINE_STOCK goods for this character and match the variety
        for (OwnedGood og : ownedGoodRepository.findByCharacterId(characterId)) {
            GoodType gt = GoodsCatalog.find(og.getGoodTypeId());
            if (gt == null || gt.getCategory() != GoodCategory.VINE_STOCK) {
                continue;
            }
            Object storedVariety = gt.getAttributes().get("variety");
            if (storedVariety == null || !storedVariety.toString().equalsIgnoreCase(varietyName)) {
                continue;
            }
            // Found a matching vine stock — apply ownRoots from the catalog attribute
            Object ownRootsAttr = gt.getAttributes().get("ownRoots");
            if (ownRootsAttr instanceof Boolean) {
                vineyard.setOwnRoots((Boolean) ownRootsAttr);
            }
            return; // apply from the first matching stock (don't stack)
        }
        // No matching stock owned → ownRoots stays at its default (gated)
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Press tier nudge: quality += (qualityTier - 1) * PRESS_QUALITY_PER_TIER.
     * Tier 1 (basket press) = +0.0 (baseline); tier 2 = +1.5; tier 3 = +3.0.
     * No press owned → no nudge (gated).
     */
    private void applyPressTier(long characterId, CellarItem item) {
        int bestTier = 0;
        for (OwnedGood og : ownedGoodRepository.findByCharacterId(characterId)) {
            GoodType gt = GoodsCatalog.find(og.getGoodTypeId());
            if (gt == null || gt.getCategory() != GoodCategory.EQUIPMENT) {
                continue;
            }
            Object tierAttr = gt.getAttributes().get("qualityTier");
            if (tierAttr instanceof Number) {
                bestTier = Math.max(bestTier, ((Number) tierAttr).intValue());
            }
        }
        if (bestTier <= 0) {
            return; // no press owned → no-op
        }
        double delta = (bestTier - 1) * 1.5; // tier1=0, tier2=1.5, tier3=3.0
        double newQuality = Math.min(100.0, item.getQuality() + delta);
        item.setQuality(newQuality);
    }

    /**
     * Spray consumption: draw down copper/sulfur INPUT goods proportional to levers.
     * Does NOT alter sim output — pure goods sink.
     */
    private void consumeSprays(Vineyard vineyard, long characterId) {
        double copperLever = vineyard.getCopperSpray01();
        double sulfurLever = vineyard.getSulfurSpray01();

        if (copperLever > 0) {
            consumeInput(characterId, COPPER_SULFATE_ID, copperLever);
        }
        if (sulfurLever > 0) {
            consumeInput(characterId, SULFUR_DUST_ID, sulfurLever);
        }
    }

    private void consumeInput(long characterId, String goodTypeId, double lever) {
        Optional<OwnedGood> opt =
                ownedGoodRepository.findByCharacterIdAndGoodTypeId(characterId, goodTypeId);
        if (opt.isEmpty()) {
            return; // not owned → silently skip (v1 spec)
        }
        OwnedGood og = opt.get();
        double toConsume = lever; // 1 kg per unit of lever
        if (og.getQuantity() <= 0) {
            return;
        }
        double remaining = og.getQuantity() - toConsume;
        if (remaining <= 0) {
            ownedGoodRepository.delete(og);
        } else {
            og.setQuantity(remaining);
            ownedGoodRepository.save(og);
        }
    }
}
