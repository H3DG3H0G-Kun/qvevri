package com.game.profession;

import com.game.character.Character;
import com.game.character.CharacterService;
import com.game.exception.ApiException;
import com.game.goods.GoodsService;
import com.game.goods.OwnedGood;
import com.game.goods.OwnedGoodRepository;
import com.game.market.CellarItem;
import com.game.market.CellarItemRepository;
import com.game.world.CareerType;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Core profession service.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Lazy, idempotent starter-kit grant (tracked in {@link ProfessionKitClaim}).</li>
 *   <li>Cooper craft: consume INPUT goods, produce VESSEL {@link OwnedGood}.</li>
 *   <li>Lab grade: ENOLOGIST scores a {@link CellarItem} and persists a {@link WineGrade}.</li>
 * </ul>
 *
 * <h3>Adaptation notes for Lane G's GoodsService</h3>
 * <p>Lane G's {@code GoodsService} exposes:
 * <ul>
 *   <li>{@code grant(long characterId, String goodTypeId, double qty)} — matches our expectation.</li>
 *   <li>{@code decrement(long ownedGoodId, double qty)} — takes the PK of the OwnedGood row,
 *       not (characterId, goodTypeId). We implement the "find then decrement-by-id" pattern
 *       by calling {@code OwnedGoodRepository.findByCharacterIdAndGoodTypeId} directly
 *       (also in {@code com.game.goods} — referenced, not modified).</li>
 * </ul>
 * If Lane G later adds {@code consume(long characterId, String goodTypeId, double qty)},
 * replace the two-step call sites in {@link #consumeGood} with the single-method form.
 */
@Service
@Transactional
public class ProfessionService {

    private static final double CERTIFICATION_THRESHOLD = 85.0;

    private final ProfessionKitClaimRepository kitClaimRepo;
    private final WineGradeRepository           wineGradeRepo;
    private final GoodsService                  goodsService;
    private final OwnedGoodRepository           ownedGoodRepo;
    private final CharacterService              characterService;
    private final CellarItemRepository          cellarItemRepo;

    public ProfessionService(
            ProfessionKitClaimRepository kitClaimRepo,
            WineGradeRepository wineGradeRepo,
            GoodsService goodsService,
            OwnedGoodRepository ownedGoodRepo,
            CharacterService characterService,
            CellarItemRepository cellarItemRepo) {
        this.kitClaimRepo     = kitClaimRepo;
        this.wineGradeRepo    = wineGradeRepo;
        this.goodsService     = goodsService;
        this.ownedGoodRepo    = ownedGoodRepo;
        this.characterService = characterService;
        this.cellarItemRepo   = cellarItemRepo;
    }

    // ── Capabilities catalog ─────────────────────────────────────────────────

    /** Returns the static career-capability map. */
    @Transactional(readOnly = true)
    public Map<CareerType, CareerCapability.Capability> capabilities() {
        return CareerCapability.catalog();
    }

    // ── Starter-kit grant (idempotent) ────────────────────────────────────────

    /**
     * Grants the one-time starter kit for a character's career.
     *
     * <p>Idempotent: a second call returns the existing {@link ProfessionKitClaim}
     * without modifying goods or wallet.
     *
     * @param character the verified, owned character
     * @return the kit claim record (existing or newly created)
     */
    public ProfessionKitClaim claimStarterKit(Character character) {
        Long characterId = character.getId();

        // Idempotency check
        return kitClaimRepo.findByCharacterId(characterId)
                .orElseGet(() -> grantKit(character));
    }

    private ProfessionKitClaim grantKit(Character character) {
        Long characterId = character.getId();
        CareerType career = character.getCareerType();
        CareerCapability.Capability cap = CareerCapability.of(career);

        // Grant goods via GoodsService.grant (Lane G's public API)
        for (CareerCapability.KitItem item : cap.starterGoods()) {
            goodsService.grant(characterId, item.goodTypeId(), item.qty());
        }

        // Grant GEL bonus (skip if 0)
        if (cap.starterGel() > 0.0) {
            characterService.adjustWallet(characterId, cap.starterGel());
        }

        ProfessionKitClaim claim = new ProfessionKitClaim(characterId, career.name());
        return kitClaimRepo.save(claim);
    }

    // ── Cooper craft ──────────────────────────────────────────────────────────

    /**
     * Crafts a vessel using the Cooper recipe.
     *
     * <p>Career gate: character must be {@link CareerType#COOPER}.
     *
     * @param character the verified, owned character
     * @param recipeId  the recipe to execute
     * @return the produced {@link OwnedGood} vessel
     * @throws ApiException 403 if not COOPER
     * @throws ApiException 400 if recipeId unknown or inputs insufficient
     */
    public OwnedGood cooperCraft(Character character, String recipeId) {
        requireCareer(character, CareerType.COOPER);

        CooperRecipe.Recipe recipe = CooperRecipe.find(recipeId);
        if (recipe == null) {
            throw ApiException.badRequest("Unknown recipe: " + recipeId);
        }

        long characterId = character.getId();

        // Validate ALL inputs are available before consuming any (atomic check)
        for (CooperRecipe.Ingredient input : recipe.inputs()) {
            OwnedGood owned = ownedGoodRepo
                    .findByCharacterIdAndGoodTypeId(characterId, input.goodTypeId())
                    .orElseThrow(() -> ApiException.badRequest(
                            "Missing input good '" + input.goodTypeId()
                            + "' (need " + input.qty() + ", have 0)"));
            if (owned.getQuantity() < input.qty()) {
                throw ApiException.badRequest(
                        "Insufficient input '" + input.goodTypeId()
                        + "': need " + input.qty()
                        + ", have " + owned.getQuantity());
            }
        }

        // Consume all inputs via GoodsService.decrement(ownedGoodId, qty)
        for (CooperRecipe.Ingredient input : recipe.inputs()) {
            consumeGood(characterId, input.goodTypeId(), input.qty());
        }

        // Grant the produced vessel via GoodsService.grant
        CooperRecipe.Ingredient output = recipe.output();
        return goodsService.grant(characterId, output.goodTypeId(), output.qty());
    }

    // ── Lab grade ─────────────────────────────────────────────────────────────

    /**
     * Issues an enologist lab grade for a cellar item.
     *
     * <p>Career gate: character must be {@link CareerType#ENOLOGIST}.
     *
     * <p>Score formula (deterministic, no RNG):
     * <ul>
     *   <li>Base = {@code cellarItem.quality} clamped to [0..100].</li>
     *   <li>Appellation bonus = +5 if {@code cellarItem.appellationOk}.</li>
     *   <li>Certified = {@code score >= 85}.</li>
     * </ul>
     *
     * @param character    the verified, owned ENOLOGIST character
     * @param cellarItemId the cellar item to grade
     * @return the persisted {@link WineGrade}
     * @throws ApiException 403 if not ENOLOGIST
     * @throws ApiException 400 if the cellar item does not exist
     */
    public WineGrade labGrade(Character character, Long cellarItemId) {
        requireCareer(character, CareerType.ENOLOGIST);

        CellarItem item = cellarItemRepo.findById(cellarItemId)
                .orElseThrow(() -> ApiException.badRequest(
                        "CellarItem " + cellarItemId + " not found"));

        double base  = Math.min(100.0, Math.max(0.0, item.getQuality()));
        double bonus = item.isAppellationOk() ? 5.0 : 0.0;
        double score = base + bonus;

        boolean certified = score >= CERTIFICATION_THRESHOLD;

        WineGrade grade = new WineGrade(cellarItemId, character.getId(), score, certified);
        return wineGradeRepo.save(grade);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Consumes {@code qty} units of a good identified by (characterId, goodTypeId).
     *
     * <p>Adapts to Lane G's {@code GoodsService.decrement(ownedGoodId, qty)} by first
     * resolving the {@link OwnedGood} PK via {@code OwnedGoodRepository}.
     * Assumption: the quantity check in {@link #cooperCraft} has already validated
     * sufficient stock, so this should not throw unless concurrent depletion occurs.
     */
    private void consumeGood(long characterId, String goodTypeId, double qty) {
        OwnedGood og = ownedGoodRepo
                .findByCharacterIdAndGoodTypeId(characterId, goodTypeId)
                .orElseThrow(() -> ApiException.badRequest(
                        "Good '" + goodTypeId + "' not found for character " + characterId));
        goodsService.decrement(og.getId(), qty);
    }

    private static void requireCareer(Character character, CareerType required) {
        if (character.getCareerType() != required) {
            throw ApiException.forbidden(
                    "This action requires career " + required.name()
                    + " (character is " + character.getCareerType().name() + ")");
        }
    }
}
