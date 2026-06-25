package com.game.market;

import com.game.bonus.BonusService;
import com.game.bonus.BonusTypes;
import com.game.econ.ItemType;
import com.game.vineyard.BottleDto;
import com.game.vineyard.VineyardRequest;
import com.game.vineyard.VineyardService;
import com.game.vineyard.VineyardYearResult;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for the character cellar.
 *
 * <p>All endpoints require a valid {@code Authorization: Bearer <token>} header
 * and verify that the target character belongs to the authenticated account.
 * Authentication is performed inline via {@link TokenHelper} (not Spring Security),
 * consistent with the MMO-CORE-SPEC §1 pattern.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>GET  /api/cellar/{characterId}       — list non-escrowed items</li>
 *   <li>POST /api/cellar/{characterId}/grow  — run vineyard sim, save bottle</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/cellar")
public class CellarController {

    private final TokenHelper         tokenHelper;
    private final VineyardService     vineyardService;
    private final CellarItemRepository cellarItemRepo;
    private final BonusService        bonusService;

    public CellarController(TokenHelper tokenHelper,
                             VineyardService vineyardService,
                             CellarItemRepository cellarItemRepo,
                             BonusService bonusService) {
        this.tokenHelper     = tokenHelper;
        this.vineyardService = vineyardService;
        this.cellarItemRepo  = cellarItemRepo;
        this.bonusService    = bonusService;
    }

    // ── GET /api/cellar/{characterId} ─────────────────────────────────────────

    /**
     * Returns all non-escrowed CellarItems for the given character.
     *
     * @param characterId path variable
     * @param request     HTTP request (used for bearer token extraction)
     * @return 200 CellarItem[]; 401 if token invalid; 404 if character not owned
     */
    @GetMapping("/{characterId}")
    public ResponseEntity<List<CellarItem>> getCellar(
            @PathVariable Long characterId,
            HttpServletRequest request) {

        tokenHelper.requireOwnedCharacter(request, characterId);
        List<CellarItem> items = cellarItemRepo.findByCharacterIdAndEscrowedFalse(characterId);
        return ResponseEntity.ok(items);
    }

    // ── POST /api/cellar/{characterId}/grow ───────────────────────────────────

    /**
     * Runs a deterministic vineyard simulation for the given parameters and
     * stores the resulting bottle as a {@link CellarItem} in the character's cellar.
     *
     * <p>Mapping from {@link BottleDto} → {@link CellarItem}:
     * <ul>
     *   <li>{@code itemType}     = {@link ItemType#AGED_WINE} (fixed for a finished bottle)</li>
     *   <li>{@code quantity}     = {@code bottle.volumeL()}</li>
     *   <li>{@code quality}      = {@code bottle.quality()}</li>
     *   <li>{@code vintageYear}  = {@code bottle.vintageYear()}</li>
     *   <li>{@code style}        = {@code bottle.style()}</li>
     *   <li>{@code appellationOk}= {@code bottle.appellationOk()}</li>
     *   <li>{@code label}        = {@code bottle.label()}</li>
     * </ul>
     *
     * @param characterId path variable
     * @param growReq     validated grow parameters
     * @param request     HTTP request (bearer token)
     * @return 200 {@link GrowResponse} containing the saved CellarItem and the VineyardYearResult
     */
    @PostMapping("/{characterId}/grow")
    public ResponseEntity<GrowResponse> grow(
            @PathVariable Long characterId,
            @Valid @RequestBody GrowRequest growReq,
            HttpServletRequest request) {

        tokenHelper.requireOwnedCharacter(request, characterId);

        // Build a VineyardRequest from the grow request.
        // Variety and soil use the server-side defaults (Saperavi / HUMUS_CARBONATE)
        // since the client grow contract only exposes seed/budLoad/pickDay/threats.
        VineyardRequest vinReq = new VineyardRequest(
                growReq.getSeed(),
                "SAPERAVI",
                "HUMUS_CARBONATE",
                growReq.getBudLoad(),
                growReq.getPickDay(),
                growReq.isThreats()
        );

        VineyardYearResult yearResult = vineyardService.simulate(vinReq);
        BottleDto bottle = yearResult.bottle();

        // INTEGRATION: YIELD raises harvest output (litres). Aggregated across career
        // (Grower +15%), skills (green_thumb), active staff (vineyard_hand), and
        // completed research (improved_pruning). 0.0 for a no-bonus character, so the
        // raw simulated volume is unchanged; a bonus only ever scales it up.
        double yield = bonusService.total(characterId, BonusTypes.YIELD);
        double volumeL = bottle.volumeL() * (1.0 + yield);

        // Map BottleDto → CellarItem
        CellarItem item = new CellarItem(
                characterId,
                ItemType.AGED_WINE.name(),   // itemType mirrors econ.ItemType name
                volumeL,                      // quantity = volume in litres (yield-adjusted)
                bottle.quality(),
                bottle.vintageYear(),
                bottle.style(),
                bottle.appellationOk(),
                bottle.label()
        );
        CellarItem saved = cellarItemRepo.save(item);

        return ResponseEntity.ok(new GrowResponse(saved, yearResult));
    }
}
