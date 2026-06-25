package com.game.build;

import com.game.character.Character;
import com.game.character.CharacterRepository;
import com.game.character.CharacterService;
import com.game.exception.ApiException;
import com.game.goods.GoodsService;
import com.game.goods.OwnedGood;
import com.game.goods.OwnedGoodRepository;
import com.game.land.ParcelRepository;
import com.game.world.clock.WorldClockService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Business logic for estate building construction and bonus aggregation.
 *
 * <p><b>Transaction safety</b> — {@link #construct} runs inside a single
 * {@code @Transactional} method with the following ordered steps:
 * <ol>
 *   <li><b>Pre-check wallet</b> (read-only) — throws 400 INSUFFICIENT_FUNDS
 *       before any write if balance is too low.</li>
 *   <li><b>Pre-check goods</b> (read-only) — throws 400 MISSING_GOODS before
 *       any write if any input good is absent or insufficient.</li>
 *   <li><b>Debit wallet</b> — first write; calls
 *       {@link CharacterService#adjustWallet}.</li>
 *   <li><b>Consume goods</b> — calls {@link GoodsService#decrement} for each
 *       input.</li>
 *   <li><b>Persist building</b> — saves and returns the new {@link Building}.</li>
 * </ol>
 * Because steps 1–2 are pure reads that throw exceptions, a failed funds or
 * goods check never reaches any mutation. The entire method is transactional so
 * any unexpected failure after step 3 still rolls back atomically.
 */
@Service
public class BuildService {

    private final BuildingRepository  buildingRepository;
    private final CharacterService    characterService;
    private final CharacterRepository characterRepository;
    private final GoodsService        goodsService;
    private final OwnedGoodRepository ownedGoodRepository;
    private final ParcelRepository    parcelRepository;
    private final WorldClockService   worldClockService;

    public BuildService(BuildingRepository buildingRepository,
                        CharacterService characterService,
                        CharacterRepository characterRepository,
                        GoodsService goodsService,
                        OwnedGoodRepository ownedGoodRepository,
                        ParcelRepository parcelRepository,
                        WorldClockService worldClockService) {
        this.buildingRepository  = buildingRepository;
        this.characterService    = characterService;
        this.characterRepository = characterRepository;
        this.goodsService        = goodsService;
        this.ownedGoodRepository = ownedGoodRepository;
        this.parcelRepository    = parcelRepository;
        this.worldClockService   = worldClockService;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Returns all building type definitions from the static catalog.
     */
    public Collection<BuildingType> getCatalog() {
        return BuildingCatalog.all();
    }

    /**
     * Returns all buildings owned by the given character.
     */
    @Transactional(readOnly = true)
    public List<Building> getForCharacter(Long characterId) {
        return buildingRepository.findByOwnerCharacterId(characterId);
    }

    /**
     * Aggregates bonus values by bonusType across all buildings owned by
     * the given character.
     *
     * <p>For each building the {@link BuildingType#getBonusValue()} is
     * multiplied by {@code buildingLevel} and accumulated into a map keyed
     * by {@link BuildingType#getBonusType()}.
     *
     * @param characterId the character whose bonuses to aggregate
     * @return map of bonusType → summed bonusValue
     */
    @Transactional(readOnly = true)
    public Map<String, Double> getBonuses(Long characterId) {
        List<Building> buildings = buildingRepository.findByOwnerCharacterId(characterId);
        Map<String, Double> bonuses = new HashMap<>();
        for (Building b : buildings) {
            BuildingType type = BuildingCatalog.find(b.getBuildingTypeId());
            if (type == null) continue; // defensive: skip unknown catalog id
            bonuses.merge(type.getBonusType(),
                          type.getBonusValue() * b.getBuildingLevel(),
                          Double::sum);
        }
        return bonuses;
    }

    /**
     * Constructs a new estate building for the character.
     *
     * <p>See class-level javadoc for the full transaction order.
     *
     * @param characterId    the character constructing the building
     * @param parcelId       optional land parcel id (null = no parcel attachment)
     * @param buildingTypeId stable id from {@link BuildingCatalog}
     * @return the newly persisted {@link Building}
     * @throws ApiException 400 BAD_REQUEST   if buildingTypeId is unknown
     * @throws ApiException 404 NOT_FOUND     if parcelId is given but not owned by character
     * @throws ApiException 400 BAD_REQUEST   with code INSUFFICIENT_FUNDS if wallet is too low
     * @throws ApiException 400 BAD_REQUEST   with code MISSING_GOODS if any input good is absent
     */
    @Transactional
    public Building construct(Long characterId, Long parcelId, String buildingTypeId) {

        // 0. Resolve building type
        BuildingType type = BuildingCatalog.find(buildingTypeId);
        if (type == null) {
            throw ApiException.badRequest("Unknown buildingTypeId: " + buildingTypeId);
        }

        // 0b. Optional parcel ownership check (read-only, do not modify land)
        if (parcelId != null) {
            parcelRepository.findByIdAndOwnerCharacterId(parcelId, characterId)
                    .orElseThrow(() -> ApiException.notFound(
                            "Parcel " + parcelId
                            + " not found or not owned by character " + characterId));
        }

        // ── STEP 1: Pre-check wallet (pure read, no mutation) ────────────────
        double walletGel = characterRepository.findById(characterId)
                .map(Character::getWalletGel)
                .orElseThrow(() -> ApiException.badRequest(
                        "Character not found: " + characterId));
        if (walletGel < type.getCostGel()) {
            throw ApiException.badRequest(
                    "INSUFFICIENT_FUNDS: wallet " + walletGel
                    + " GEL, need " + type.getCostGel() + " GEL");
        }

        // ── STEP 2: Pre-check all input goods (pure read, no mutation) ───────
        List<BuildingTypeInput> inputs = type.getInputs();
        long[] ownedGoodIds = new long[inputs.size()];
        for (int i = 0; i < inputs.size(); i++) {
            BuildingTypeInput inp = inputs.get(i);
            OwnedGood og = ownedGoodRepository
                    .findByCharacterIdAndGoodTypeId(characterId, inp.getGoodTypeId())
                    .orElse(null);
            double have = (og == null) ? 0.0 : og.getQuantity();
            if (have < inp.getQty()) {
                throw ApiException.badRequest(
                        "MISSING_GOODS: need " + inp.getQty() + "× "
                        + inp.getGoodTypeId()
                        + " but character only has " + have);
            }
            ownedGoodIds[i] = og.getId();
        }

        // ── STEP 3: Debit wallet (first mutation) ────────────────────────────
        // adjustWallet throws ApiException(INSUFFICIENT_FUNDS, 402) if balance
        // goes negative; our pre-check above guarantees this never fires, but
        // the wrapping transaction would still roll back if it did.
        characterService.adjustWallet(characterId, -type.getCostGel());

        // ── STEP 4: Consume each input good ──────────────────────────────────
        for (int i = 0; i < inputs.size(); i++) {
            goodsService.decrement(ownedGoodIds[i], inputs.get(i).getQty());
        }

        // ── STEP 5: Persist and return the building ───────────────────────────
        long builtDay  = worldClockService.currentAbsoluteDay();
        long createdAt = System.currentTimeMillis();
        Building building = new Building(characterId, parcelId, buildingTypeId,
                                         builtDay, createdAt);
        return buildingRepository.save(building);
    }
}
