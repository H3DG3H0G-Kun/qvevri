package com.game.wine;

import com.game.bonus.BonusService;
import com.game.bonus.BonusTypes;
import com.game.exception.ApiException;
import com.game.goods.GoodCategory;
import com.game.goods.GoodType;
import com.game.goods.GoodsCatalog;
import com.game.goods.OwnedGood;
import com.game.goods.OwnedGoodRepository;
import com.game.market.CellarItem;
import com.game.market.CellarItemRepository;
import com.game.world.clock.WorldClockService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Core service for the winemaking-depth v1 flow (BACKEND-DEPTH-SPEC §6, WINE lane).
 *
 * <p>Manages three operations on {@link CellarItem} records:
 * <ol>
 *   <li><b>Start fermentation</b> — attaches a vessel, sets FERMENTING state and
 *       {@code fermentReadyDay = currentDay + fermentationDays(vessel)}.</li>
 *   <li><b>Status</b> — auto-transitions from FERMENTING → READY when the current
 *       world-clock day has passed {@code fermentReadyDay}; computes current aged
 *       quality.</li>
 *   <li><b>Bottle</b> — transitions READY → BOTTLED; locks quality.</li>
 * </ol>
 *
 * <p><b>The golden rule:</b> every operation is gated. If the caller never calls
 * {@code startFermentation} the CellarItem's fermentation fields stay {@code null}
 * and the default (instant-harvest) path is untouched.
 */
@Service
@Transactional
public class WineMakingService {

    private final CellarItemRepository cellarItemRepository;
    private final OwnedGoodRepository  ownedGoodRepository;
    private final WorldClockService    clock;
    private final BonusService         bonusService;

    public WineMakingService(CellarItemRepository cellarItemRepository,
                             OwnedGoodRepository ownedGoodRepository,
                             WorldClockService clock,
                             BonusService bonusService) {
        this.cellarItemRepository = cellarItemRepository;
        this.ownedGoodRepository  = ownedGoodRepository;
        this.clock                = clock;
        this.bonusService         = bonusService;
    }

    // ── Start fermentation ────────────────────────────────────────────────────

    /**
     * Starts the fermentation clock on a CellarItem.
     *
     * <p>Guards:
     * <ul>
     *   <li>CellarItem must exist and be owned by {@code characterId}.</li>
     *   <li>CellarItem must not already be in a fermentation state.</li>
     *   <li>If {@code vesselGoodId} is non-null the OwnedGood must exist,
     *       be owned by the same character, and be a VESSEL category good.</li>
     * </ul>
     *
     * @param cellarItemId  id of the CellarItem to ferment
     * @param characterId   owning character (auth guard)
     * @param vesselGoodId  id of the OwnedGood vessel to use, or {@code null}
     * @return status view after the transition
     */
    public FermentStatusView startFermentation(long cellarItemId,
                                               long characterId,
                                               Long vesselGoodId) {
        CellarItem item = requireOwnedItem(cellarItemId, characterId);

        // Guard: can only start if not already fermenting
        if (item.getFermentationState() != null) {
            throw ApiException.badRequest(
                    "Fermentation already started (state=" + item.getFermentationState() + ")");
        }

        // Resolve vessel catalog type (null vesselGoodId → no vessel)
        String vesselTypeId = resolveVesselTypeId(vesselGoodId, characterId);

        long currentDay = clock.currentAbsoluteDay();
        int  duration   = VesselEffect.fermentationDays(vesselTypeId);
        long readyDay   = currentDay + duration;

        // Apply vessel style override
        String newStyle = VesselEffect.resolveStyle(vesselTypeId, item.getStyle());
        item.setStyle(newStyle);

        // Snapshot base quality (before aging)
        item.setBaseQuality(item.getQuality());

        // Set fermentation fields
        item.setFermentationState(FermentationState.FERMENTING.name());
        item.setVesselGoodId(vesselGoodId);
        item.setFermentStartedDay(currentDay);
        item.setFermentReadyDay(readyDay);

        cellarItemRepository.save(item);

        return buildStatus(item, currentDay);
    }

    // ── Status (with auto-transition) ─────────────────────────────────────────

