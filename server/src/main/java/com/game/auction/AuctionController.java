package com.game.auction;

import com.game.character.Character;
import com.game.market.TokenHelper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for the lane-auction bidding system.
 *
 * <p>All endpoints require a valid {@code Authorization: Bearer <token>} header;
 * character ownership is verified via {@link TokenHelper}. Security is handled
 * inline — {@code /api/auction/**} is already listed in SecurityConfig's
 * permitAll matcher per the SOCIAL-FINANCE-SPEC.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>POST /api/auction/create          — seller creates a timed auction</li>
 *   <li>GET  /api/auction/open            — all OPEN auctions (lazy settle first)</li>
 *   <li>POST /api/auction/{id}/bid        — bidder places a bid</li>
 *   <li>POST /api/auction/{id}/settle     — explicit settlement after expiry</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/auction")
public class AuctionController {

    private final TokenHelper    tokenHelper;
    private final AuctionService auctionService;

    public AuctionController(TokenHelper tokenHelper,
                             AuctionService auctionService) {
        this.tokenHelper    = tokenHelper;
        this.auctionService = auctionService;
    }

    // ── POST /api/auction/create ──────────────────────────────────────────────

    /**
     * Seller creates a timed auction.
     *
     * <p>Bearer token → accountId; verifies that {@code characterId} belongs to
     * that account. Then delegates guard-and-create to {@link AuctionService}.
     *
     * @return 200 with the OPEN {@link Auction}
     */
    @PostMapping("/create")
    public ResponseEntity<Auction> create(
            @Valid @RequestBody CreateAuctionRequest req,
            HttpServletRequest request) {

        tokenHelper.requireOwnedCharacter(request, req.getCharacterId());

        Auction auction = auctionService.create(
                req.getCharacterId(),
                req.getKind(),
                req.getRefId(),
                req.getQuantity(),
                req.getStartBidGel(),
                req.getDurationDays());

        return ResponseEntity.ok(auction);
    }

    // ── GET /api/auction/open ─────────────────────────────────────────────────

    /**
     * Returns all OPEN auctions after lazily settling any whose endDay has passed.
     * Any authenticated user may browse.
     *
     * @return 200 with a list of OPEN {@link Auction} records
     */
    @GetMapping("/open")
    public ResponseEntity<List<Auction>> listOpen(HttpServletRequest request) {
        tokenHelper.requireAccountId(request);
        return ResponseEntity.ok(auctionService.listOpen());
    }

    // ── POST /api/auction/{id}/bid ────────────────────────────────────────────

    /**
     * Bidder places a bid on an OPEN auction.
     *
     * <p>The pre-loaded {@link Character} supplies the bidder's current wallet
     * balance for the affordability check (GEL is NOT charged at bid time).
     *
     * @return 200 with the updated {@link Auction}; 400 on any guard failure
     */
    @PostMapping("/{id}/bid")
    public ResponseEntity<Auction> bid(
            @PathVariable Long id,
            @Valid @RequestBody BidRequest req,
            HttpServletRequest request) {

        Character bidder = tokenHelper.requireOwnedCharacter(request, req.getCharacterId());

        Auction auction = auctionService.bid(
                id,
                req.getCharacterId(),
                req.getAmountGel(),
                bidder.getWalletGel());

        return ResponseEntity.ok(auction);
    }

    // ── POST /api/auction/{id}/settle ─────────────────────────────────────────

    /**
     * Explicit settlement of an expired auction.
     *
     * <p>Idempotent: if the auction is already SETTLED the current state is
     * returned without error. If the auction is still live (currentDay &lt; endDay)
     * a 400 is returned.
     *
     * @return 200 with the SETTLED {@link Auction}
     */
    @PostMapping("/{id}/settle")
    public ResponseEntity<Auction> settle(
            @PathVariable Long id,
            @Valid @RequestBody SettleRequest req,
            HttpServletRequest request) {

        tokenHelper.requireOwnedCharacter(request, req.getCharacterId());

        Auction auction = auctionService.settle(id, req.getCharacterId());
        return ResponseEntity.ok(auction);
    }
}
