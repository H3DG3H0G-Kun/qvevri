package com.game.account;

import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Tiny utility for reading the {@code Authorization: Bearer <token>} header
 * and resolving it to an accountId via {@link AccountTokenService}.
 *
 * <p>Used by controllers in the MA lane (AccountController, CharacterController)
 * and intended for reuse by MB (market lane) controllers.
 */
public final class BearerTokenSupport {

    private BearerTokenSupport() {}

    /**
     * Extracts the raw token from the Authorization header.
     *
     * @param request incoming HTTP request
     * @return the token string, or Optional.empty() if the header is absent or malformed
     */
    public static Optional<String> extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            return Optional.empty();
        }
        String token = header.substring(7).strip();
        return token.isBlank() ? Optional.empty() : Optional.of(token);
    }

    /**
     * Resolves the bearer token from the request to an accountId.
     *
     * @param request      incoming HTTP request
     * @param tokenService the AccountTokenService to validate against
     * @return the accountId, or Optional.empty() if missing or invalid
     */
    public static Optional<Long> resolveAccountId(HttpServletRequest request,
                                                   AccountTokenService tokenService) {
        return extractToken(request).flatMap(tokenService::accountIdFor);
    }
}
