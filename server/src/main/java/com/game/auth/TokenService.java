package com.game.auth;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import com.game.config.AppProperties;

/**
 * Stateless bearer-token issuance and validation backed by HMAC-SHA256 JWTs.
 *
 * <p>The externally visible contract is identical to the old UUID-map implementation:
 * <ul>
 *   <li>{@link #login} returns a {@link LoginResponse} whose {@code token} field is
 *       an opaque string (now a JWT compact serialisation instead of a UUID).</li>
 *   <li>{@link #validate} resolves that token to a {@link UserStore.UserRecord}.</li>
 * </ul>
 *
 * <p>Claims embedded in the JWT:
 * <ul>
 *   <li>{@code sub} — username</li>
 *   <li>{@code pid} — playerId (e.g. "p_abc123")</li>
 *   <li>{@code dn}  — displayName</li>
 * </ul>
 *
 * <p>The UserStore is still used for credential validation and to hold the in-memory
 * user record (password hash, playerId, displayName); tokens are no longer stored
 * in a map so any application instance can validate any token.
 */
@Service
public class TokenService implements InitializingBean {

    // Claim key constants
    private static final String CLAIM_PLAYER_ID    = "pid";
    private static final String CLAIM_DISPLAY_NAME = "dn";

    private final UserStore userStore;
    private final AppProperties appProperties;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    /** Lazily initialised from config after properties are set. */
    private JwtHelper jwtHelper;

    public TokenService(UserStore userStore, AppProperties appProperties) {
        this.userStore = userStore;
        this.appProperties = appProperties;
    }

    @Override
    public void afterPropertiesSet() {
        jwtHelper = new JwtHelper(
                appProperties.getJwt().getSecret(),
                appProperties.getJwt().getExpirySeconds());

        // Seed default dev user "lasha" with password "secret".
        String seedHash = encoder.encode("secret");
        String playerId = generatePlayerId();
        userStore.seed("lasha", seedHash, playerId, "lasha");
    }

    /**
     * Authenticates and returns a signed JWT token, or empty if credentials are bad.
     *
     * <p>The returned {@link LoginResponse#getToken()} is now a JWT string instead of
     * a UUID, but callers treat it as opaque so this is a transparent substitution.
     */
    public Optional<LoginResponse> login(String username, String password) {
        Optional<UserStore.UserRecord> existing = userStore.find(username);

        UserStore.UserRecord record;
        if (existing.isPresent()) {
            record = existing.get();
            if (!encoder.matches(password, record.passwordHash())) {
                return Optional.empty();
            }
        } else if (appProperties.getAuth().isAutoRegister()) {
            String hashed = encoder.encode(password);
            record = userStore.register(username, hashed, generatePlayerId());
        } else {
            return Optional.empty();
        }

        String token = mintToken(record);
        long expirySeconds = appProperties.getJwt().getExpirySeconds();
        return Optional.of(new LoginResponse(token, record.playerId(), record.displayName(), expirySeconds));
    }

    /**
     * Returns the UserRecord associated with a token, or empty if invalid/missing.
     *
     * <p>Validates the JWT signature and expiry, then looks up the UserRecord from
     * the UserStore by the {@code sub} (username) claim so that the returned object
     * has the current password hash (useful if credentials changed).  Falls back to
     * reconstructing the record from JWT claims when the user is not in the store
     * (e.g. a new instance that hasn't seen the login yet — auto-registered users
     * should be in the store; for the rare edge case, the JWT claims are authoritative).
     */
    public Optional<UserStore.UserRecord> validate(String token) {
        return jwtHelper.parse(token).flatMap(claims -> {
            String username = claims.getSubject();
            // Tokens without a username subject are not user tokens (e.g. an
            // account JWT from AccountTokenService, signed with the same key).
            // Ignore them here so this filter never calls UserStore.find(null).
            if (username == null || username.isBlank()) {
                return Optional.empty();
            }
            String playerId = (String) claims.get(CLAIM_PLAYER_ID);
            String dn       = (String) claims.get(CLAIM_DISPLAY_NAME);
            // Prefer the live UserStore record; fall back to JWT claims.
            return Optional.of(userStore.find(username)
                    .orElseGet(() -> new UserStore.UserRecord(playerId, username, dn, "")));
        });
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private String mintToken(UserStore.UserRecord record) {
        return jwtHelper.issue(Map.of(
                "sub", record.username(),
                CLAIM_PLAYER_ID,    record.playerId(),
                CLAIM_DISPLAY_NAME, record.displayName()
        ));
    }

    private String generatePlayerId() {
        return "p_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
}
