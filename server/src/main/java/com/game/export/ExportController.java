package com.game.export;

import com.game.account.AccountTokenService;
import com.game.character.CharacterService;
import com.game.exception.ApiException;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for the EXPORT lane — {@code /api/export/**}.
 *
 * <p>Inline bearer-token auth (permitAll in SecurityConfig); character ownership via
 * {@link CharacterService#getOwned}.
 *
 * <ul>
 *   <li>GET  /api/export/markets             — the foreign markets</li>
 *   <li>POST /api/export/sell                — sell a cellar item abroad</li>
 *   <li>GET  /api/export/{characterId}       — that character's export history</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/export")
public class ExportController {

    private final AccountTokenService tokenService;
    private final CharacterService    characterService;
    private final ExportService       exportService;

    public ExportController(AccountTokenService tokenService,
                            CharacterService characterService,
                            ExportService exportService) {
        this.tokenService     = tokenService;
        this.characterService = characterService;
        this.exportService    = exportService;
    }

    @GetMapping("/markets")
    public ResponseEntity<List<ForeignMarket>> markets(HttpServletRequest http) {
        requireAccount(http);
        return ResponseEntity.ok(exportService.markets());
    }

    @PostMapping("/sell")
    public ResponseEntity<SellResponse> sell(@RequestBody SellRequest req, HttpServletRequest http) {
        long accountId   = requireAccount(http);
        long characterId = requireCharacterId(req.getCharacterId());
        requireOwnership(characterId, accountId);
        if (req.getCellarItemId() == null) {
            throw ApiException.badRequest("cellarItemId is required");
        }
        if (req.getForeignMarketId() == null) {
            throw ApiException.badRequest("foreignMarketId is required");
        }
        return ResponseEntity.ok(exportService.sell(
                characterId, req.getForeignMarketId(), req.getCellarItemId(), req.getQuantity()));
    }

    @GetMapping("/{characterId}")
    public ResponseEntity<List<ExportRecord>> history(@PathVariable long characterId,
                                                      HttpServletRequest http) {
        long accountId = requireAccount(http);
        requireOwnership(characterId, accountId);
        return ResponseEntity.ok(exportService.history(characterId));
    }

    // ── helpers ──
    private long requireAccount(HttpServletRequest http) {
        String header = http.getHeader("Authorization");
        String token = (header != null && header.startsWith("Bearer ")) ? header.substring(7) : null;
        return tokenService.accountIdFor(token)
                .orElseThrow(() -> ApiException.unauthorized("Missing or invalid bearer token"));
    }

    private long requireCharacterId(Long characterId) {
        if (characterId == null) throw ApiException.badRequest("characterId is required");
        return characterId;
    }

    private void requireOwnership(long characterId, long accountId) {
        characterService.getOwned(characterId, accountId)
                .orElseThrow(() -> ApiException.notFound(
                        "Character " + characterId + " not found for this account"));
    }
}
