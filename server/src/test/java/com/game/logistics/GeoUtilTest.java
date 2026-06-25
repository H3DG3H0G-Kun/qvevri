package com.game.logistics;

import com.game.world.Region;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure unit tests for {@link GeoUtil} — no Spring context required.
 *
 * <p>Region coordinates used (from WorldCatalog):
 * <ul>
 *   <li>KAKHETI     (41.92, 45.47) — Telavi</li>
 *   <li>KARTLI      (41.98, 44.11) — Gori</li>
 *   <li>GURIA_ADJARA(41.64, 41.64) — Batumi</li>
 * </ul>
 */
class GeoUtilTest {

    // ── haversineKm ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("haversineKm_samePoint_returnsZero")
    void haversineKm_samePoint_returnsZero() {
        double dist = GeoUtil.haversineKm(41.92, 45.47, 41.92, 45.47);
        assertThat(dist).isLessThan(0.001);
    }

    @Test
    @DisplayName("haversineKm_kakhetiToKartli_approximatelyCorrect")
    void haversineKm_kakhetiToKartli_approximatelyCorrect() {
        // Telavi (41.92, 45.47) → Gori (41.98, 44.11) ≈ 100-130 km
        double dist = GeoUtil.haversineKm(41.92, 45.47, 41.98, 44.11);
        assertThat(dist)
                .as("Kakheti→Kartli haversine distance should be ~100-130 km")
                .isGreaterThan(80.0)
                .isLessThan(160.0);
    }

    @Test
    @DisplayName("haversineKm_symmetric")
    void haversineKm_symmetric() {
        double d1 = GeoUtil.haversineKm(41.92, 45.47, 41.64, 41.64);
        double d2 = GeoUtil.haversineKm(41.64, 41.64, 41.92, 45.47);
        assertThat(d1).isEqualTo(d2, org.assertj.core.data.Offset.offset(0.001));
    }

    // ── travelDays ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("travelDays_sameRegion_returnsMinimumOne")
    void travelDays_sameRegion_returnsMinimumOne() {
        int days = GeoUtil.travelDays(Region.KAKHETI, Region.KAKHETI);
        assertThat(days)
                .as("Same-region travel must cost at least 1 day")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("travelDays_kakhetiToGuriaAdjara_greaterThan_kakhetiToKartli")
    void travelDays_kakhetiToGuriaAdjara_greaterThan_kakhetiToKartli() {
        int kakhetiToKartli      = GeoUtil.travelDays(Region.KAKHETI, Region.KARTLI);
        int kakhetiToGuriaAdjara = GeoUtil.travelDays(Region.KAKHETI, Region.GURIA_ADJARA);

        assertThat(kakhetiToGuriaAdjara)
                .as("Kakheti→Guria/Adjara must take strictly more days than Kakheti→Kartli")
                .isGreaterThan(kakhetiToKartli);
    }

    @Test
    @DisplayName("travelDays_alwaysPositive")
    void travelDays_alwaysPositive() {
        for (Region from : Region.values()) {
            for (Region to : Region.values()) {
                int days = GeoUtil.travelDays(from, to);
                assertThat(days)
                        .as("travelDays(" + from + ", " + to + ") must be >= 1")
                        .isGreaterThanOrEqualTo(1);
            }
        }
    }

    @Test
    @DisplayName("travelDays_concreteValues_kakheti_kartli")
    void travelDays_concreteValues_kakheti_kartli() {
        // Kakheti (41.92,45.47) → Kartli (41.98,44.11) ≈ 108 km → ceil(108/40) = 3 days
        int days = GeoUtil.travelDays(Region.KAKHETI, Region.KARTLI);
        assertThat(days)
                .as("Kakheti→Kartli should be 2-5 travel days at 40 km/day")
                .isGreaterThanOrEqualTo(2)
                .isLessThanOrEqualTo(5);
    }
}
