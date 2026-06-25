package com.game.export;

/**
 * Immutable descriptor for one foreign market destination.
 *
 * <p>Fields:
 * <ul>
 *   <li>{@code id}              — stable string key used in API requests/responses</li>
 *   <li>{@code name}            — human-readable display name</li>
 *   <li>{@code priceMultiplier} — factor applied to the gross base price (> 1.0 = premium market)</li>
 *   <li>{@code tariffRate}      — fraction of gross deducted as export tariff (0.0–1.0)</li>
 *   <li>{@code demandNote}      — brief flavour / demand characteristic shown to the player</li>
 * </ul>
 *
 * @param id              stable market identifier (e.g. "russia")
 * @param name            display name (e.g. "Russian Empire")
 * @param priceMultiplier gross price multiplier (&gt; 1.0 for premium markets)
 * @param tariffRate      tariff fraction applied to gross (e.g. 0.10 = 10 %)
 * @param demandNote      one-line demand description for the player UI
 */
public record ForeignMarket(
        String id,
        String name,
        double priceMultiplier,
        double tariffRate,
        String demandNote
) {}
