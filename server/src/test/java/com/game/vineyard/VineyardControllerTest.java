package com.game.vineyard;

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
 * Integration tests for POST /api/vineyard/simulate (VINEYARD-API §1).
 *
 * <p>Exercises the full Spring context (security filter chain, validation,
 * controller, simulation pipeline) via a real HTTP server on a random port.
 * The test profile uses in-memory H2 so no external DB is needed.
 *
 * <p>The endpoint is {@code permitAll} (§0: "No auth required") so all
 * calls here are intentionally made WITHOUT an Authorization header.
 *
 * <p>The code-under-test (VineyardController) is written in parallel by lane VA.
 * Tests are coded against the literal contract; if the controller is not yet
 * deployed all @Test methods will fail with 404 — that is the correct signal.
 * Never weaken a test to make it pass.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class VineyardControllerTest {

    private static final String ENDPOINT = "/api/vineyard/simulate";

    @LocalServerPort
    int port;

    /** TestRestTemplate is pre-configured by Spring Boot test slice (no auth). */
    @Autowired
    TestRestTemplate rest;

    // ── Helpers ──────────────────────────────────────────────────────────────

    private String url() {
        return "http://localhost:" + port + ENDPOINT;
    }

    /**
     * POST the given body (as JSON string) WITHOUT an Authorization header.
     * Uses raw Map response so assertions remain independent of
     * whether VineyardYearResult is on the test classpath.
     */
    @SuppressWarnings("unchecked")
    private ResponseEntity<Map> post(String jsonBody) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(jsonBody, headers);
        return rest.postForEntity(url(), entity, Map.class);
    }

    /** Convenience: POST with a fully populated default-seed request. */
    private ResponseEntity<Map> postDefault() {
        return post("""
                {
                  "seed": 42,
                  "variety": "SAPERAVI",
                  "soil": "HUMUS_CARBONATE",
                  "budLoad": 12,
                  "pickDay": 270,
                  "threats": true
                }
                """);
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    /**
     * simulate_defaultRequest_returns200_withBottle
     *
     * <p>POST a standard request (or empty body with defaults) and verify:
     * <ul>
     *   <li>HTTP 200</li>
     *   <li>{@code bottle.quality} in [0, 100]</li>
     *   <li>{@code bottle.style == "RED"} (SAPERAVI + FermentMethod.RED)</li>
     *   <li>{@code bottle.abv} in (0, 18)</li>
     *   <li>{@code bottle.volumeL > 0}</li>
     *   <li>{@code events} is non-empty</li>
     * </ul>
     *
     * <p>Contract ref: VINEYARD-API §1 (200 OK body shape, §0 SAPERAVI is always RED).
     */
    @Test
    @DisplayName("simulate_defaultRequest_returns200_withBottle")
    void simulate_defaultRequest_returns200_withBottle() {
        ResponseEntity<Map> resp = postDefault();

        assertThat(resp.getStatusCode())
                .as("POST /api/vineyard/simulate must return 200")
                .isEqualTo(HttpStatus.OK);

        Map<?, ?> body = resp.getBody();
        assertThat(body).as("Response body must not be null").isNotNull();

        // bottle sub-object
        Map<?, ?> bottle = (Map<?, ?>) body.get("bottle");
        assertThat(bottle).as("Response must contain a 'bottle' object").isNotNull();

        Number quality = (Number) bottle.get("quality");
        assertThat(quality).as("bottle.quality must be present").isNotNull();
        double q = quality.doubleValue();
        assertThat(q)
                .as("bottle.quality must be in [0, 100] per VINEYARD-API §1")
                .isGreaterThanOrEqualTo(0.0)
                .isLessThanOrEqualTo(100.0);

        String style = (String) bottle.get("style");
        assertThat(style)
                .as("bottle.style must be RED for SAPERAVI + FermentMethod.RED")
                .isEqualTo("RED");

        Number abv = (Number) bottle.get("abv");
        assertThat(abv).as("bottle.abv must be present").isNotNull();
        double abvVal = abv.doubleValue();
        assertThat(abvVal)
                .as("bottle.abv must be in (0, 18) per VINEYARD-API §1")
                .isGreaterThan(0.0)
                .isLessThan(18.0);

        Number volumeL = (Number) bottle.get("volumeL");
        assertThat(volumeL).as("bottle.volumeL must be present").isNotNull();
        assertThat(volumeL.doubleValue())
                .as("bottle.volumeL must be > 0")
                .isGreaterThan(0.0);

        // events list
        List<?> events = (List<?>) body.get("events");
        assertThat(events)
                .as("Response must contain a non-empty 'events' list (phenology + threat log)")
                .isNotNull()
                .isNotEmpty();
    }

    /**
     * simulate_isDeterministic_sameSeedSameResult
     *
     * <p>POST identical requests twice → identical numeric fields.
     * VINEYARD-API §4: "Same request → identical VineyardYearResult."
     * The sim is deterministic from the seed; the endpoint must not introduce
     * wall-clock or external RNG.
     *
     * <p>Exact double equality is required for the determinism assertion
     * (no tolerance) per the task spec.
     */
    @Test
    @DisplayName("simulate_isDeterministic_sameSeedSameResult")
    void simulate_isDeterministic_sameSeedSameResult() {
        ResponseEntity<Map> first  = postDefault();
        ResponseEntity<Map> second = postDefault();

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);

        Map<?, ?> b1 = first.getBody();
        Map<?, ?> b2 = second.getBody();
        assertThat(b1).isNotNull();
        assertThat(b2).isNotNull();

        // Compare bottle fields exactly
        Map<?, ?> bottle1 = (Map<?, ?>) b1.get("bottle");
        Map<?, ?> bottle2 = (Map<?, ?>) b2.get("bottle");
        assertThat(bottle1).isNotNull();
        assertThat(bottle2).isNotNull();

        double q1 = ((Number) bottle1.get("quality")).doubleValue();
        double q2 = ((Number) bottle2.get("quality")).doubleValue();
        assertThat(q1)
                .as("bottle.quality must be identical across same-seed runs (VINEYARD-API §4)")
                .isEqualTo(q2);

        double abv1 = ((Number) bottle1.get("abv")).doubleValue();
        double abv2 = ((Number) bottle2.get("abv")).doubleValue();
        assertThat(abv1)
                .as("bottle.abv must be identical across same-seed runs")
                .isEqualTo(abv2);

        // Compare must.brix exactly — catches upstream non-determinism masked by cellar/resolver
        Map<?, ?> must1 = (Map<?, ?>) b1.get("must");
        Map<?, ?> must2 = (Map<?, ?>) b2.get("must");
        assertThat(must1).isNotNull();
        assertThat(must2).isNotNull();

        double brix1 = ((Number) must1.get("brix")).doubleValue();
        double brix2 = ((Number) must2.get("brix")).doubleValue();
        assertThat(brix1)
                .as("must.brix must be identical across same-seed runs")
                .isEqualTo(brix2);
    }

    /**
     * simulate_threatsOff_vs_on_differ
     *
     * <p>Same seed, threats=false vs threats=true → results differ (at least
     * bottle.quality differs) for a seed where threats fire.
     *
     * <p>Seed 42 with pickDay=270 is the canonical combination; the default
     * threat levers (ownRoots=true, no sprays, no netting) should cause at
     * least one threat event that changes fruitHealth01 and thereby quality.
     *
     * <p>IMPORTANT: If threats happen not to fire for seed=42 (a valid but
     * unlikely outcome), this test will fail. The correct response is to
     * investigate whether ThreatEngine is running, not to weaken the assertion.
     * The spec requires that threats=true vs threats=false produce different
     * outcomes for seeds where threats fire.
     */
    @Test
    @DisplayName("simulate_threatsOff_vs_on_differ")
    void simulate_threatsOff_vs_on_differ() {
        // Seed chosen to be the canonical seed; threats should fire at default levers.
        String bodyOn = """
                {
                  "seed": 42,
                  "variety": "SAPERAVI",
                  "soil": "HUMUS_CARBONATE",
                  "budLoad": 12,
                  "pickDay": 270,
                  "threats": true
                }
                """;
        String bodyOff = """
                {
                  "seed": 42,
                  "variety": "SAPERAVI",
                  "soil": "HUMUS_CARBONATE",
                  "budLoad": 12,
                  "pickDay": 270,
                  "threats": false
                }
                """;

        ResponseEntity<Map> respOn  = post(bodyOn);
        ResponseEntity<Map> respOff = post(bodyOff);

        assertThat(respOn.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respOff.getStatusCode()).isEqualTo(HttpStatus.OK);

        Map<?, ?> bottleOn  = (Map<?, ?>) respOn.getBody().get("bottle");
        Map<?, ?> bottleOff = (Map<?, ?>) respOff.getBody().get("bottle");
        assertThat(bottleOn).isNotNull();
        assertThat(bottleOff).isNotNull();

        double qualityOn  = ((Number) bottleOn.get("quality")).doubleValue();
        double qualityOff = ((Number) bottleOff.get("quality")).doubleValue();

        assertThat(qualityOn)
                .as("threats=true vs threats=false must produce different bottle.quality "
                        + "(threats fired on seed=42 should reduce fruitHealth01 → lower quality); "
                        + "on=%.4f off=%.4f".formatted(qualityOn, qualityOff))
                .isNotEqualTo(qualityOff);
    }

    /**
     * simulate_invalidBudLoad_returns400
     *
     * <p>budLoad=999 is outside the valid range 1..40 (VINEYARD-API §1 validation).
     * Expect 400 BAD_REQUEST and the standard error envelope with {@code error.code} present.
     */
    @Test
    @DisplayName("simulate_invalidBudLoad_returns400")
    void simulate_invalidBudLoad_returns400() {
        ResponseEntity<Map> resp = post("""
                {
                  "seed": 42,
                  "variety": "SAPERAVI",
                  "soil": "HUMUS_CARBONATE",
                  "budLoad": 999,
                  "pickDay": 270,
                  "threats": false
                }
                """);

        assertThat(resp.getStatusCode())
                .as("budLoad=999 must return 400 BAD_REQUEST per VINEYARD-API §1 validation")
                .isEqualTo(HttpStatus.BAD_REQUEST);

        Map<?, ?> body = resp.getBody();
        assertThat(body).as("Error response body must not be null").isNotNull();

        // Standard error envelope per API.md §1: { "error": { "code": "...", "message": "..." } }
        @SuppressWarnings("unchecked")
        Map<String, Object> error = (Map<String, Object>) body.get("error");
        assertThat(error)
                .as("400 response must contain a nested 'error' object with 'code'")
                .isNotNull()
                .containsKey("code");

        String code = (String) error.get("code");
        assertThat(code)
                .as("error.code must be a non-blank string")
                .isNotNull()
                .isNotBlank();
    }

    /**
     * simulate_noAuthRequired
     *
     * <p>VINEYARD-API §0: "No auth required for this compute endpoint."
     * The request has no Authorization header; the endpoint must be
     * {@code permitAll} in the security config.
     *
     * <p>This test intentionally duplicates the plausibility check from
     * {@link #simulate_defaultRequest_returns200_withBottle()} with an
     * explicit assertion that 401 must NOT be returned — keeping the auth
     * assertion separate and self-documenting.
     */
    @Test
    @DisplayName("simulate_noAuthRequired")
    void simulate_noAuthRequired() {
        // Post with NO Authorization header (TestRestTemplate starts unauthenticated).
        ResponseEntity<Map> resp = postDefault();

        assertThat(resp.getStatusCode())
                .as("POST /api/vineyard/simulate must succeed WITHOUT an Authorization header "
                        + "(endpoint is permitAll per VINEYARD-API §0); "
                        + "got status " + resp.getStatusCode())
                .isNotEqualTo(HttpStatus.UNAUTHORIZED)
                .isEqualTo(HttpStatus.OK);
    }
}
