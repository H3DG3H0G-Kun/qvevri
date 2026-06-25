package com.game.goods;

import com.game.market.TokenHelper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Collection;
import java.util.List;

/**
 * REST controller for goods catalog and character inventory.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>GET /api/goods/catalog            → {@link GoodType}[] (no auth)</li>
 *   <li>GET /api/goods/{characterId}       → {@link OwnedGood}[] (auth + ownership)</li>
 * </ul>
 *
 * <p>Security config already permits /api/goods/** at the filter level.
 * Auth is enforced inline via {@link TokenHelper} (same pattern as MarketController).
 */
@RestController
@RequestMapping("/api/goods")
public class GoodsController {

    private final TokenHelper        tokenHelper;
    private final OwnedGoodRepository ownedGoodRepository;

    public GoodsController(TokenHelper tokenHelper,
                           OwnedGoodRepository ownedGoodRepository) {
        this.tokenHelper         = tokenHelper;
        this.ownedGoodRepository = ownedGoodRepository;
    }

    // ── GET /api/goods/catalog ────────────────────────────────────────────────

    /**
     * Returns the full static goods catalog. No authentication required.
     */
    @GetMapping("/catalog")
    public ResponseEntity<Collection<GoodType>> getCatalog() {
        return ResponseEntity.ok(GoodsCatalog.all());
    }

    // ── GET /api/goods/{characterId} ─────────────────────────────────────────

    /**
     * Returns all goods owned by the given character.
     *
     * <p>Requires a valid bearer token. The character must belong to the
     * authenticated account.
     */
    @GetMapping("/{characterId}")
    public ResponseEntity<List<OwnedGood>> getInventory(
            @PathVariable Long characterId,
            HttpServletRequest request) {

        // Ownership check: token → account → character
        tokenHelper.requireOwnedCharacter(request, characterId);

        List<OwnedGood> inventory = ownedGoodRepository.findByCharacterId(characterId);
        return ResponseEntity.ok(inventory);
    }
}
