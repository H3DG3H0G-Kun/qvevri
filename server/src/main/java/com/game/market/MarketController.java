package com.game.market;

import com.game.character.Character;
import com.game.character.CharacterService;
import com.game.econ.Item;
import com.game.econ.ItemType;
import com.game.econ.MarketContext;
import com.game.econ.WinePricer;
import com.game.exception.ApiException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * REST controller for the player market.
 *
 * <p>All endpoints require a valid {@code Authorization: Bearer <token>} header
 * and verify character ownership via {@link TokenHelper}.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>GET  /api/market               — list ACTIVE listings with suggested prices</li>
 *   <li>POST /api/market/list           — create a listing (escrowing the item)</li>
 *   <li>POST /api/market/buy            — purchase a listing (atomic wallet+item transfer)</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/market")
public class MarketController {

    private final TokenHelper           tokenHelper;
    private final CharacterService      characterService;
    private final CellarItemRepository  cellarItemRepo;
    private final MarketListingRepository listingRepo;
    private final TradeRecordRepository tradeRepo;

    public MarketController(TokenHelper tokenHelper,
                            CharacterService characterService,
                            CellarItemRepository cellarItemRepo,
                            MarketListingRepository listingRepo,
                            TradeRecordRepository tradeRepo) {
        this.tokenHelper      = tokenHelper;
        this.characterService = characterService;
        this.cellarItemRepo   = cellarItemRepo;
        this.listingRepo      = listingRepo;
        this.tradeRepo        = tradeRepo;
    }

    // ── GET /api/market ───────────────────────────────────────────────────────

    /**
     * Returns all ACTIVE market listings, each annotated with a suggested price
     * computed by {@link WinePricer}.
     *
     * <p>Pricing uses:
     * <ol>
     *   <li>Build an {@link Item} from the {@link CellarItem} via
     *       {@link Item#ofWine(double, int, ItemType, boolean)} so the econ
     *       pricing formula has the right type/quality/appellation data.</li>
     *   <li>Compute {@code supply} = count of non-escrowed AGED_WINE items
     *       currently in all cellars (comparable market supply).</li>
     *   <li>Call {@link WinePricer#price(Item, MarketContext)} with a fresh
     *       {@link MarketContext}(supply, item.appellationOk).</li>
     * </ol>
     *
     * <p>Bearer token is required for this endpoint (ownership of the
     * character used to browse is not checked — any valid token may browse).
     */
    @GetMapping
    public ResponseEntity<List<MarketListingView>> getMarket(HttpServletRequest request) {
        tokenHelper.requireAccountId(request);

        List<MarketListing> actives = listingRepo.findByStatus(ListingStatus.ACTIVE);

        // Current active supply: non-escrowed AGED_WINE items across all cellars.
        long activeSupply = cellarItemRepo.countByItemTypeAndEscrowedFalse(ItemType.AGED_WINE.name());

        List<MarketListingView> views = actives.stream().map(listing -> {
            CellarItem ci = cellarItemRepo.findById(listing.getCellarItemId())
                    .orElseThrow(() -> ApiException.badRequest(
                            "CellarItem " + listing.getCellarItemId() + " missing for listing "
                                    + listing.getId()));

            // Build an econ.Item for pricing
            ItemType econType = parseItemType(ci.getItemType());
            Item econItem = Item.ofWine(ci.getQuality(), ci.getVintageYear(),
                    econType, ci.isAppellationOk());

            MarketContext ctx = new MarketContext((int) activeSupply, ci.isAppellationOk());
            double suggestedPrice = WinePricer.price(econItem, ctx);

            return new MarketListingView(listing, ci, suggestedPrice);
        }).collect(Collectors.toList());

        return ResponseEntity.ok(views);
    }

    // ── POST /api/market/list ─────────────────────────────────────────────────