    /**
     * Returns the current fermentation status for a CellarItem.
     *
     * <p>If the item is in FERMENTING state and the current world-clock day has
     * passed {@code fermentReadyDay}, this method automatically transitions it to
     * READY and starts the aging clock. Aging quality is computed via
     * {@link AgingModel#agedQuality} and reflected in the view.
     *
     * @param cellarItemId  id of the CellarItem
     * @param characterId   owning character (auth guard)
     * @return the current status view
     */
    @Transactional
    public FermentStatusView getStatus(long cellarItemId, long characterId) {
        CellarItem item = requireOwnedItem(cellarItemId, characterId);
        long currentDay = clock.currentAbsoluteDay();

        // Auto-transition FERMENTING → READY
        if (FermentationState.FERMENTING.name().equals(item.getFermentationState())
                && item.getFermentReadyDay() != null
                && currentDay >= item.getFermentReadyDay()) {

            // Apply vessel quality delta once, at the moment fermentation completes.
            // INTEGRATION: also apply the maker's QUALITY bonus (career + skills) — e.g. a
            // Winemaker's +10%. 0.0 for a default character, so completion is unchanged.
            String vesselTypeId = resolveVesselTypeIdFromItem(item);
            double vesselDelta  = VesselEffect.qualityDelta(vesselTypeId);
            double qBonus       = bonusService.total(characterId, BonusTypes.QUALITY);
            double base         = (item.getBaseQuality() != null ? item.getBaseQuality() : item.getQuality());
            double newBase      = Math.min(AgingModel.QUALITY_CAP, (base + vesselDelta) * (1.0 + qBonus));
            item.setBaseQuality(newBase);
            item.setQuality(newBase); // update so existing fields are coherent

            item.setFermentationState(FermentationState.READY.name());
            item.setAgingFromDay(item.getFermentReadyDay()); // aging starts from ferment completion

            cellarItemRepository.save(item);
        }

        // Compute current aged quality for READY/BOTTLED items
        if (FermentationState.READY.name().equals(item.getFermentationState())
                && item.getAgingFromDay() != null) {
            double aged = AgingModel.agedQuality(
                    item.getBaseQuality() != null ? item.getBaseQuality() : item.getQuality(),
                    item.getAgingFromDay(),
                    currentDay);
            item.setQuality(aged);
            cellarItemRepository.save(item);
        }

        return buildStatus(item, currentDay);
    }

    // ── Bottle ────────────────────────────────────────────────────────────────

    /**
     * Bottles a READY CellarItem, locking its quality at the current aged value.
     *
     * @param cellarItemId  id of the CellarItem
     * @param characterId   owning character (auth guard)
     * @return the status view after bottling
     * @throws ApiException BAD_REQUEST if the item is not in READY state
     */
    public FermentStatusView bottle(long cellarItemId, long characterId) {
        CellarItem item = requireOwnedItem(cellarItemId, characterId);
        long currentDay = clock.currentAbsoluteDay();

        if (!FermentationState.READY.name().equals(item.getFermentationState())) {
            throw ApiException.badRequest(
                    "Item is not READY for bottling (state="
                            + item.getFermentationState() + ")");
        }

        // Lock aged quality
        if (item.getAgingFromDay() != null) {
            double aged = AgingModel.agedQuality(
                    item.getBaseQuality() != null ? item.getBaseQuality() : item.getQuality(),
                    item.getAgingFromDay(),
                    currentDay);
            item.setQuality(aged);
        }

        item.setFermentationState(FermentationState.BOTTLED.name());
        cellarItemRepository.save(item);

        return buildStatus(item, currentDay);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private CellarItem requireOwnedItem(long cellarItemId, long characterId) {
        return cellarItemRepository
                .findByIdAndCharacterId(cellarItemId, characterId)
                .orElseThrow(() -> ApiException.notFound(
                        "CellarItem " + cellarItemId + " not found for character " + characterId));
    }

    /**
     * Resolves the catalog GoodType id for the vessel OwnedGood.
     * Returns null if vesselGoodId is null (no vessel selected — gated default).
     */
    private String resolveVesselTypeId(Long vesselGoodId, long characterId) {
        if (vesselGoodId == null) {
            return null; // no vessel → all effects off
        }
        OwnedGood og = ownedGoodRepository.findById(vesselGoodId)
                .orElseThrow(() -> ApiException.notFound(
                        "OwnedGood not found: " + vesselGoodId));
        if (!og.getCharacterId().equals(characterId)) {
            throw ApiException.forbidden("Vessel good is not owned by character " + characterId);
        }
        GoodType gt = GoodsCatalog.find(og.getGoodTypeId());
        if (gt == null || gt.getCategory() != GoodCategory.VESSEL) {
            throw ApiException.badRequest("Good " + og.getGoodTypeId() + " is not a VESSEL");
        }
        return og.getGoodTypeId();
    }

    /**
     * Resolves the vessel type id from an already-fermented item (for auto-transition).
     * Does NOT verify ownership — item ownership was already validated at start.
     */
    private String resolveVesselTypeIdFromItem(CellarItem item) {
        if (item.getVesselGoodId() == null) {
            return null;
        }
        return ownedGoodRepository.findById(item.getVesselGoodId())
                .map(og -> og.getGoodTypeId())
                .orElse(null);
    }

    private FermentStatusView buildStatus(CellarItem item, long currentDay) {
        long daysUntilReady = 0L;
        if (FermentationState.FERMENTING.name().equals(item.getFermentationState())
                && item.getFermentReadyDay() != null) {
            daysUntilReady = Math.max(0L, item.getFermentReadyDay() - currentDay);
        }

        return new FermentStatusView(
                item.getId(),
                item.getFermentationState(),
                item.getFermentStartedDay(),
                item.getFermentReadyDay(),
                item.getAgingFromDay(),
                currentDay,
                daysUntilReady,
                item.getQuality(),
                item.getBaseQuality(),
                item.getStyle(),
                item.getVesselGoodId()
        );
    }
}
