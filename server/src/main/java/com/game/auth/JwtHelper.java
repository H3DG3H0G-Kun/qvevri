package com.game.auth;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import java.util.Optional;

import javax.crypto.SecretKey;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * Low-level HMAC-SHA256 JWT helper shared by {@link TokenService} and
 * {@link com.game.account.AccountTokenService}.
 *
 * <p>All public methods are thread-safe (SecretKey is immutable).
 */
public final class JwtHelper {

    private final SecretKey signingKey;
    private final long expiryMs;

    /**
     * @param secret      raw secret string (base64 or plain; padded to ≥ 32 bytes internally)
     * @param expirySeconds token lifetime in seconds
     */
    public JwtHelper(String secret, long expirySeconds) {
        // Keys.hmacShaKeyFor requires at least 256 bits (32 bytes).
        // We derive the key from the UTF-8 bytes of the secret string,
        // padding to 32 bytes if necessary so short dev secrets don't throw.
        byte[] raw = secret.getBytes(StandardCharsets.UTF_8);
        if (raw.length < 32) {
            byte[] padded = new byte[32];
            System.arraycopy(raw, 0, padded, 0, raw.length);
            raw = padded;
        }
        this.signingKey = Keys.hmacShaKeyFor(raw);
        this.expiryMs = expirySeconds * 1000L;
    }

    /**
     * Issues a signed JWT with the given claims.
     *
     * @param claims additional claims to embed (e.g. "playerId", "accountId")
     * @return compact serialised JWT string
     */
    public String issue(Map<String, Object> claims) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .claims(claims)
                .issuedAt(new Date(now))
                .expiration(new Date(now + expiryMs))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Parses and validates a JWT, returning its claims on success.
     *
     * @param token compact serialised JWT string
     * @return parsed claims, or {@link Optional#empty()} if the token is missing,
     *         malformed, or signature-invalid
     */
    public Optional<Claims> parse(String token) {
        if (token == null || token.isBlank()) return Optional.empty();
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.of(claims);
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** Remaining lifetime in seconds encoded in the token, or 0 if already expired. */
    public long remainingSeconds(Claims claims) {
        long expMs = claims.getExpiration().getTime();
        long remainingMs = expMs - System.currentTimeMillis();
        return Math.max(0L, remainingMs / 1000L);
    }
}
