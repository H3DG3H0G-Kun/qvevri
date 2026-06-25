package com.game.sim.resolve;

import com.game.core.data.FermentMethod;
import com.game.core.data.Region;
import com.game.core.data.Variety;

/**
 * Appellation and terroir-fit rules for Georgian wines.
 *
 * <h2>Appellation table</h2>
 * Based on Georgian PDO/appellation reality:
 * <ul>
 *   <li>Saperavi → KAKHETI (including Kakhetian amber skin-contact method)</li>
 *   <li>Rkatsiteli → KAKHETI and KARTLI</li>
 *   <li>Mtsvane, Kisi → KAKHETI</li>
 *   <li>Tsolikouri, Tsitska → IMERETI</li>
 *   <li>Aleksandrouli, Mujuretuli-analog (ALEKSANDROULI) → RACHA_LECHKHUMI
 *       (Khvanchkara; semi-sweet SWEET method preferred)</li>
 *   <li>Ojaleshi → SAMEGRELO</li>
 *   <li>Chkhaveri → GURIA_ADJARA</li>
 *   <li>Chinuri → KARTLI</li>
 * </ul>
 *
 * <h2>Terroir fit score</h2>
 * A multiplier in (0, 1] applied to quality. Value {@code 1.0} = best-terroir
 * match; lower values penalise wildly off-terroir plantings (heat-loving
 * variety in a cool short-season region, or vice-versa).
 *
 * <p>The KAKHETI + SAPERAVI entry has {@code terroir = 1.0} so the quality
 * formula for that combination is mathematically identical to Phase 0 (where
 * no terroir multiplier existed).
 *
 * <h2>Style override</h2>
 * <ul>
 *   <li>White varieties (Variety.isWhite()) fermented by RED method produce
 *       WHITE style (not RED), because the RED method on a white grape is a
 *       conventional white fermentation — just no skin contact.</li>
 *   <li>Kakhetian/Imeretian skin-contact methods produce AMBER regardless of
 *       variety colour (already handled by {@code styleFromMethod}).</li>
 *   <li>Racha-Lechkhumi reds (ALEKSANDROULI, OJALESHI) with SWEET method
 *       produce SWEET style (Khvanchkara-type).</li>
 * </ul>
 */
public final class AppellationRules {

    private AppellationRules() {}

    // ── Terroir fit tiers ─────────────────────────────────────────────────────

    /** Signature home-region pairing — best possible terroir score. */
    public static final double FIT_SIGNATURE   = 1.00;

    /** Strong secondary pairing — nearby region or variety naturally adapts. */
    public static final double FIT_GOOD        = 0.95;

    /** Neutral pairing — plausible but no particular advantage. */
    public static final double FIT_NEUTRAL     = 0.90;

    /** Mild mismatch — variety can grow here but not thriving. */
    public static final double FIT_POOR        = 0.82;

    /** Serious mismatch — heat-lover in cold short season or vice-versa. */
    public static final double FIT_BAD         = 0.72;

    // ── Appellation check ────────────────────────────────────────────────────

    /**
     * Returns {@code true} when the given (region, variety, method) combination
     * satisfies Georgian appellation rules.
     *
     * <p>The KAKHETI + SAPERAVI entry is explicitly listed for every ferment
     * method so the existing test path remains unchanged.
     *
     * @param region  vineyard region (from Vintage.region())
     * @param variety grape variety
     * @param method  fermentation method used in the cellar
     * @return true if appellation rules are satisfied
     */
    public static boolean appellationOk(Region region, Variety variety, FermentMethod method) {
        if (region == null || variety == null) return false;
        return switch (variety) {
            case SAPERAVI      -> region == Region.KAKHETI;
            case RKATSITELI    -> region == Region.KAKHETI || region == Region.KARTLI;
            case MTSVANE       -> region == Region.KAKHETI;
            case KISI          -> region == Region.KAKHETI;
            case TSOLIKOURI    -> region == Region.IMERETI;
            case TSITSKA       -> region == Region.IMERETI;
            case ALEKSANDROULI -> region == Region.RACHA_LECHKHUMI;
            case OJALESHI      -> region == Region.SAMEGRELO;
            case CHKHAVERI     -> region == Region.GURIA_ADJARA;
            case CHINURI       -> region == Region.KARTLI;
        };
    }

