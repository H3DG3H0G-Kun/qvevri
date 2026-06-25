package com.game.auction;

import com.game.character.CharacterService;
import com.game.exception.ApiException;
import com.game.goods.GoodsService;
import com.game.goods.OwnedGood;
import com.game.goods.OwnedGoodRepository;
import com.game.market.CellarItem;
import com.game.market.CellarItemRepository;
import com.game.world.clock.WorldClockService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Business logic for the lane-auction timed bidding system.
 *
 * <p>All public mutating methods are {@code @Transactional} so that
 * escrow, bid, and settlement paths are fully atomic.
 *
 * <p>Settlement is LAZY: it is triggered on reads ({@link #listOpen()}) and
 * on explicit calls ({@link #settle(Long, Long)}). No scheduler is used —
 * resolution is deterministic against the world clock sim-day.
 *
 * <p>Bid wallet check: the controller pre-loads the bidder's wallet (via
 * {@code requireOwnedCharacter} → {@code Character.getWalletGel()}) and
 * passes it in. GEL is NOT charged at bid time — only at settlement.
 */
@Service
@Transactional
public class AuctionService {

    static final String KIND_GOODS       = "GOODS";
    static final String KIND_CELLAR_ITEM = "CELLAR_ITEM";

    static final String STATUS_OPEN      = "OPEN";
    static final String STATUS_SETTLED   = "SETTLED";
    static final String STATUS_CANCELLED = "CANCELLED";

    private final AuctionRepository    auctionRepo;
    private final CharacterService     characterService;
    private final CellarItemRepository cellarItemRepo;
    private final OwnedGoodRepository  ownedGoodRepo;
    private final GoodsService         goodsService;
    private final WorldClockService    clockService;

    public AuctionService(AuctionRepository auctionRepo,
                          CharacterService characterService,
                          CellarItemRepository cellarItemRepo,
                          OwnedGoodRepository ownedGoodRepo,
                          GoodsService goodsService,
                          WorldClockService clockService) {
        this.auctionRepo      = auctionRepo;
        this.characterService = characterService;
        this.cellarItemRepo   = cellarItemRepo;
        this.ownedGoodRepo    = ownedGoodRepo;
        this.goodsService     = goodsService;
        this.clockService     = clockService;
    }

    // ── Create ────────────────────────────────────────────────────────────────

    /**
     * Creates an OPEN auction on behalf of the seller character.
     *
     * <p>Guards:
     * <ul>
     *   <li>kind must be "GOODS" or "CELLAR_ITEM".</li>
     *   <li>CELLAR_ITEM: the item must be owned by the seller and not already
     *       escrowed; sets {@code escrowed=true} to reserve it.</li>
     *   <li>GOODS: seller must own at least {@code quantity} units; decrements
     *       the stack immediately to hold the goods in escrow.</li>
     *   <li>durationDays must be &ge; 1.</li>
     *   <li>startBidGel must be &gt; 0.</li>
     * </ul>
     *
     * @return the newly created OPEN {@link Auction}
     */
    public Auction create(Long sellerCharacterId, String kind, String refId,
                          double quantity, double startBidGel, int durationDays) {
        if (durationDays < 1) {
            throw ApiException.badRequest("durationDays must be >= 1");
        }
        if (startBidGel <= 0) {
            throw ApiException.badRequest("startBidGel must be > 0");
        }

        switch (kind) {
            case KIND_CELLAR_ITEM -> reserveCellarItem(sellerCharacterId, refId);
            case KIND_GOODS       -> reserveGoods(sellerCharacterId, refId, quantity);
            default               -> throw ApiException.badRequest(
                    "Unknown auction kind '" + kind + "'; expected GOODS or CELLAR_ITEM");
        }

        long endDay = (long) clockService.currentAbsoluteDay() + durationDays;
        Auction auction = new Auction(sellerCharacterId, kind, refId, quantity, startBidGel, endDay);
        return auctionRepo.save(auction);
    }

    // ── List open (with lazy auto-settle) ─────────────────────────────────────

    /**
     * Returns all OPEN auctions, first lazily settling any whose {@code endDay}
     * has passed (currentDay &ge; endDay).
     *
     * @return list of auctions that are OPEN after the settlement pass
     */
    @Transactional
    public List<Auction> listOpen() {
        int currentDay = clockService.currentAbsoluteDay();

        // Lazy settle: any OPEN auction past its endDay
        List<Auction> allOpen = auctionRepo.findByAuctionStatus(STATUS_OPEN);
        for (Auction a : allOpen) {
            if (currentDay >= a.getEndDay()) {
                settleInternal(a);
            }
        }

        // Return whatever remains OPEN
        return auctionRepo.findByAuctionStatus(STATUS_OPEN);
    }

    // ── Bid ───────────────────────────────────────────────────────────────────

    /**
     * Places a bid on behalf of {@code bidderCharacterId}.
     *
     * <p>The controller supplies the bidder's current wallet balance (read
     * from the pre-loaded {@link com.game.character.Character}) so we can
     * guard affordability without a separate DB call.
     *
     * <p>GEL is NOT charged at bid time; the winner is charged at settlement.
     *
     * <p>Guards (400 on failure):
     * <ul>
     *   <li>Auction must be OPEN.</li>
     *   <li>currentDay &lt; endDay (auction not expired).</li>
     *   <li>Bidder must not be the seller.</li>
     *   <li>amountGel &ge; startBidGel (first bid) or &gt; currentBidGel (subsequent).</li>
     *   <li>bidderWalletGel &ge; amountGel (can afford at bid time).</li>
     * </ul>
     *
     * @param auctionId         the auction to bid on
     * @param bidderCharacterId the bidding character
     * @param amountGel         the bid amount in GEL
     * @param bidderWalletGel   the bidder's current wallet (pre-loaded by controller)
     * @return the updated {@link Auction}
     */
    public Auction bid(Long auctionId, Long bidderCharacterId,
                       double amountGel, double bidderWalletGel) {

        Auction auction = requireAuction(auctionId);

        if (!STATUS_OPEN.equals(auction.getAuctionStatus())) {
            throw ApiException.badRequest(
                    "Auction " + auctionId + " is not OPEN (status: "
                            + auction.getAuctionStatus() + ")");
        }

        int currentDay = clockService.currentAbsoluteDay();
        if (currentDay >= auction.getEndDay()) {
            throw ApiException.badRequest(
                    "Auction " + auctionId + " has already expired (endDay="
                            + auction.getEndDay() + ", currentDay=" + currentDay + ")");
        }

        if (auction.getSellerCharacterId().equals(bidderCharacterId)) {
            throw ApiException.badRequest("Cannot bid on your own auction");
        }

        if (auction.getCurrentBidGel() == null) {
            // First bid: must be >= startBidGel
            if (amountGel < auction.getStartBidGel()) {
                throw ApiException.badRequest(
                        "Bid " + amountGel + " GEL is below the starting bid of "
                                + auction.getStartBidGel() + " GEL");
            }
        } else {
            // Subsequent bid: must strictly exceed current high bid
            if (amountGel <= auction.getCurrentBidGel()) {
                throw ApiException.badRequest(
                        "Bid " + amountGel + " GEL must exceed the current high bid of "
                                + auction.getCurrentBidGel() + " GEL");
            }
        }

        // Affordability check (not yet charged)
        if (bidderWalletGel < amountGel) {
            throw ApiException.badRequest(
                    "Insufficient funds: wallet " + bidderWalletGel
                            + " GEL, bid requires " + amountGel + " GEL");
        }

        auction.setCurrentBidGel(amountGel);
        auction.setHighBidderCharacterId(bidderCharacterId);
        return auctionRepo.save(auction);
    }

    // ── Settle (explicit endpoint) ────────────────────────────────────────────

    /**
     * Explicit settle: any authenticated character may trigger settlement once
     * {@code currentDay &ge; endDay}. Idempotent — already SETTLED auctions are
     * returned unchanged.
     *
     * @param auctionId   the auction to settle
     * @param characterId the requesting character (auth already verified by controller)
     * @return the (now SETTLED) {@link Auction}
     */
    public Auction settle(Long auctionId, Long characterId) {
        Auction auction = requireAuction(auctionId);

        // Idempotency: already settled → no-op
        if (STATUS_SETTLED.equals(auction.getAuctionStatus())) {
            return auction;
        }

        if (!STATUS_OPEN.equals(auction.getAuctionStatus())) {
            throw ApiException.badRequest(
                    "Auction " + auctionId + " cannot be settled (status: "
                            + auction.getAuctionStatus() + ")");
        }

        int currentDay = clockService.currentAbsoluteDay();
        if (currentDay < auction.getEndDay()) {
            throw ApiException.badRequest(
                    "Auction " + auctionId + " has not expired yet (endDay="
                            + auction.getEndDay() + ", currentDay=" + currentDay + ")");
        }

        return settleInternal(auction);
    }

    // ── Core settlement logic ─────────────────────────────────────────────────

    /**
     * Performs the actual settlement. Called both lazily (from {@link #listOpen()})
     * and explicitly (from {@link #settle(Long, Long)}).
     *
     * <p>Winner path (highBidder present):
     * <ol>
     *   <li>Charge winner: {@code adjustWallet(highBidder, -currentBid)}.</li>
     *   <li>Pay seller:   {@code adjustWallet(seller, +currentBid)}.</li>
     *   <li>Transfer asset:
     *       CELLAR_ITEM → setCharacterId(winner) + clear escrow;
     *       GOODS       → grant to winner (goods were decremented at create time).</li>
     * </ol>
     *
     * <p>No-bid path: return the reserved asset to the seller
     * (CELLAR_ITEM → clear escrow; GOODS → grant back to seller).
     *
     * <p>Mark SETTLED.
     */
    @Transactional
    Auction settleInternal(Auction auction) {
        if (STATUS_SETTLED.equals(auction.getAuctionStatus())) {
            return auction; // idempotent guard
        }

        Long   highBidder = auction.getHighBidderCharacterId();
        Double winningBid = auction.getCurrentBidGel();

        if (highBidder != null && winningBid != null) {
            // ── Winner path ──────────────────────────────────────────────────
            try {
                characterService.adjustWallet(highBidder, -winningBid);
            } catch (ApiException ex) {
                if ("INSUFFICIENT_FUNDS".equals(ex.getCode())) {
                    throw ApiException.badRequest(
                            "Winner (character " + highBidder
                                    + ") has insufficient funds (" + winningBid
                                    + " GEL) at settlement time");
                }
                throw ex;
            }
            characterService.adjustWallet(auction.getSellerCharacterId(), +winningBid);

            switch (auction.getKind()) {
                case KIND_CELLAR_ITEM -> transferCellarItemToWinner(auction, highBidder);
                case KIND_GOODS       -> goodsService.grant(highBidder,
                                                            auction.getRefId(),
                                                            auction.getQuantity());
                default               -> throw ApiException.badRequest(
                        "Unknown kind in auction: " + auction.getKind());
            }
        } else {
            // ── No-bid path: return reserved item to seller ──────────────────
            switch (auction.getKind()) {
                case KIND_CELLAR_ITEM -> releaseEscrow(auction.getRefId());
                case KIND_GOODS       -> goodsService.grant(auction.getSellerCharacterId(),
                                                            auction.getRefId(),
                                                            auction.getQuantity());
                default               -> throw ApiException.badRequest(
                        "Unknown kind in auction: " + auction.getKind());
            }
        }

        auction.setAuctionStatus(STATUS_SETTLED);
        return auctionRepo.save(auction);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private Auction requireAuction(Long auctionId) {
        return auctionRepo.findById(auctionId)
                .orElseThrow(() -> ApiException.notFound("Auction " + auctionId + " not found"));
    }

    /**
     * Verifies CELLAR_ITEM ownership + non-escrowed state; sets escrowed=true.
     */
    private void reserveCellarItem(Long sellerCharacterId, String refId) {
        long cellarItemId = parseCellarItemId(refId);
        CellarItem item = cellarItemRepo.findByIdAndCharacterId(cellarItemId, sellerCharacterId)
                .orElseThrow(() -> ApiException.badRequest(
                        "CellarItem " + cellarItemId
                                + " not found or not owned by character " + sellerCharacterId));
        if (item.isEscrowed()) {
            throw ApiException.badRequest(
                    "CellarItem " + cellarItemId
                            + " is already reserved under an active listing or auction");
        }
        item.setEscrowed(true);
        cellarItemRepo.save(item);
    }

    /**
     * Verifies GOODS ownership + sufficient stock; decrements the stack to hold.
     */
    private void reserveGoods(Long sellerCharacterId, String goodTypeId, double quantity) {
        OwnedGood owned = ownedGoodRepo
                .findByCharacterIdAndGoodTypeId(sellerCharacterId, goodTypeId)
                .orElseThrow(() -> ApiException.badRequest(
                        "Character " + sellerCharacterId
                                + " does not own any '" + goodTypeId + "'"));
        if (owned.getQuantity() < quantity) {
            throw ApiException.badRequest(
                    "Insufficient stock: character " + sellerCharacterId
                            + " owns " + owned.getQuantity() + " of '" + goodTypeId
                            + "' but auction requires " + quantity);
        }
        goodsService.decrement(owned.getId(), quantity);
    }

    /**
     * Reassigns the CELLAR_ITEM to the winner and clears escrow.
     */
    private void transferCellarItemToWinner(Auction auction, Long winnerId) {
        long cellarItemId = parseCellarItemId(auction.getRefId());
        CellarItem item = cellarItemRepo.findById(cellarItemId)
                .orElseThrow(() -> ApiException.badRequest(
                        "CellarItem " + cellarItemId + " not found during settlement"));
        item.setCharacterId(winnerId);
        item.setEscrowed(false);
        cellarItemRepo.save(item);
    }

    /**
     * Clears escrow on a CELLAR_ITEM (no-bid return to seller).
     */
    private void releaseEscrow(String refId) {
        try {
            long cellarItemId = parseCellarItemId(refId);
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

    private static long parseCellarItemId(String refId) {
        try {
            return Long.parseLong(refId);
        } catch (NumberFormatException e) {
            throw ApiException.badRequest(
                    "Invalid cellarItemId reference '" + refId
                            + "'; expected a numeric string id");
        }
    }
}
