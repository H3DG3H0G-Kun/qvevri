package com.game.career;

import com.game.account.AccountTokenService;
import com.game.character.Character;
import com.game.character.CharacterService;
import com.game.exception.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;

/**
 * REST controller for the Career lane — {@code /api/career/**}.
 *
 * <p>All endpoints use inline bearer-token authentication (no Spring Security
 * filter needed — {@code /api/career/**} is {@code permitAll} in SecurityConfig).
 * Character ownership is verified via {@link CharacterService#getOwned}.
 *
 * <p>This lane is ADDITIVE and read-only: it exposes static {@link CareerProfile}
 * data. Multipliers are NOT yet wired into pricing/yield/shipping — that is the
 * deferred integration pass.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>GET /api/career/catalog           — all 9 CareerProfiles</li>
 *   <li>GET /api/career/{characterId}     — profile for that character's careerType</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/career")
public class CareerController {

    private final AccountTokenService accountTokenService;
    private final CharacterService    characterService;

    public CareerController(AccountTokenService accountTokenService,
                            CharacterService characterService) {
        this.accountTokenService = accountTokenService;
        this.characterService    = characterService;
    }

    // ── GET /api/career/catalog ───────────────────────────────────────────────

    /**
     * Returns all 9 {@link CareerProfile}s from the static catalog.
     *
     * <p>Requires a valid bearer token (account must be authenticated);
     * no character ownership check is needed for a public catalog.
     *
     * @return 200 with the full collection of CareerProfiles
     */
    @GetMapping("/catalog")
    public ResponseEntity<Collection<CareerProfile>> getCatalog(HttpServletRequest request) {
        requireAccountId(request);
        return ResponseEntity.ok(CareerProfileCatalog.all());
    }

    // ── GET /api/career/{characterId} ─────────────────────────────────────────

    /**
     * Returns the {@link CareerProfile} for the given character's careerType.
     *
     * <p>The requesting account must own the character; ownership is enforced
     * via {@link CharacterService#getOwned}.
     *
     * @param characterId the character whose career profile to retrieve
     * @return 200 with the matching CareerProfile
     */
    @GetMapping("/{characterId}")
    public ResponseEntity<CareerProfile> getCharacterCareerProfile(
            @PathVariable Long characterId,
            HttpServletRequest request) {

        Character character = requireOwnedCharacter(request, characterId);
        CareerProfile profile = CareerProfileCatalog.of(character.getCareerType());
        return ResponseEntity.ok(profile);
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    /**
     * Extracts and validates the bearer token, returning the accountId.
     *
     * @throws ApiException 401 if the header is missing or invalid
     */
    private Long requireAccountId(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            throw ApiException.unauthorized("Missing or malformed Authorization header");
        }
        String token = header.substring(7).strip();
        return accountTokenService.accountIdFor(token)
                .orElseThrow(() -> ApiException.unauthorized("Invalid or expired token"));
    }

    /**
     * Verifies that {@code characterId} belongs to the authenticated account.
     *
     * @return the owned {@link Character}
     * @throws ApiException 401 if token invalid, 404 if character not found or not owned
     */
    private Character requireOwnedCharacter(HttpServletRequest request, Long characterId) {
        Long accountId = requireAccountId(request);
        return characterService.getOwned(characterId, accountId)
                .orElseThrow(() -> ApiException.notFound(
                        "Character " + characterId
                        + " not found or not owned by this account"));
    }
}