    // ── Terroir fit ───────────────────────────────────────────────────────────

    /**
     * Returns a terroir-fit multiplier in (0, 1] that is applied to quality.
     *
     * <p>{@code 1.0} for the signature home region of a variety; lower values
     * for increasingly ill-suited pairings. The KAKHETI + SAPERAVI entry is
     * exactly {@code 1.0} so the Phase-0 quality result is preserved.
     *
     * @param region  vineyard region
     * @param variety grape variety
     * @return terroir fit multiplier ∈ (0, 1]
     */
    public static double terroirFit(Region region, Variety variety) {
        if (region == null || variety == null) return FIT_NEUTRAL;
        return switch (variety) {

            // ── SAPERAVI (warm/continental, Kakheti home) ─────────────────────
            case SAPERAVI -> switch (region) {
                case KAKHETI         -> FIT_SIGNATURE; // 1.00 — home region, Phase-0 baseline
                case KARTLI          -> FIT_GOOD;      // close, continental
                case SAMEGRELO       -> FIT_NEUTRAL;   // warm but humid, unusual
                case GURIA_ADJARA    -> FIT_POOR;      // too wet
                case IMERETI         -> FIT_POOR;      // cooler/wetter, Saperavi struggles
                case RACHA_LECHKHUMI -> FIT_BAD;       // too cool / short season
                case MESKHETI        -> FIT_BAD;       // too cool / high altitude
            };

            // ── RKATSITELI (late, high-acid; Kakheti & Kartli home) ───────────
            case RKATSITELI -> switch (region) {
                case KAKHETI         -> FIT_SIGNATURE;
                case KARTLI          -> FIT_SIGNATURE; // dual home region
                case IMERETI         -> FIT_NEUTRAL;   // grown but not classic
                case SAMEGRELO       -> FIT_POOR;      // humidity issues
                case GURIA_ADJARA    -> FIT_POOR;      // too wet
                case RACHA_LECHKHUMI -> FIT_BAD;       // too cool, won't fully ripen
                case MESKHETI        -> FIT_BAD;       // too cool / high altitude
            };

            // ── MTSVANE (aromatic; Kakheti home) ─────────────────────────────
            case MTSVANE -> switch (region) {
                case KAKHETI         -> FIT_SIGNATURE;
                case KARTLI          -> FIT_GOOD;
                case IMERETI         -> FIT_NEUTRAL;
                case SAMEGRELO       -> FIT_POOR;
                case GURIA_ADJARA    -> FIT_POOR;
                case RACHA_LECHKHUMI -> FIT_BAD;
                case MESKHETI        -> FIT_BAD;
            };

            // ── KISI (aromatic; Kakheti home) ────────────────────────────────
            case KISI -> switch (region) {
                case KAKHETI         -> FIT_SIGNATURE;
                case KARTLI          -> FIT_GOOD;
                case IMERETI         -> FIT_NEUTRAL;
                case SAMEGRELO       -> FIT_POOR;
                case GURIA_ADJARA    -> FIT_POOR;
                case RACHA_LECHKHUMI -> FIT_BAD;
                case MESKHETI        -> FIT_BAD;
            };

            // ── TSOLIKOURI (late high-acid; Imereti home) ────────────────────
            case TSOLIKOURI -> switch (region) {
                case IMERETI         -> FIT_SIGNATURE;
                case KARTLI          -> FIT_GOOD;      // adapts to cooler continental
                case KAKHETI         -> FIT_NEUTRAL;   // too warm → loses acid
                case RACHA_LECHKHUMI -> FIT_NEUTRAL;   // high acid variety tolerates cool
                case SAMEGRELO       -> FIT_POOR;      // humidity, wrong feel
                case GURIA_ADJARA    -> FIT_POOR;      // too wet / warm
                case MESKHETI        -> FIT_POOR;      // marginal season length
            };

            // ── TSITSKA (very late, highest acid; Imereti home) ───────────────
            case TSITSKA -> switch (region) {
                case IMERETI         -> FIT_SIGNATURE;
                case RACHA_LECHKHUMI -> FIT_NEUTRAL;   // cool is OK, very late ripen risk
                case KARTLI          -> FIT_NEUTRAL;
                case KAKHETI         -> FIT_POOR;      // too warm → over-ripe acid collapse
                case SAMEGRELO       -> FIT_POOR;
                case GURIA_ADJARA    -> FIT_POOR;
                case MESKHETI        -> FIT_BAD;       // season too short for very late variety
            };

            // ── ALEKSANDROULI (thin-skinned; Racha-Lechkhumi home, semi-sweet) ─
            case ALEKSANDROULI -> switch (region) {
                case RACHA_LECHKHUMI -> FIT_SIGNATURE;
                case IMERETI         -> FIT_GOOD;      // cool, adapts
                case MESKHETI        -> FIT_NEUTRAL;   // cool high-altitude, similar
                case KARTLI          -> FIT_NEUTRAL;
                case KAKHETI         -> FIT_POOR;      // too warm, loses freshness
                case SAMEGRELO       -> FIT_BAD;       // humid, disease pressure
                case GURIA_ADJARA    -> FIT_BAD;       // subtropical, wrong entirely
            };

            // ── OJALESHI (aromatic red; Samegrelo home) ───────────────────────
            case OJALESHI -> switch (region) {
                case SAMEGRELO       -> FIT_SIGNATURE;
                case GURIA_ADJARA    -> FIT_GOOD;      // similarly warm/wet
                case KAKHETI         -> FIT_NEUTRAL;   // warm but drier
                case KARTLI          -> FIT_NEUTRAL;
                case IMERETI         -> FIT_POOR;      // too cool
                case RACHA_LECHKHUMI -> FIT_BAD;       // too cool / short
                case MESKHETI        -> FIT_BAD;       // too cool
            };

            // ── CHKHAVERI (rosé; Guria/Adjara home) ──────────────────────────
            case CHKHAVERI -> switch (region) {
                case GURIA_ADJARA    -> FIT_SIGNATURE;
                case SAMEGRELO       -> FIT_GOOD;      // similar subtropical
                case IMERETI         -> FIT_NEUTRAL;
                case KARTLI          -> FIT_NEUTRAL;
                case KAKHETI         -> FIT_POOR;      // too hot/dry for the delicate variety
                case RACHA_LECHKHUMI -> FIT_BAD;
                case MESKHETI        -> FIT_BAD;
            };

            // ── CHINURI (light; Kartli home) ──────────────────────────────────
            case CHINURI -> switch (region) {
                case KARTLI          -> FIT_SIGNATURE;
                case KAKHETI         -> FIT_GOOD;      // warm but Chinuri handles it
                case IMERETI         -> FIT_GOOD;      // similarly cool
                case RACHA_LECHKHUMI -> FIT_NEUTRAL;
                case MESKHETI        -> FIT_NEUTRAL;
                case SAMEGRELO       -> FIT_POOR;
                case GURIA_ADJARA    -> FIT_POOR;
            };
        };
    }

    // ── Style override for white varieties ────────────────────────────────────

    /**
     * Returns {@code true} when the ferment method combined with variety colour
     * should force a WHITE style override.
     *
     * <p>Specifically: a white variety fermented with {@link FermentMethod#RED}
     * is a conventional (non-skin-contact) white fermentation and should produce
     * {@link com.game.core.data.WineStyle#WHITE}, not RED.
     *
     * <p>The SAPERAVI (non-white) path is unchanged — this returns {@code false}
     * for all non-white varieties.
     *
     * @param variety grape variety
     * @param method  fermentation method
     * @return true if WHITE style should override the default method-based style
     */
    public static boolean shouldOverrideToWhite(Variety variety, FermentMethod method) {
        // Only override for white varieties using the RED method.
        // KAKHETIAN/IMERETIAN → AMBER (handled by styleFromMethod already).
        // SPARKLING_BASE / SWEET — those are intentional style choices; don't override.
        return variety != null
                && variety.isWhite()
                && method == FermentMethod.RED;
    }
}
