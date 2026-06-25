package com.game.sim.region;

import com.game.core.data.Region;
import com.game.core.data.SiteProfile;
import com.game.core.data.SoilType;

/**
 * Default {@link SiteProfile} per region, representing each region's
 * canonical mid-slope, mid-altitude plot geometry and predominant soil type.
 *
 * <p><strong>KAKHETI keeps the exact SiteProfile that was hardcoded in
 * {@code VineyardReplayService} and {@code VineyardService}:</strong>
 * {@code new SiteProfile(HUMUS_CARBONATE, 12.0, 180.0, 450.0, 0.15, 0.25)}.
 * This guarantees that KAKHETI + SAPERAVI sim output is byte-identical to the
 * pre-refactor form (REGIONS-SPEC hard constraint).
 *
 * <h2>Per-region rationale (REGIONS-SPEC §2)</h2>
 * <ul>
 *   <li>KARTLI — continental valley; alluvial soils, moderate slope, slightly lower
 *       altitude than Kakheti, moderate frost risk.</li>
 *   <li>IMERETI — wetter, clay-limestone soils; moderate slope, south-facing,
 *       higher frost risk (humid air).</li>
 *   <li>RACHA_LECHKHUMI — high altitude, steep terraced slopes; humus-carbonate,
 *       moderate frost risk, high water proximity.</li>
 *   <li>SAMEGRELO — warm/humid coastal; alluvial / clay soils, gentle slope,
 *       low frost risk.</li>
 *   <li>GURIA_ADJARA — subtropical; clay-limestone, gentle slope, very low frost
 *       risk, high water proximity.</li>
 *   <li>MESKHETI — high-altitude terraced; volcanic / clay-limestone, steep slope,
 *       moderate-high frost risk, low water proximity.</li>
 * </ul>
 */
public final class RegionSiteProfiles {

    private RegionSiteProfiles() {}

    // ── Pre-built site profiles ───────────────────────────────────────────────

    /**
     * KAKHETI canonical site — identical to the hardcoded constant in
     * {@code VineyardReplayService} and {@code VineyardService}.
     * Must not be changed; all 122 existing tests depend on this value.
     */
    public static final SiteProfile KAKHETI =
            new SiteProfile(SoilType.HUMUS_CARBONATE, 12.0, 180.0, 450.0, 0.15, 0.25);

    /** KARTLI — alluvial valley, moderate slope, 350 m altitude. */
    public static final SiteProfile KARTLI =
            new SiteProfile(SoilType.ALLUVIAL, 10.0, 175.0, 350.0, 0.20, 0.20);

    /** IMERETI — clay-limestone, moderate slope, 400 m altitude, higher frost risk. */
    public static final SiteProfile IMERETI =
            new SiteProfile(SoilType.CLAY_LIMESTONE, 12.0, 180.0, 400.0, 0.25, 0.30);

    /** RACHA_LECHKHUMI — humus-carbonate, steep terraced slope, 700 m altitude. */
    public static final SiteProfile RACHA_LECHKHUMI =
            new SiteProfile(SoilType.HUMUS_CARBONATE, 20.0, 170.0, 700.0, 0.20, 0.35);

    /** SAMEGRELO — alluvial, gentle slope, 150 m altitude, low frost risk. */
    public static final SiteProfile SAMEGRELO =
            new SiteProfile(SoilType.ALLUVIAL, 8.0, 180.0, 150.0, 0.10, 0.40);

    /** GURIA_ADJARA — clay-limestone, gentle slope, 200 m altitude, very low frost risk. */
    public static final SiteProfile GURIA_ADJARA =
            new SiteProfile(SoilType.CLAY_LIMESTONE, 8.0, 175.0, 200.0, 0.08, 0.45);

    /** MESKHETI — volcanic, steep terraced slope, 900 m altitude, moderate-high frost risk. */
    public static final SiteProfile MESKHETI =
            new SiteProfile(SoilType.VOLCANIC, 18.0, 165.0, 900.0, 0.30, 0.20);

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Returns the default {@link SiteProfile} for the given region.
     * Never returns {@code null}; unknown/null regions fall back to {@link #KAKHETI}.
     *
     * @param region the wine region
     * @return immutable SiteProfile record
     */
    public static SiteProfile of(Region region) {
        if (region == null) return KAKHETI;
        return switch (region) {
            case KAKHETI         -> KAKHETI;
            case KARTLI          -> KARTLI;
            case IMERETI         -> IMERETI;
            case RACHA_LECHKHUMI -> RACHA_LECHKHUMI;
            case SAMEGRELO       -> SAMEGRELO;
            case GURIA_ADJARA    -> GURIA_ADJARA;
            case MESKHETI        -> MESKHETI;
        };
    }
}