    /**
     * Creates an ACTIVE MarketListing for the given CellarItem.
     *
     * <p>Guards:
     * <ul>
     *   <li>The character must belong to the authenticated account (403/404).</li>
     *   <li>The CellarItem must be owned by that character (400).</li>
     *   <li>The CellarItem must not already be escrowed (400).</li>
     * </ul>
     *
     * <p>Side-effect: sets {@code CellarItem.escrowed = true}.
     */
    @PostMapping("/list")
    public ResponseEntity<MarketListing> listItem(
            @Valid @RequestBody ListRequest req,
            HttpServletRequest request) {

        tokenHelper.requireOwnedCharacter(request, req.getCharacterId());

        CellarItem item = cellarItemRepo
                .findByIdAndCharacterId(req.getCellarItemId(), req.getCharacterId())
                .orElseThrow(() -> ApiException.badRequest(
                        "CellarItem " + req.getCellarItemId()
                                + " not found or not owned by character " + req.getCharacterId()));

        if (item.isEscrowed()) {
            throw ApiException.badRequest(
                    "CellarItem " + item.getId() + " is already escrowed under an active listing");
        }

        // Escrow the item
        item.setEscrowed(true);
        cellarItemRepo.save(item);

        // Create the listing
        MarketListing listing = new MarketListing(
                req.getCharacterId(), item.getId(), req.getAskPrice());
        MarketListing saved = listingRepo.save(listing);

        return ResponseEntity.ok(saved);
    }

    // ── POST /api/market/buy ──────────────────────────────────────────────────

    /**
     * Purchases an ACTIVE listing on behalf of the buyer character.
     *
     * <p>Guards (return 400):
     * <ul>
     *   <li>Listing must exist and be ACTIVE.</li>
     *   <li>Self-buy rejected (buyer == seller character).</li>
     *   <li>Insufficient funds: buyer wallet &lt; askPrice.</li>
     * </ul>
     *
     * <p>On success (atomic sequence):
     * <ol>
     *   <li>Debit buyer wallet by {@code askPrice}.</li>
     *   <li>Credit seller wallet by {@code askPrice}.</li>
     *   <li>Reassign {@code CellarItem.characterId} to buyer and clear escrow.</li>
     *   <li>Set listing status to SOLD.</li>
     *   <li>Create and persist a {@link TradeRecord}.</li>
     * </ol>
     *
     * @return 200 with the created {@link TradeRecord}
     */
    @PostMapping("/buy")
    public ResponseEntity<TradeRecord> buy(
            @Valid @RequestBody BuyRequest req,
            HttpServletRequest request) {

        Character buyer = tokenHelper.requireOwnedCharacter(request, req.getCharacterId());

        MarketListing listing = listingRepo.findById(req.getListingId())
                .orElseThrow(() -> ApiException.badRequest(
                        "Listing " + req.getListingId() + " not found"));

        if (listing.getStatus() != ListingStatus.ACTIVE) {
            throw ApiException.badRequest(
                    "Listing " + req.getListingId() + " is not ACTIVE (status: " + listing.getStatus() + ")");
        }

        Long sellerCharacterId = listing.getSellerCharacterId();
        Long buyerCharacterId  = buyer.getId();

        // Self-buy guard
        if (sellerCharacterId.equals(buyerCharacterId)) {
            throw ApiException.badRequest("Cannot buy your own listing");
        }

        // Funds check
        double price = listing.getAskPrice();
        if (buyer.getWalletGel() < price) {
            throw ApiException.badRequest(
                    "Insufficient funds: wallet " + buyer.getWalletGel()
                            + " GEL, ask price " + price + " GEL");
        }

        // ── Atomic wallet transfer ────────────────────────────────────────────
        // adjustWallet throws if the result would go negative (redundant safety net).
        characterService.adjustWallet(buyerCharacterId,  -price);
        characterService.adjustWallet(sellerCharacterId, +price);

        // ── Transfer item ownership ───────────────────────────────────────────
        CellarItem item = cellarItemRepo.findById(listing.getCellarItemId())
                .orElseThrow(() -> ApiException.badRequest(
                        "CellarItem " + listing.getCellarItemId() + " not found"));
        item.setCharacterId(buyerCharacterId);
        item.setEscrowed(false);
        cellarItemRepo.save(item);

        // ── Close listing ─────────────────────────────────────────────────────
        listing.setStatus(ListingStatus.SOLD);
        listingRepo.save(listing);

        // ── Ledger record ─────────────────────────────────────────────────────
        TradeRecord trade = new TradeRecord(buyerCharacterId, sellerCharacterId,
                item.getId(), price);
        TradeRecord saved = tradeRepo.save(trade);

        return ResponseEntity.ok(saved);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static ItemType parseItemType(String name) {
        try {
            return ItemType.valueOf(name);
        } catch (IllegalArgumentException e) {
            return ItemType.AGED_WINE; // safe fallback
        }
    }
}
