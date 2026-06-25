package com.game.account;

import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.game.auth.JwtHelper;
import com.game.config.AppProperties;

/**
 * Stateless bearer-token store for the MMO account system, backed by
 * HMAC-SHA256 JWTs (replaces the old ConcurrentHashMap UUID store).
 *
 * <p>The public API is identical to the original:
 * <ul>
 *   <li>{@link #mint(Long)} issues a new opaque token for the given accountId.</li>
 *   <li>{@link #accountIdFor(String)} resolves a token back to an accountId.</li>
 * </ul>
 *
 * <p>Stateless design: any application instance can validate any token without
 * shared state, enabling horizontal scale behind a load balancer.
 *
 * <p>JWT claim used: {@code "aid"} (account id as a Long).
 */
@Service
public class AccountTokenService {

    private static final String CLAIM_ACCOUNT_ID = "aid";

    private final JwtHelper jwtHelper;

    public AccountTokenService(AppProperties appProperties) {
        this.jwtHelper = new JwtHelper(
                appProperties.getJwt().getSecret(),
                appProperties.getJwt().getExpirySeconds());
    }

    /**
     * Issues a fresh signed JWT for {@code accountId}.
     *
     * @param accountId the account to associate with the token
     * @return compact JWT string (treated as opaque by callers)
     */
    public String mint(Long accountId) {
        return jwtHelper.issue(Map.of(CLAIM_ACCOUNT_ID, accountId));
    }

    /**
     * Resolves a token to the owning account id.
     *
     * @param token raw token string (after stripping "Bearer ")
     * @return the accountId, or Optional.empty() if the token is unknown,
     *         expired, or null/blank
     */
    public Optional<Long> accountIdFor(String token) {
        return jwtHelper.parse(token).map(claims -> {
            Object raw = claims.get(CLAIM_ACCOUNT_ID);
            if (raw instanceof Number) {
                return ((Number) raw).longValue();
            }
            return null;
        });
    }
}
