package com.game.trade;

import com.game.character.Character;
import com.game.character.CharacterService;
import com.game.exception.ApiException;
import com.game.goods.GoodsService;
import com.game.goods.OwnedGood;
import com.game.goods.OwnedGoodRepository;
import com.game.market.CellarItem;
import com.game.market.CellarItemRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Business logic for the player-to-player trade lane.
 *
 * <p>All public mutating methods are {@code @Transactional} so that the accept
 * path (wallet debit + item transfer + status update) is fully atomic.
 */
@Service
@Transactional
public class TradeService {

    static final String KIND_GOODS       = "GOODS";
    static final String KIND_CELLAR_ITEM = "CELLAR_ITEM";

    private final TradeOfferRepository  offerRepo;
    private final CharacterService      characterService;
    private final CellarItemRepository  cellarItemRepo;
    private final OwnedGoodRepository   ownedGoodRepo;
    private final GoodsService          goodsService;

    public TradeService(TradeOfferRepository offerRepo,
                        CharacterService characterService,
                        CellarItemRepository cellarItemRepo,
                        OwnedGoodRepository ownedGoodRepo,
                        GoodsService goodsService) {
        this.offerRepo        = offerRepo;
        this.characterService = characterService;
        this.cellarItemRepo   = cellarItemRepo;
        this.ownedGoodRepo    = ownedGoodRepo;
        this.goodsService     = goodsService;
    }

    // ── Create ────────────────────────────────────────────────────────────────

    /**
     * Creates an OPEN trade offer on behalf of the seller character.
     *
     * <p>Guards:
     * <ul>
     *   <li>kind must be "GOODS" or "CELLAR_ITEM".</li>
     *   <li>CELLAR_ITEM: the cellar item must be owned by the seller and not
     *       already escrowed.</li>
     *   <li>GOODS: the seller must own at least {@code quantity} units of the
     *       goodTypeId at offer-creation time.</li>
     * </ul>
     *
     * <p>Side-effect for CELLAR_ITEM: sets {@code CellarItem.escrowed = true}
     * to reserve the item against double-listing.
     */
    public TradeOffer createOffer(Long sellerCharacterId, String kind,
                                  String reference, double quantity, double priceGel) {
        switch (kind) {
            case KIND_CELLAR_ITEM -> validateAndReserveCellarItem(sellerCharacterId, reference);
            case KIND_GOODS       -> validateGoodsStock(sellerCharacterId, reference, quantity);
            default               -> throw ApiException.badRequest(
                    "Unknown trade kind '" + kind + "'; expected GOODS or CELLAR_ITEM");
        }

        TradeOffer offer = new TradeOffer(sellerCharacterId, kind, reference, quantity, priceGel);
        return offerRepo.save(offer);
    }

