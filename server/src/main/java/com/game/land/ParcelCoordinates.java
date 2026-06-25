package com.game.land;

import com.game.world.Region;
import com.game.world.RegionInfo;
import com.game.world.WorldCatalog;

/**
 * Derives jittered WGS84 coordinates for a new parcel.
 *
 * <p>Strategy: the parcel is placed near the region's representative-town centre
 * (from {@link WorldCatalog#REGIONS}) with a small deterministic offset derived
 * from the owner character-id and a monotonically increasing counter (the parcel's
 * creation-time epoch millis).  This ensures:
 * <ul>
 *   <li>Parcels cluster near the real Georgian town for that region.</li>
 *   <li>Two parcels bought by the same character at different times land at
 *       different spots (counter drives them apart).</li>
 *   <li>No wall-clock randomness enters the sim path — the jitter is fully
 *       reproducible from (characterId, seed).</li>
 * </ul>
 *
 * <p>Jitter magnitude: ±0.08° (~8 km) in each axis — enough spread across
 * a wine region without leaving it.  Both axes are clamped inside Georgia's
 * hard bounding box (lat 41.0–43.6, lon 40.0–46.8).
 */
final class ParcelCoordinates {

    // Georgia bounding box
    static final double LAT_MIN = 41.0;
    static final double LAT_MAX = 43.6;
    static final double LON_MIN = 40.0;
    static final double LON_MAX = 46.8;

    /** Maximum jitter radius in decimal degrees (~8 km). */
    private static final double MAX_JITTER = 0.08;

    private ParcelCoordinates() {}

    /**
     * Derives jittered, clamped coordinates for a parcel in the given region.
     *
     * @param region      the Georgian wine region
     * @param characterId owning character (seed source 1)
     * @param seed        a monotonically increasing counter, e.g. epoch-millis
     *                    at buy time (seed source 2)
     * @return {@code double[]{latitude, longitude}} clamped inside Georgia's box
     */
    static double[] derive(Region region, long characterId, long seed) {
        RegionInfo info = WorldCatalog.REGIONS.stream()
                .filter(r -> r.region() == region)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown region: " + region));

        double centreLat = info.latitude();
        double centreLon = info.longitude();

        // Mix characterId and seed with two independent primes for lat/lon.
        // Bit-mix is deterministic and gives values spread over the full long range.
        long latBits = mix(characterId ^ 0x9E3779B97F4A7C15L, seed ^ 0x6C62272E07BB0142L);
        long lonBits = mix(characterId ^ 0xBF58476D1CE4E5B9L, seed ^ 0x94D049BB133111EBL);

        // Normalise to [-1, 1] via a 53-bit mantissa trick.
        double latFrac = (double)(latBits >>> 11) / (double)(1L << 53); // [0, 1)
        double lonFrac = (double)(lonBits >>> 11) / (double)(1L << 53);
        latFrac = latFrac * 2.0 - 1.0; // [-1, 1)
        lonFrac = lonFrac * 2.0 - 1.0;

        double lat = centreLat + latFrac * MAX_JITTER;
        double lon = centreLon + lonFrac * MAX_JITTER;

        // Clamp inside Georgia's bounding box
        lat = Math.max(LAT_MIN, Math.min(LAT_MAX, lat));
        lon = Math.max(LON_MIN, Math.min(LON_MAX, lon));

        return new double[]{lat, lon};
    }

    /** Finalisation mix (64-bit splitmix64 round). */
    private static long mix(long a, long b) {
        long z = a + b + 0x9e3779b97f4a7c15L;
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        return z ^ (z >>> 31);
    }
}
