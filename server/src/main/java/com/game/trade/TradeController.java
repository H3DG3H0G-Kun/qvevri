package com.game.trade;

import com.game.character.Character;
import com.game.character.CharacterService;
import com.game.exception.ApiException;
import com.game.market.TokenHelper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for the player-to-player trade marketplace.
 *
 * <p>All endpoints require a valid {@code Authorization: Bearer <token>} header;
 * character ownership is verified via {@link TokenHelper}. Security is handled
 * inline (no Spring Security rules needed — {@code /api/trade/**} is permitAll
 * in SecurityConfig as of the BACKEND-DEPTH-SPEC requirement).
 *
 * <p>Endpoints:
 * <ul>
 *   <li>POST /api/trade/offers                    — seller creates an offer</li>
 *   <li>GET  /api/trade/offers                    — all OPEN offers (marketplace)</li>
 *   <li>GET  /api/trade/offers/mine?characterId=… — seller's own offers</li>
 *   <li>POST /api/trade/offers/{offerId}/accept   — buyer accepts atomically</li>
 *   <li>POST /api/trade/offers/{offerId}/cancel   — seller cancels</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/trade")
public class TradeController {

    private final TokenHelper    tokenHelper;
    private final CharacterService characterService;
    private final TradeService   tradeService;

    public TradeController(TokenHelper tokenHelper,
                           CharacterService characterService,
                           TradeService tradeService) {
        this.tokenHelper      = tokenHelper;
        this.characterService = characterService;
        this.tradeService     = tradeService;
    }

    // ── POST /api/trade/offers ────────────────────────────────────────────────

    /**
     * Seller lists an asset for sale.
     *
     * <p>Bearer token → accountId; verifies that {@code characterId} belongs to
     * that account. Then delegates guard-and-create to {@link TradeService}.
     *
     * @return 200 with the OPEN {@link TradeOffer}
     */
    @PostMapping("/offers")
    public ResponseEntity<TradeOffer> createOffer(
            @Valid @RequestBody CreateOfferRequest req,
            HttpServletRequest request) {

        tokenHelper.requireOwnedCharacter(request, req.getCharacterId());

        TradeOffer offer = tradeService.createOffer(
                req.getCharacterId(),
                req.getKind(),
                req.getReference(),
                req.getQuantity(),
                req.getPriceGel());

        return ResponseEntity.ok(offer);
    }

    // ── GET /api/trade/offers ─────────────────────────────────────────────────

    /**
     * Returns all OPEN trade offers (the marketplace).
     * Any authenticated user may browse.
     */
    @GetMapping("/offers")
    public ResponseEntity<List<TradeOffer>> listOpenOffers(HttpServletRequest request) {
        tokenHelper.requireAccountId(request);
        return ResponseEntity.ok(tradeService.listOpenOffers());
    }

    // ── GET /api/trade/offers/mine ────────────────────────────────────────────

    /**
     * Returns all offers (any status) created by the given seller character.
     *
     * @param characterId the seller's character id (query param)
     */
    @GetMapping("/offers/mine")
    public ResponseEntity<List<TradeOffer>> listMyOffers(
            @RequestParam Long characterId,
            HttpServletRequest request) {

        tokenHelper.requireOwnedCharacter(request, characterId);
        return ResponseEntity.ok(tradeService.listMyOffers(characterId));
    }

    // ── POST /api/trade/offers/{offerId}/accept ───────────────────────────────

    /**
     * Buyer accepts an open offer atomically.
     *
     * <p>Verifies character ownership before entering the transactional accept
     * path. The pre-loaded {@link Character} is passed to the service so it can
     * read back the post-transaction wallet without needing a second DB lookup by
     * a different key.
     *
     * @return 200 with {@link AcceptOfferResponse} containing the settled offer
     *         and the buyer's new wallet balance
     */
    @PostMapping("/offers/{offerId}/accept")
    public ResponseEntity<AcceptOfferResponse> acceptOffer(
            @PathVariable Long offerId,
            @Valid @RequestBody AcceptOfferRequest req,
            HttpServletRequest request) {

        Character buyer = tokenHelper.requireOwnedCharacter(request, req.getCharacterId());
        AcceptOfferResponse response = tradeService.accept(offerId, buyer);
        return ResponseEntity.ok(response);
    }

    // ── POST /api/trade/offers/{offerId}/cancel ───────────────────────────────

    /**
     * Seller cancels an OPEN offer.
     * Releases the CELLAR_ITEM escrow if applicable.
     *
     * @return 200 with the CANCELLED {@link TradeOffer}
     */
    @PostMapping("/offers/{offerId}/cancel")
    public ResponseEntity<TradeOffer> cancelOffer(
            @PathVariable Long offerId,
            @Valid @RequestBody CancelOfferRequest req,
            HttpServletRequest request) {

        tokenHelper.requireOwnedCharacter(request, req.getCharacterId());
        TradeOffer cancelled = tradeService.cancel(offerId, req.getCharacterId());
        return ResponseEntity.ok(cancelled);
    }
}