    // ── List all OPEN offers ──────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<TradeOffer> listOpenOffers() {
        return offerRepo.findByStatus(TradeOfferStatus.OPEN);
    }

    // ── List seller's own offers ──────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<TradeOffer> listMyOffers(Long sellerCharacterId) {
        return offerRepo.findBySellerCharacterId(sellerCharacterId);
    }

    // ── Accept ────────────────────────────────────────────────────────────────

    /**
     * Accepts a trade offer atomically.
     *
     * <p>The controller must supply the pre-loaded {@code buyer} entity (verified
     * via {@code CharacterService.getOwned}) so we can read the post-transaction
     * wallet balance without needing a plain {@code findById} on the character.
     *
     * <p>Atomic sequence:
     * <ol>
     *   <li>Verify offer is OPEN (409 if not).</li>
     *   <li>Reject self-accept (400).</li>
     *   <li>Debit buyer wallet by priceGel (400 on insufficient funds).</li>
     *   <li>Credit seller wallet by priceGel.</li>
     *   <li>Transfer asset: CELLAR_ITEM → reassign characterId + clear escrow;
     *       GOODS → decrement seller + grant to buyer via GoodsService.</li>
     *   <li>Set buyerCharacterId + status=ACCEPTED.</li>
     * </ol>
     *
     * @param offerId  the offer to accept
     * @param buyer    pre-loaded, ownership-verified buyer character
     * @return the accepted offer and the buyer's updated wallet balance
     */
    public AcceptOfferResponse accept(Long offerId, Character buyer) {

        TradeOffer offer = requireOffer(offerId);

        if (!TradeOfferStatus.OPEN.equals(offer.getStatus())) {
            throw new ApiException("CONFLICT",
                    "Offer " + offerId + " is not OPEN (status: " + offer.getStatus() + ")",
                    HttpStatus.CONFLICT);
        }

        long buyerCharacterId = buyer.getId();

        if (offer.getSellerCharacterId().equals(buyerCharacterId)) {
            throw ApiException.badRequest("Cannot accept your own offer");
        }

        // Wallet debit — CharacterService throws INSUFFICIENT_FUNDS (HTTP 402 internally).
        // Per spec the client must receive 400, so we re-wrap it.
        try {
            characterService.adjustWallet(buyerCharacterId, -offer.getPriceGel());
        } catch (ApiException ex) {
            if ("INSUFFICIENT_FUNDS".equals(ex.getCode())) {
                throw ApiException.badRequest(
                        "Insufficient funds: wallet " + buyer.getWalletGel()
                                + " GEL, need " + offer.getPriceGel() + " GEL");
            }
            throw ex;
        }

        // Credit seller
        characterService.adjustWallet(offer.getSellerCharacterId(), +offer.getPriceGel());

        // Transfer asset
        switch (offer.getKind()) {
            case KIND_CELLAR_ITEM -> transferCellarItem(offer, buyerCharacterId);
            case KIND_GOODS       -> transferGoods(offer, buyerCharacterId);
            default               -> throw ApiException.badRequest(
                    "Unknown kind in offer: " + offer.getKind());
        }

        // Settle offer
        offer.setBuyerCharacterId(buyerCharacterId);
        offer.setStatus(TradeOfferStatus.ACCEPTED);
        offerRepo.save(offer);

        // Read back updated buyer wallet (adjustWallet already saved the entity;
        // JPA session has the fresh state — reload via getOwned using accountId).
        double newBuyerWallet = characterService.getOwned(buyerCharacterId, buyer.getAccountId())
                .map(Character::getWalletGel)
                .orElse(buyer.getWalletGel() - offer.getPriceGel()); // safe fallback

        return new AcceptOfferResponse(offer, newBuyerWallet);
    }

    // ── Cancel ────────────────────────────────────────────────────────────────

    /**
     * Cancels an OPEN trade offer (seller only).
     *
     * <p>For CELLAR_ITEM offers the {@code escrowed} flag is cleared so the
     * seller can list the item elsewhere.
     */
    public TradeOffer cancel(Long offerId, Long sellerCharacterId) {

        TradeOffer offer = requireOffer(offerId);

        if (!TradeOfferStatus.OPEN.equals(offer.getStatus())) {
            throw ApiException.badRequest(
                    "Offer " + offerId + " is not OPEN (status: " + offer.getStatus() + ")");
        }

        if (!offer.getSellerCharacterId().equals(sellerCharacterId)) {
            throw ApiException.forbidden("Only the seller can cancel an offer");
        }

        if (KIND_CELLAR_ITEM.equals(offer.getKind())) {
            releaseEscrow(offer.getReference());
        }

        offer.setStatus(TradeOfferStatus.CANCELLED);
        return offerRepo.save(offer);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private TradeOffer requireOffer(Long offerId) {
        return offerRepo.findById(offerId)
                .orElseThrow(() -> ApiException.notFound("TradeOffer " + offerId + " not found"));
    }

    /**
     * For CELLAR_ITEM create-offer: verify ownership and non-escrowed state;
     * then set escrowed=true to reserve it.
     */
    private void validateAndReserveCellarItem(Long sellerCharacterId, String reference) {
        long cellarItemId = parseCellarItemId(reference);
        CellarItem item = cellarItemRepo.findByIdAndCharacterId(cellarItemId, sellerCharacterId)
                .orElseThrow(() -> ApiException.badRequest(
                        "CellarItem " + cellarItemId
                                + " not found or not owned by character " + sellerCharacterId));
        if (item.isEscrowed()) {
            throw ApiException.badRequest(
                    "CellarItem " + cellarItemId
                            + " is already reserved under an active listing or offer");
        }
        item.setEscrowed(true);
        cellarItemRepo.save(item);
    }

    /**
     * For GOODS create-offer: verify the seller currently holds at least
     * {@code quantity} units of goodTypeId.  No deduction at this stage.
     */
    private void validateGoodsStock(Long sellerCharacterId, String goodTypeId, double quantity) {
        Optional<OwnedGood> owned = ownedGoodRepo
                .findByCharacterIdAndGoodTypeId(sellerCharacterId, goodTypeId);
        double available = owned.map(OwnedGood::getQuantity).orElse(0.0);
        if (available < quantity) {
            throw ApiException.badRequest(
                    "Insufficient stock: character " + sellerCharacterId
                            + " owns " + available + " of '" + goodTypeId
                            + "' but offer requires " + quantity);
        }
    }

    /**
     * Reassign the CellarItem to the buyer and clear the escrow.
     */
    private void transferCellarItem(TradeOffer offer, long buyerCharacterId) {
        long cellarItemId = parseCellarItemId(offer.getReference());
        CellarItem item = cellarItemRepo.findById(cellarItemId)
                .orElseThrow(() -> ApiException.badRequest(
                        "CellarItem " + cellarItemId + " not found during accept"));
        item.setCharacterId(buyerCharacterId);
        item.setEscrowed(false);
        cellarItemRepo.save(item);
    }

    /**
     * Decrement the seller's goods stack and grant the same quantity to the buyer.
     */
    private void transferGoods(TradeOffer offer, long buyerCharacterId) {
        String goodTypeId = offer.getReference();
        double qty        = offer.getQuantity();

        OwnedGood sellerStack = ownedGoodRepo
                .findByCharacterIdAndGoodTypeId(offer.getSellerCharacterId(), goodTypeId)
                .orElseThrow(() -> ApiException.badRequest(
                        "Seller no longer owns any '" + goodTypeId
                                + "' — offer " + offer.getId() + " cannot be fulfilled"));

        if (sellerStack.getQuantity() < qty) {
            throw ApiException.badRequest(
                    "Seller stock for '" + goodTypeId
                            + "' dropped below the offer quantity (" + qty
                            + "). Offer " + offer.getId() + " cannot be fulfilled.");
        }

        goodsService.decrement(sellerStack.getId(), qty);
        goodsService.grant(buyerCharacterId, goodTypeId, qty);
    }

    /**
     * Release the escrow on a CellarItem after cancel (best-effort).
     */
    private void releaseEscrow(String reference) {
        try {
            long cellarItemId = parseCellarItemId(reference);
            cellarItemRepo.findById(cellarItemId).ifPresent(item -> {
                if (item.isEscrowed()) {
                    item.setEscrowed(false);
                    cellarItemRepo.save(item);
                }
            });
        } catch (NumberFormatException ignored) {
            // Corrupt reference — nothing to release.
        }
    }

    private static long parseCellarItemId(String reference) {
        try {
            return Long.parseLong(reference);
        } catch (NumberFormatException e) {
            throw ApiException.badRequest(
                    "Invalid cellarItemId reference '" + reference
                            + "'; expected a numeric string id");
        }
    }
}
