package com.game.world;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for GET /api/world/regions and GET /api/world/careers
 * (MMO-CORE-SPEC §3 and §4 — World endpoints, permitAll).
 *
 * The spec defines:
 *  - 7 regions: KAKHETI, KARTLI, IMERETI, RACHA_LECHKHUMI, SAMEGRELO, GURIA_ADJARA, MESKHETI
 *  - 9 career types: GROWER, WINEMAKER, ENOLOGIST, NEGOCIANT, BROKER, COOPER,
 *                     NURSERYMAN, HAULER, MERCHANT
 *
 * Both endpoints are permitAll — no Authorization header needed.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class WorldControllerTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    private String base() {
        return "http://localhost:" + port;
    }

    // ── regions ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("regions_returns7")
    @SuppressWarnings("unchecked")
    void regions_returns7() {
        ResponseEntity<List> resp = rest.getForEntity(
                base() + "/api/world/regions", List.class);

        assertThat(resp.getStatusCode())
                .as("GET /api/world/regions must return 200")
                .isEqualTo(HttpStatus.OK);

        List<?> regions = resp.getBody();
        assertThat(regions)
                .as("GET /api/world/regions must return exactly 7 entries (MMO-CORE-SPEC §3)")
                .isNotNull()
                .hasSize(7);
    }

    @Test
    @DisplayName("regions_containsAllDocumentedNames")
    @SuppressWarnings("unchecked")
    void regions_containsAllDocumentedNames() {
        ResponseEntity<List> resp = rest.getForEntity(
                base() + "/api/world/regions", List.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);

        List<?> regions = resp.getBody();
        assertThat(regions).isNotNull();

        // Each element should be a map; collect the 'region' discriminator values
        List<String> regionNames = regions.stream()
                .map(r -> (String) ((java.util.Map<?, ?>) r).get("region"))
                .toList();

        assertThat(regionNames).contains(
                "KAKHETI", "KARTLI", "IMERETI", "RACHA_LECHKHUMI",
                "SAMEGRELO", "GURIA_ADJARA", "MESKHETI");
    }

    @Test
    @DisplayName("regions_noAuthRequired")
    void regions_noAuthRequired() {
        // No Authorization header — must still return 200
        ResponseEntity<List> resp = rest.getForEntity(
                base() + "/api/world/regions", List.class);

        assertThat(resp.getStatusCode())
                .as("GET /api/world/regions must be permitAll (no auth needed)")
                .isNotEqualTo(HttpStatus.UNAUTHORIZED)
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("regions_haveLatitudeLongitudeFields")
    @SuppressWarnings("unchecked")
    void regions_haveLatitudeLongitudeFields() {
        ResponseEntity<List> resp = rest.getForEntity(
                base() + "/api/world/regions", List.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);

        List<?> regions = resp.getBody();
        assertThat(regions).isNotNull().hasSize(7);

        for (Object entry : regions) {
            Map<String, Object> region = (Map<String, Object>) entry;
            assertThat(region)
                    .as("Region entry must contain 'latitude' field")
                    .containsKey("latitude");
            assertThat(region)
                    .as("Region entry must contain 'longitude' field")
                    .containsKey("longitude");
        }
    }

    @Test
    @DisplayName("regions_coordinatesInsideGeorgiaBoundingBox")
    @SuppressWarnings("unchecked")
    void regions_coordinatesInsideGeorgiaBoundingBox() {
        ResponseEntity<List> resp = rest.getForEntity(
                base() + "/api/world/regions", List.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);

        List<?> regions = resp.getBody();
        assertThat(regions).isNotNull().hasSize(7);

        for (Object entry : regions) {
            Map<String, Object> region = (Map<String, Object>) entry;
            String regionName = (String) region.get("region");

            double lat = ((Number) region.get("latitude")).doubleValue();
            double lon = ((Number) region.get("longitude")).doubleValue();

            assertThat(lat)
                    .as("Region %s latitude must be within Georgia's bounding box [41.0, 43.6]", regionName)
                    .isBetween(41.0, 43.6);
            assertThat(lon)
                    .as("Region %s longitude must be within Georgia's bounding box [40.0, 46.8]", regionName)
                    .isBetween(40.0, 46.8);
        }
    }

    // ── careers ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("careers_returns9")
    @SuppressWarnings("unchecked")
    void careers_returns9() {
        ResponseEntity<List> resp = rest.getForEntity(
                base() + "/api/world/careers", List.class);

        assertThat(resp.getStatusCode())
                .as("GET /api/world/careers must return 200")
                .isEqualTo(HttpStatus.OK);

        List<?> careers = resp.getBody();
        assertThat(careers)
                .as("GET /api/world/careers must return exactly 9 entries (MMO-CORE-SPEC §3)")
                .isNotNull()
                .hasSize(9);
    }

    @Test
    @DisplayName("careers_containsAllDocumentedNames")
    @SuppressWarnings("unchecked")
    void careers_containsAllDocumentedNames() {
        ResponseEntity<List> resp = rest.getForEntity(
                base() + "/api/world/careers", List.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);

        List<?> careers = resp.getBody();
        assertThat(careers).isNotNull();

        List<String> careerNames = careers.stream()
                .map(c -> (String) ((java.util.Map<?, ?>) c).get("type"))
                .toList();

        assertThat(careerNames).contains(
                "GROWER", "WINEMAKER", "ENOLOGIST", "NEGOCIANT",
                "BROKER", "COOPER", "NURSERYMAN", "HAULER", "MERCHANT");
    }

    @Test
    @DisplayName("careers_noAuthRequired")
    void careers_noAuthRequired() {
        ResponseEntity<List> resp = rest.getForEntity(
                base() + "/api/world/careers", List.class);

        assertThat(resp.getStatusCode())
                .as("GET /api/world/careers must be permitAll (no auth needed)")
                .isNotEqualTo(HttpStatus.UNAUTHORIZED)
                .isEqualTo(HttpStatus.OK);
    }
}
