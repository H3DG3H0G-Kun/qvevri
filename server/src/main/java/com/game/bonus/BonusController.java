package com.game.bonus;

import com.game.account.AccountTokenService;
import com.game.character.CharacterService;
import com.game.exception.ApiException;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Read API for the aggregated bonus totals (INTEGRATION PASS).
 *
 * <p>{@code GET /api/bonus/{characterId}} → every canonical bonus type mapped to the
 * character's total effective value (career + skills). Inline bearer auth + ownership.
 */
@RestController
@RequestMapping("/api/bonus")
public class BonusController {

    private final AccountTokenService tokenService;
    private final CharacterService    characterService;
    private final BonusService        bonusService;

    public BonusController(AccountTokenService tokenService,
                           CharacterService characterService,
                           BonusService bonusService) {
        this.tokenService     = tokenService;
        this.characterService = characterService;
        this.bonusService     = bonusService;
    }

    @GetMapping("/{characterId}")
    public ResponseEntity<Map<String, Double>> bonuses(@PathVariable long characterId,
                                                       HttpServletRequest http) {
        long accountId = requireAccount(http);
        characterService.getOwned(characterId, accountId)
                .orElseThrow(() -> ApiException.notFound(
                        "Character " + characterId + " not found for this account"));
        return ResponseEntity.ok(bonusService.allBonuses(characterId));
    }

    private long requireAccount(HttpServletRequest http) {
        String header = http.getHeader("Authorization");
        String token = (header != null && header.startsWith("Bearer ")) ? header.substring(7) : null;
        return tokenService.accountIdFor(token)
                .orElseThrow(() -> ApiException.unauthorized("Missing or invalid bearer token"));
    }
}
