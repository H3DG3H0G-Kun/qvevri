package com.game.logistics;

import com.game.world.Region;
import com.game.world.RegionInfo;
import com.game.world.WorldCatalog;

/**
 * Pure geographic utility for the logistics lane.
 *
 * <p>Travel-time model:
 * <pre>
 *   haversineKm(lat1, lon1, lat2, lon2)  — great-circle distance in km
 *   travelDays(from, to) = max(1, ceil(haversineKm / KM_PER_DAY))
 * </pre>
 *
 * <p>{@code KM_PER_DAY = 40} — a laden mule-cart / slow ox-wagon across
 * Georgian mountain roads. Produces realistic 1–4 day haul times within
 * the seven regions (all within ~350 km of each other).
 *
 * <p>Example distances (approximate):
 * <ul>
 *   <li>Kakheti  → Kartli  (Telavi→Gori)  ≈  110 km  → 3 travel days</li>
 *   <li>Kakheti  → Guria/Adjara (Telavi→Batumi) ≈ 395 km → 10 travel days</li>
 * </ul>
 */
public final class GeoUtil {

    /** Earth mean radius used for Haversine computation (km). */
    private static final double EARTH_RADIUS_KM = 6371.0;

    /**
     * Simulated road speed in km per sim-day. Governs travel time.
     * Documented here so tuning has a single point of change.
     * 40 km/day ≈ a Georgian mountain cart over mixed terrain.
     */
    public static final double KM_PER_DAY = 40.0;

    private GeoUtil() {}

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Computes the great-circle distance between two WGS84 points.
     *
     * @param lat1 latitude of point 1 (decimal degrees)
     * @param lon1 longitude of point 1 (decimal degrees)
     * @param lat2 latitude of point 2 (decimal degrees)
     * @param lon2 longitude of point 2 (decimal degrees)
     * @return distance in kilometres
     */
    public static double haversineKm(double lat1, double lon1,
                                     double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double lat1r = Math.toRadians(lat1);
        double lat2r = Math.toRadians(lat2);

        double a = Math.sin(dLat / 2.0) * Math.sin(dLat / 2.0)
                + Math.cos(lat1r) * Math.cos(lat2r)
                * Math.sin(dLon / 2.0) * Math.sin(dLon / 2.0);

        double c = 2.0 * Math.atan2(Math.sqrt(a), Math.sqrt(1.0 - a));
        return EARTH_RADIUS_KM * c;
    }

    /**
     * Computes the number of sim-days to travel from {@code from} to {@code to}.
     *
     * <p>Formula: {@code max(1, ceil(haversineKm(from, to) / KM_PER_DAY))}.
     * Same-region shipments always cost at least 1 day (local delivery overhead).
     *
     * @param from the origin region
     * @param to   the destination region
     * @return travel days (always ≥ 1)
     */
    public static int travelDays(Region from, Region to) {
        RegionInfo fromInfo = regionInfo(from);
        RegionInfo toInfo   = regionInfo(to);
        double km = haversineKm(
                fromInfo.latitude(), fromInfo.longitude(),
                toInfo.latitude(),   toInfo.longitude());
        return Math.max(1, (int) Math.ceil(km / KM_PER_DAY));
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private static RegionInfo regionInfo(Region region) {
        return WorldCatalog.REGIONS.stream()
                .filter(r -> r.region() == region)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "No RegionInfo found for region: " + region));
    }
}
