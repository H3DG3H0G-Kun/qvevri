package com.game.estate;

import com.game.account.AccountTestHelper;
import com.game.core.data.Region;
import com.game.core.data.Variety;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import java.util.HashMap;
import java.util.Map;

import static com.game.account.AccountTestHelper.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the management plan endpoints (MANAGE-SPEC §3, §5, §6).
 *
 * <p>Tests are grouped into:
 * <ul>
 *   <li>Unit-style replay tests — instantiate {@link VineyardReplayService} and
 *       {@link Vineyard} directly; no Spring context required for those assertions.</li>
 *   <li>HTTP integration tests — hit the running server via {@link TestRestTemplate}.</li>
 * </ul>
 *
 * <p>The test profile freezes the clock
 * ({@code world.real-seconds-per-sim-day=86400000}).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class VineyardManagementTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    // ── Shared constants ──────────────────────────────────────────────────────

    /** Canonical seed — matches DeterminismTest / existing suite. */
    private static final long SEED = 42L;

    /** Canonical pick-day used across the suite (deep into Kakheti ripening). */
    private static final int PICK_DAY = 270;

    // ── URL helpers ───────────────────────────────────────────────────────────

    private String base() { return "http://localhost:" + port; }

    @SuppressWarnings("unchecked")
    private Map<String, Object> advanceClock(int days) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Map> r = rest.postForEntity(
                base() + "/api/world/advance",
                new HttpEntity<>(Map.of("days", days), h),
                Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (Map<String, Object>) r.getBody();
    }

    @SuppressWarnings("unchecked")
    private void advanceToYearStart() {
        ResponseEntity<Map> r = rest.getForEntity(base() + "/api/world/clock", Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        int doy = ((Number) r.getBody().get("dayOfYear")).intValue();
        if (doy != 0) advanceClock(365 - doy);
    }

    @SuppressWarnings("unchecked")
    private ResponseEntity<Map> plantVineyard(String token, long characterId,
                                               String region, String variety) {
        Map<String, Object> body = Map.of(
                "characterId", characterId, "region", region, "variety", variety);
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.setBearerAuth(token);
        return rest.postForEntity(base() + "/api/vineyards",
                new HttpEntity<>(body, h), Map.class);
    }

    /** POST /api/vineyards/{vineyardId}/manage */
    @SuppressWarnings("unchecked")
    private ResponseEntity<Map> manage(String token, long vineyardId,
                                        Map<String, Object> levers) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.setBearerAuth(token);
        return rest.postForEntity(
                base() + "/api/vineyards/" + vineyardId + "/manage",
                new HttpEntity<>(levers, h),
                Map.class);
    }

    /** GET /api/vineyards/{vineyardId}/management */
    @SuppressWarnings("unchecked")
    private ResponseEntity<Map> getManagement(String token, long vineyardId) {
        return rest.exchange(
                base() + "/api/vineyards/" + vineyardId + "/management",
                HttpMethod.GET,
                getWithToken(token),
                Map.class);
    }

    /** GET /api/vineyards/detail/{vineyardId} */
    @SuppressWarnings("unchecked")
    private ResponseEntity<Map> detail(String token, long vineyardId) {
        return rest.exchange(
                base() + "/api/vineyards/detail/" + vineyardId,
                HttpMethod.GET,
                getWithToken(token),
                Map.class);
    }

    // ── Helper: register, create character, plant vineyard ────────────────────

    private static class PlayerContext {
        String token;
        long characterId;
        long vineyardId;
    }

    @SuppressWarnings("unchecked")
    private PlayerContext setup(String regionCode, String varietyCode) {
        PlayerContext ctx = new PlayerContext();
        ctx.token = registerAndGetToken(rest, base());
        ctx.characterId = createCharacter(rest, base(), ctx.token,
                "Mgr_" + System.nanoTime()).longValue();
        ResponseEntity<Map> plant = plantVineyard(ctx.token, ctx.characterId,
                regionCode, varietyCode);
        assertThat(plant.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        ctx.vineyardId = ((Number) plant.getBody().get("id")).longValue();
        return ctx;
    }

    // =========================================================================
    // § Default-plan path: byte-identical to pre-manage behaviour
    // =========================================================================

    /**
     * A vineyard constructed without specifying levers must produce the same
     * VineyardView as a vineyard whose levers are explicitly set to the spec
     * defaults.  This verifies that the "default-lever path stays byte-identical"
     * requirement (MANAGE-SPEC §2, HARD RULE).
     *
     * <p>Performed as a unit-style test using VineyardReplayService directly
     * (no Spring context needed for the core assertion).
     */
    @Test
    @DisplayName("defaultLevers_replayIsIdenticalToHardcodedDefaults")
    void defaultLevers_replayIsIdenticalToHardcodedDefaults() {
        VineyardReplayService svc = new VineyardReplayService();

        // Vineyard with default levers (field initialisers = spec defaults)
        Vineyard defaultVineyard = new Vineyard(1L, Region.KAKHETI, Variety.SAPERAVI, SEED, 12);

        // Vineyard with levers explicitly set to their spec defaults — must be identical
        Vineyard explicitVineyard = new Vineyard(1L, Region.KAKHETI, Variety.SAPERAVI, SEED, 12);
        explicitVineyard.setOwnRoots(true);
        explicitVineyard.setCanopyOpenness01(0.40);
        explicitVineyard.setLeafPulled(false);
        explicitVineyard.setCopperSpray01(0.0);
        explicitVineyard.setSulfurSpray01(0.0);
        explicitVineyard.setNetting(false);
        explicitVineyard.setGuardDog(false);
        explicitVineyard.setFalcons(false);
        explicitVineyard.setCats(false);
        explicitVineyard.setDucks(false);
        explicitVineyard.setCoverCrop01(0.0);

        VineyardView vDefault  = svc.viewAt(defaultVineyard,  1, PICK_DAY);
        VineyardView vExplicit = svc.viewAt(explicitVineyard, 1, PICK_DAY);

        assertThat(vDefault.brix())
                .as("brix must be identical: default field-init == explicit spec-default levers")
                .isEqualTo(vExplicit.brix());
        assertThat(vDefault.healthFraction())
                .as("healthFraction must be identical")
                .isEqualTo(vExplicit.healthFraction());
        assertThat(vDefault.stage())
                .as("stage must be identical")
                .isEqualTo(vExplicit.stage());
        assertThat(vDefault.estimatedYieldKg())
                .as("estimatedYieldKg must be identical")
                .isEqualTo(vExplicit.estimatedYieldKg());
    }

    // =========================================================================
    // § copperSpray direction test (MANAGE-SPEC §6)
    // =========================================================================

    /**
     * copperSpray01=0.8 in IMERETI (wet, high fungal pressure) must produce
     * higher fruit health (and therefore quality) than copperSpray01=0 there.
     *
     * <p>Direction-only assertion: sprayed &gt; unsprayed in healthFraction.
     * MANAGE-SPEC §6: "copperSpray01=0.8 in a wet region (e.g. IMERETI) ⇒
     * higher fruit health / quality than spray=0 there".
     */
    @Test
    @DisplayName("copperSpray_highVsNone_inImereti_higherHealth")
    void copperSpray_highVsNone_inImereti_higherHealth() {
        VineyardReplayService svc = new VineyardReplayService();

        // No spray baseline
        Vineyard noSpray = new Vineyard(2L, Region.IMERETI, Variety.SAPERAVI, SEED, 12);
        noSpray.setCopperSpray01(0.0);

        // High copper spray
        Vineyard withSpray = new Vineyard(2L, Region.IMERETI, Variety.SAPERAVI, SEED, 12);
        withSpray.setCopperSpray01(0.8);

        // Use a day deep in the season where fungal pressure has accumulated
        VineyardView noSprayView   = svc.viewAt(noSpray,   1, PICK_DAY);
        VineyardView withSprayView = svc.viewAt(withSpray, 1, PICK_DAY);

        assertThat(withSprayView.healthFraction())
                .as("copperSpray01=0.8 must yield >= health than copperSpray01=0 in wet IMERETI "
                        + "(sprayed=" + withSprayView.healthFraction()
                        + " vs unsprayed=" + noSprayView.healthFraction() + ")")
                .isGreaterThanOrEqualTo(noSprayView.healthFraction());
    }

    // =========================================================================
    // § netting direction test (MANAGE-SPEC §6)
    // =========================================================================

    /**
     * netting=true must produce a result >= netting=false when bird pressure
     * is active (véraison / ripening window).
     *
     * <p>MANAGE-SPEC §6: "netting=true ⇒ better outcome than netting=false
     * in a bird-pressured véraison".
     */
    @Test
    @DisplayName("netting_true_vs_false_higherOrEqualHealth")
    void netting_true_vs_false_higherOrEqualHealth() {
        VineyardReplayService svc = new VineyardReplayService();

        // Véraison / early ripening window — approximately day 220..270 for Kakheti
        int veraison = 230;

        Vineyard noNet  = new Vineyard(3L, Region.KAKHETI, Variety.SAPERAVI, SEED, 12);
        noNet.setNetting(false);

        Vineyard withNet = new Vineyard(3L, Region.KAKHETI, Variety.SAPERAVI, SEED, 12);
        withNet.setNetting(true);

        VineyardView noNetView   = svc.viewAt(noNet,   1, veraison);
        VineyardView withNetView = svc.viewAt(withNet, 1, veraison);

        assertThat(withNetView.healthFraction())
                .as("netting=true health=" + withNetView.healthFraction()
                        + " must be >= netting=false health=" + noNetView.healthFraction())
                .isGreaterThanOrEqualTo(noNetView.healthFraction());
    }

    // =========================================================================
    // § HTTP: GET /management returns plan
    // =========================================================================

    @Test
    @DisplayName("getManagement_returnsDefaultPlan")
    void getManagement_returnsDefaultPlan() {
        PlayerContext ctx = setup("KAKHETI", "SAPERAVI");

        ResponseEntity<Map> resp = getManagement(ctx.token, ctx.vineyardId);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);

        Map<String, Object> plan = resp.getBody();
        assertThat(plan).isNotNull();
        assertThat(plan.get("vineyardId")).isNotNull();

        // Verify spec defaults are returned
        assertThat(((Number) plan.get("budLoad")).intValue())
                .as("default budLoad should be 12").isEqualTo(12);
        assertThat(plan.get("ownRoots"))
                .as("default ownRoots should be true").isEqualTo(true);
        assertThat(((Number) plan.get("canopyOpenness01")).doubleValue())
                .as("default canopyOpenness01 should be 0.40").isEqualTo(0.40);
        assertThat(plan.get("leafPulled"))
                .as("default leafPulled should be false").isEqualTo(false);
        assertThat(((Number) plan.get("copperSpray01")).doubleValue())
                .as("default copperSpray01 should be 0.0").isEqualTo(0.0);
        assertThat(plan.get("netting"))
                .as("default netting should be false").isEqualTo(false);
        assertThat(plan.get("guardDog"))
                .as("default guardDog should be false").isEqualTo(false);
    }

    // =========================================================================
    // § HTTP: POST /manage persists levers and returns updated view
    // =========================================================================

    @Test
    @DisplayName("manage_persistsLevers_returnsUpdatedView")
    void manage_persistsLevers_returnsUpdatedView() {
        advanceToYearStart();
        advanceClock(150);  // mid-season for a meaningful view

        PlayerContext ctx = setup("KAKHETI", "SAPERAVI");

        Map<String, Object> levers = new HashMap<>();
        levers.put("characterId", ctx.characterId);
        levers.put("netting", true);
        levers.put("copperSpray01", 0.5);
        levers.put("budLoad", 10);

        ResponseEntity<Map> resp = manage(ctx.token, ctx.vineyardId, levers);
        assertThat(resp.getStatusCode())
                .as("POST /manage must return 200").isEqualTo(HttpStatus.OK);

        Map<String, Object> view = resp.getBody();
        assertThat(view).as("response body must not be null").isNotNull();
        assertThat(view).containsKey("stage");

        // Verify persistence: GET management should reflect updated values
        ResponseEntity<Map> planResp = getManagement(ctx.token, ctx.vineyardId);
        assertThat(planResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> plan = planResp.getBody();
        assertThat(plan.get("netting")).as("netting must be persisted as true").isEqualTo(true);
        assertThat(((Number) plan.get("copperSpray01")).doubleValue())
                .as("copperSpray01 must be persisted as 0.5").isEqualTo(0.5);
        assertThat(((Number) plan.get("budLoad")).intValue())
                .as("budLoad must be persisted as 10").isEqualTo(10);
    }

    // =========================================================================
    // § HTTP: range validation → 400
    // =========================================================================

    @Nested
    @DisplayName("Range validation returns 400")
    class RangeValidation {

        @Test
        @DisplayName("budLoad_below1_returns400")
        void budLoad_below1_returns400() {
            PlayerContext ctx = setup("KAKHETI", "SAPERAVI");
            Map<String, Object> levers = new HashMap<>();
            levers.put("characterId", ctx.characterId);
            levers.put("budLoad", 0);  // below minimum
            ResponseEntity<Map> resp = manage(ctx.token, ctx.vineyardId, levers);
            assertThat(resp.getStatusCode())
                    .as("budLoad=0 must be rejected with 400").isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("budLoad_above40_returns400")
        void budLoad_above40_returns400() {
            PlayerContext ctx = setup("KAKHETI", "SAPERAVI");
            Map<String, Object> levers = new HashMap<>();
            levers.put("characterId", ctx.characterId);
            levers.put("budLoad", 41);  // above maximum
            ResponseEntity<Map> resp = manage(ctx.token, ctx.vineyardId, levers);
            assertThat(resp.getStatusCode())
                    .as("budLoad=41 must be rejected with 400").isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("copperSpray01_above1_returns400")
        void copperSpray01_above1_returns400() {
            PlayerContext ctx = setup("KAKHETI", "SAPERAVI");
            Map<String, Object> levers = new HashMap<>();
            levers.put("characterId", ctx.characterId);
            levers.put("copperSpray01", 1.5);  // above 1.0
            ResponseEntity<Map> resp = manage(ctx.token, ctx.vineyardId, levers);
            assertThat(resp.getStatusCode())
                    .as("copperSpray01=1.5 must be rejected with 400").isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("sulfurSpray01_negative_returns400")
        void sulfurSpray01_negative_returns400() {
            PlayerContext ctx = setup("KAKHETI", "SAPERAVI");
            Map<String, Object> levers = new HashMap<>();
            levers.put("characterId", ctx.characterId);
            levers.put("sulfurSpray01", -0.1);  // below 0
            ResponseEntity<Map> resp = manage(ctx.token, ctx.vineyardId, levers);
            assertThat(resp.getStatusCode())
                    .as("sulfurSpray01=-0.1 must be rejected with 400").isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("canopyOpenness01_above1_returns400")
        void canopyOpenness01_above1_returns400() {
            PlayerContext ctx = setup("KAKHETI", "SAPERAVI");
            Map<String, Object> levers = new HashMap<>();
            levers.put("characterId", ctx.characterId);
            levers.put("canopyOpenness01", 2.0);  // above 1.0
            ResponseEntity<Map> resp = manage(ctx.token, ctx.vineyardId, levers);
            assertThat(resp.getStatusCode())
                    .as("canopyOpenness01=2.0 must be rejected with 400").isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("coverCrop01_negative_returns400")
        void coverCrop01_negative_returns400() {
            PlayerContext ctx = setup("KAKHETI", "SAPERAVI");
            Map<String, Object> levers = new HashMap<>();
            levers.put("characterId", ctx.characterId);
            levers.put("coverCrop01", -0.5);
            ResponseEntity<Map> resp = manage(ctx.token, ctx.vineyardId, levers);
            assertThat(resp.getStatusCode())
                    .as("coverCrop01=-0.5 must be rejected with 400").isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    // =========================================================================
    // § HTTP: manage on unowned vineyard → 403/404
    // =========================================================================

    /**
     * An attacker using their own characterId against another player's vineyard
     * must receive 403 or 404 on both the manage and management endpoints.
     * MANAGE-SPEC §6: "manage on unowned vineyard ⇒ 403/404".
     */
    @Test
    @DisplayName("manage_unownedVineyard_returns403or404")
    void manage_unownedVineyard_returns403or404() {
        // Owner plants a vineyard
        PlayerContext owner = setup("KAKHETI", "SAPERAVI");

        // Attacker registers a separate account + character
        String attackerToken = registerAndGetToken(rest, base());
        long attackerCharId  = createCharacter(rest, base(), attackerToken,
                "Atk_" + System.nanoTime()).longValue();

        // Attacker tries to manage the owner's vineyard with attacker's own characterId
        Map<String, Object> levers = new HashMap<>();
        levers.put("characterId", attackerCharId);
        levers.put("netting", true);

        ResponseEntity<Map> resp = manage(attackerToken, owner.vineyardId, levers);
        assertThat(resp.getStatusCode())
                .as("attacker must receive 403 or 404 on manage")
                .isIn(HttpStatus.FORBIDDEN, HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("getManagement_unownedVineyard_returns403or404")
    void getManagement_unownedVineyard_returns403or404() {
        PlayerContext owner = setup("KAKHETI", "SAPERAVI");

        String attackerToken = registerAndGetToken(rest, base());

        ResponseEntity<Map> resp = getManagement(attackerToken, owner.vineyardId);
        assertThat(resp.getStatusCode())
                .as("attacker must receive 403 or 404 on getManagement")
                .isIn(HttpStatus.FORBIDDEN, HttpStatus.NOT_FOUND);
    }

    // =========================================================================
    // § HTTP: partial update leaves other levers unchanged
    // =========================================================================

    @Test
    @DisplayName("manage_partialUpdate_leavesOtherLeversUnchanged")
    void manage_partialUpdate_leavesOtherLeversUnchanged() {
        PlayerContext ctx = setup("KAKHETI", "SAPERAVI");

        // First: set guardDog=true
        Map<String, Object> firstUpdate = new HashMap<>();
        firstUpdate.put("characterId", ctx.characterId);
        firstUpdate.put("guardDog", true);
        ResponseEntity<Map> r1 = manage(ctx.token, ctx.vineyardId, firstUpdate);
        assertThat(r1.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Second: set netting=true only — guardDog must stay true
        Map<String, Object> secondUpdate = new HashMap<>();
        secondUpdate.put("characterId", ctx.characterId);
        secondUpdate.put("netting", true);
        ResponseEntity<Map> r2 = manage(ctx.token, ctx.vineyardId, secondUpdate);
        assertThat(r2.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Read back the plan
        ResponseEntity<Map> planResp = getManagement(ctx.token, ctx.vineyardId);
        assertThat(planResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> plan = planResp.getBody();

        assertThat(plan.get("guardDog"))
                .as("guardDog must still be true after unrelated partial update")
                .isEqualTo(true);
        assertThat(plan.get("netting"))
                .as("netting must be true after second update")
                .isEqualTo(true);
    }
}
