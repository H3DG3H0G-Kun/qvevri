package com.game.economy;

import com.game.market.TokenHelper;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for LANE ECONOMY endpoints.
 *
 * <p>All endpoints require a valid {@code Authorization: Bearer <token>} header;
 * authentication is handled inline via {@link TokenHelper} (no Spring Security
 * rules needed — {@code /api/economy/**} is already {@code permitAll} in SecurityConfig).
 *
 * <h2>Endpoints</h2>
 * <ul>
 *   <li>GET  /api/economy/price?itemType=&amp;region=  — compute a live price quote</li>
 *   <li>GET  /api/economy/index                        — per-region WINE price index</li>
 *   <li>POST /api/economy/snapshot {itemType, region}  — persist + return a snapshot</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/economy")
public class EconomyController {

    private final TokenHelper    tokenHelper;
    private final EconomyService economyService;

    public EconomyController(TokenHelper tokenHelper, EconomyService economyService) {
        this.tokenHelper    = tokenHelper;
        this.economyService = economyService;
    }

    // ── GET /api/economy/price ────────────────────────────────────────────────

    /**
     * Compute a live price quote for the given item type and region.
     *
     * <p>Returns 400 if {@code itemType} or {@code region} is unknown.
     *
     * @param itemType  e.g. "WINE", "AGED_WINE", "GRAPES" …
     * @param region    e.g. "KAKHETI", "KARTLI" …
     * @return 200 with { basePrice, supplyFactor, regionalFactor,
     *                    grossPrice, fee, netPrice, supplyCount }
     */
    @GetMapping("/price")
    public ResponseEntity<PriceQuote> price(
            @RequestParam String itemType,
            @RequestParam String region,
            HttpServletRequest request) {

        tokenHelper.requireAccountId(request);
        PriceQuote quote = economyService.quote(itemType, region);
        return ResponseEntity.ok(quote);
    }

    // ── GET /api/economy/index ────────────────────────────────────────────────

    /**
     * Returns the per-region price index for the main wine item type (WINE).
     *
     * <p>The response is a JSON array of {@code { region, price }} objects,
     * one entry per known region, sorted alphabetically by region name.
     *
     * @return 200 with array of { region, price }
     */
    @GetMapping("/index")
    public ResponseEntity<List<RegionIndex>> index(HttpServletRequest request) {
        tokenHelper.requireAccountId(request);
        return ResponseEntity.ok(economyService.index());
    }

    // ── POST /api/economy/snapshot ────────────────────────────────────────────

    /**
     * Persists a PriceSnapshot for the given item type and region and returns it.
     *
     * <p>Request body: {@code { "itemType": "WINE", "region": "KAKHETI" }}
     *
     * @return 200 with the persisted {@link PriceSnapshot}
     */
    @PostMapping("/snapshot")
    public ResponseEntity<PriceSnapshot> snapshot(
            @RequestBody Map<String, String> body,
            HttpServletRequest request) {

        tokenHelper.requireAccountId(request);

        String itemType = body.get("itemType");
        String region   = body.get("region");

        if (itemType == null || itemType.isBlank()) {
            throw com.game.exception.ApiException.badRequest("itemType is required");
        }
        if (region == null || region.isBlank()) {
            throw com.game.exception.ApiException.badRequest("region is required");
        }

        PriceSnapshot snap = economyService.snapshot(itemType, region);
        return ResponseEntity.ok(snap);
    }
}
