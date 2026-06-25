package com.game.goods;

import com.game.bonus.BonusService;
import com.game.bonus.BonusTypes;
import com.game.character.Character;
import com.game.character.CharacterRepository;
import com.game.character.CharacterService;
import com.game.exception.ApiException;
import com.game.market.TokenHelper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;

/**
 * NPC bazaar endpoints — buy from / sell back to the NPC vendor.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>POST /api/shop/buy  {characterId, goodTypeId, quantity}
 *       → {@link BuyResponse} (ownedGood + walletGel)</li>
 *   <li>POST /api/shop/sell {characterId, ownedGoodId, quantity}
 *       → {@link SellResponse} (walletGel)</li>
 * </ul>
 *
 * <p>Both endpoints require a valid bearer token and verify character
 * ownership inline via {@link TokenHelper}.
 *
 * <p>Security config already permits /api/shop/** at the filter level.
 */
@RestController
@RequestMapping("/api/shop")
public class ShopController {

    /** NPC buy-back fraction (50 % of basePrice). */
    private static final double SELL_FRACTION = 0.5;

    private final TokenHelper        tokenHelper;
    private final CharacterService   characterService;
    private final CharacterRepository characterRepository;
    private final GoodsService       goodsService;
    private final OwnedGoodRepository ownedGoodRepository;
    private final BonusService       bonusService;

    public ShopController(TokenHelper tokenHelper,
                          CharacterService characterService,
                          CharacterRepository characterRepository,
                          GoodsService goodsService,
                          OwnedGoodRepository ownedGoodRepository,
                          BonusService bonusService) {
        this.tokenHelper          = tokenHelper;
        this.characterService     = characterService;
        this.characterRepository  = characterRepository;
        this.goodsService         = goodsService;
        this.ownedGoodRepository  = ownedGoodRepository;
        this.bonusService         = bonusService;
    }

    // ── POST /api/shop/buy ────────────────────────────────────────────────────

    /**
     * Purchases {@code quantity} units of a catalog good from the NPC vendor.
     *
     * <p>Price = {@code basePrice × quantity}. If the character's wallet is
     * insufficient an HTTP 400 / INSUFFICIENT_FUNDS error is returned.
     * The good is granted (stacked if already owned) and the updated
     * {@link OwnedGood} is returned together with the new wallet balance.
     */
    @PostMapping("/buy")
    public ResponseEntity<BuyResponse> buy(
            @RequestBody BuyRequest req,
            HttpServletRequest request) {

        // Auth + ownership
        tokenHelper.requireOwnedCharacter(request, req.getCharacterId());

        // Validate quantity
        if (req.getQuantity() <= 0) {
            throw ApiException.badRequest("quantity must be > 0");
        }

        // Validate good exists
        GoodType goodType = GoodsCatalog.find(req.getGoodTypeId());
        if (goodType == null) {
            throw ApiException.badRequest("Unknown goodTypeId: " + req.getGoodTypeId());
        }

        // INTEGRATION: a Negociant (or anyone with BUY_DISCOUNT, e.g. the haggler skill)
        // buys cheaper. 0.0 for a default character, so the base price is unchanged.
        double discount = bonusService.total(req.getCharacterId(), BonusTypes.BUY_DISCOUNT);
        double totalPrice = goodType.getBasePrice() * req.getQuantity() * (1.0 - discount);

        // Debit wallet — CharacterService.adjustWallet throws INSUFFICIENT_FUNDS (402)
        // wrapped as ApiException if the wallet would go negative.
        // We re-throw as BAD_REQUEST to match the spec's "400 INSUFFICIENT_FUNDS".
        try {
            characterService.adjustWallet(req.getCharacterId(), -totalPrice);
        } catch (ApiException e) {
            if ("INSUFFICIENT_FUNDS".equals(e.getCode())) {
                throw ApiException.badRequest(e.getMessage());
            }
            throw e;
        }

        // Grant the good (stacks if already owned)
        OwnedGood og = goodsService.grant(req.getCharacterId(),
                req.getGoodTypeId(), req.getQuantity());

        // Read new wallet balance
        double newWallet = characterRepository.findById(req.getCharacterId())
                .map(Character::getWalletGel)
                .orElseThrow(() -> ApiException.badRequest(
                        "Character not found: " + req.getCharacterId()));

        return ResponseEntity.ok(new BuyResponse(og, newWallet));
    }

    // ── POST /api/shop/sell ───────────────────────────────────────────────────

    /**
     * Sells back {@code quantity} units of an owned good to the NPC vendor.
     *
     * <p>Credit = {@code 0.5 × basePrice × quantity}. The OwnedGood row is
     * decremented; if quantity reaches zero the row is deleted. Returns the
     * character's new wallet balance.
     */
    @PostMapping("/sell")
    public ResponseEntity<SellResponse> sell(
            @RequestBody SellRequest req,
            HttpServletRequest request) {

        // Auth + ownership
        tokenHelper.requireOwnedCharacter(request, req.getCharacterId());

        // Validate quantity
        if (req.getQuantity() <= 0) {
            throw ApiException.badRequest("quantity must be > 0");
        }

        // Load and verify OwnedGood ownership
        OwnedGood og = ownedGoodRepository.findById(req.getOwnedGoodId())
                .orElseThrow(() -> ApiException.badRequest(
                        "OwnedGood not found: " + req.getOwnedGoodId()));

        if (!og.getCharacterId().equals(req.getCharacterId())) {
            throw ApiException.badRequest(
                    "OwnedGood " + req.getOwnedGoodId()
                            + " does not belong to character " + req.getCharacterId());
        }

        // Resolve catalog price
        GoodType goodType = GoodsCatalog.find(og.getGoodTypeId());
        if (goodType == null) {
            throw ApiException.badRequest(
                    "Catalog entry missing for goodTypeId: " + og.getGoodTypeId());
        }

        // Decrement/remove the good first (validates quantity)
        goodsService.decrement(req.getOwnedGoodId(), req.getQuantity());

        // Credit wallet
        double credit = SELL_FRACTION * goodType.getBasePrice() * req.getQuantity();
        characterService.adjustWallet(req.getCharacterId(), credit);

        // Read new wallet balance
        double newWallet = characterRepository.findById(req.getCharacterId())
                .map(Character::getWalletGel)
                .orElseThrow(() -> ApiException.badRequest(
                        "Character not found: " + req.getCharacterId()));

        return ResponseEntity.ok(new SellResponse(newWallet));
    }
}
