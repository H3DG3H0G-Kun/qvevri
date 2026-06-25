package com.game.market;

import com.game.account.AccountTokenService;
import com.game.character.Character;
import com.game.character.CharacterService;
import com.game.exception.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * Shared helper used by both {@link CellarController} and {@link MarketController}
 * to extract and validate the bearer token and resolve character ownership.
 *
 * <p>Constructor-injected; stateless and thread-safe.
 */
@Component
public class TokenHelper {

    private final AccountTokenService accountTokenService;
    private final CharacterService    characterService;

    public TokenHelper(AccountTokenService accountTokenService,
                       CharacterService characterService) {
        this.accountTokenService = accountTokenService;
        this.characterService    = characterService;
    }

    /**
     * Extracts {@code Authorization: Bearer <token>} from the request header
     * and resolves the accountId.
     *
     * @throws ApiException 401 UNAUTHORIZED if the header is missing or invalid
     */
    public Long requireAccountId(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            throw ApiException.unauthorized("Missing or malformed Authorization header");
        }
        String token = header.substring(7).strip();
        return accountTokenService.accountIdFor(token)
                .orElseThrow(() -> ApiException.unauthorized("Invalid or expired token"));
    }

    /**
     * Verifies that {@code characterId} belongs to the authenticated account
     * derived from the request's bearer token.
     *
     * @return the verified {@link Character}
     * @throws ApiException 401 if the token is invalid, 404 if the character is not found
     *                      or does not belong to the account
     */
    public Character requireOwnedCharacter(HttpServletRequest request, Long characterId) {
        Long accountId = requireAccountId(request);
        return characterService.getOwned(characterId, accountId)
                .orElseThrow(() -> ApiException.notFound(
                        "Character " + characterId + " not found or not owned by this account"));
    }
}
