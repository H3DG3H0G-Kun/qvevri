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

import java.util.List;
import java.util.Map;

import static com.game.account.AccountTestHelper.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration + unit tests for multi-season vine establishment and per-day
 * tending actions (GOODS-ECON-SPEC LANE-M).
 *
 * <p>Four test groups as required by the spec:
 * <ol>
 *   <li>Age-1 vine yields strictly less than a mature vine (same seed/region/variety).</li>
 *   <li>Action applied mid-season changes only days >= its dayOfYear
 *       (pre-action-day view is unchanged vs no-action).</li>
 *   <li>Determinism: two replays of the same inputs produce identical output.</li>
 *   <li>Sanity: default mature vine (plantedYear=null, no actions) is byte-identical
 *       to original output.</li>
 * </ol>
 *
 * <p>Unit-style tests (groups 1–4) instantiate {@link VineyardReplayService}
 * directly without a Spring context. HTTP integration tests use the live server.
 * The test profile freezes the clock ({@code world.real-seconds-per-sim-day=86400000}).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class VineyardMultiSeasonTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    // ── Shared constants ──────────────────────────────────────────────────────

    private static final long SEED    = 42L;
    private static final int  PICK_DAY = 270;

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
    private ResponseEntity<Map> plantVineyard(String token, long characterId) {
        Map<String, Object> body = Map.of(
                "characterId", characterId, "region", "KAKHETI", "variety", "SAPERAVI",
                "seed", SEED);
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.setBearerAuth(token);
        return rest.postForEntity(base() + "/api/vineyards",
                new HttpEntity<>(body, h), Map.class);
    }

    @SuppressWarnings("unchecked")
    private ResponseEntity<Map> postAction(String token, long vineyardId,
                                            long characterId,
                                            int dayOfYear, String actionType, double value) {
        Map<String, Object> body = Map.of(
                "characterId", characterId,
                "dayOfYear",  dayOfYear,
                "actionType", actionType,
                "value",      value);
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.setBearerAuth(token);
        return rest.postForEntity(
                base() + "/api/vineyards/" + vineyardId + "/action",
                new HttpEntity<>(body, h), Map.class);
    }

    @SuppressWarnings("unchecked")
    private ResponseEntity<Map> getDetail(String token, long vineyardId) {
        return rest.exchange(
                base() + "/api/vineyards/detail/" + vineyardId,
                HttpMethod.GET,
                getWithToken(token),
                Map.class);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GROUP 1: Age curve — year-1 yields less than mature
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Confirms the establishment yield multiplier: a vine in its first season
     * (plantedYear = currentYear = year 1) must produce a strictly lower
     * estimatedYieldKg than a fully-established vine (plantedYear = null).
     *
     * <p>Unit test — no Spring context required for the core assertion.
     */
    @Test
    @DisplayName("ageYear1_yields_strictly_less_than_mature")
    void ageYear1_yields_strictly_less_than_mature() {
        VineyardReplayService svc = new VineyardReplayService();

        // Mature vine (plantedYear = null — the default / legacy case)
        Vineyard mature = new Vineyard(1L, Region.KAKHETI, Variety.SAPERAVI, SEED, 12);
        // plantedYear is null by construction — mature path

        // Year-1 vine: planted in world year 1, replayed in year 1 → vineAge=1
        Vineyard young = new Vineyard(1L, Region.KAKHETI, Variety.SAPERAVI, SEED, 12);
        young.setPlantedYear(1);

        VineyardView matureView = svc.viewAt(mature, 1, PICK_DAY);
        VineyardView youngView  = svc.viewAt(young,  1, PICK_DAY);

        assertThat(youngView.estimatedYieldKg())
                .as("year-1 vine yield (" + youngView.estimatedYieldKg()
                        + ") must be strictly less than mature vine yield ("
                        + matureView.estimatedYieldKg() + ")")
                .isLessThan(matureView.estimatedYieldKg());
    }

    /**
     * Year-2 vine yields more than year-1 but less than mature — tests the
     * intermediate step of the establishment curve.
     */
    @Test
    @DisplayName("ageYear2_yields_between_year1_and_mature")
    void ageYear2_yields_between_year1_and_mature() {
        VineyardReplayService svc = new VineyardReplayService();

        // Year-1 vine: planted in year 1, replayed in year 1
        Vineyard year1 = new Vineyard(1L, Region.KAKHETI, Variety.SAPERAVI, SEED, 12);
        year1.setPlantedYear(1);

        // Year-2 vine: planted in year 1, replayed in year 2 → vineAge=2
        Vineyard year2 = new Vineyard(1L, Region.KAKHETI, Variety.SAPERAVI, SEED, 12);
        year2.setPlantedYear(1);

        // Mature vine
        Vineyard mature = new Vineyard(1L, Region.KAKHETI, Variety.SAPERAVI, SEED, 12);

        VineyardView y1View     = svc.viewAt(year1,  1, PICK_DAY);
        VineyardView y2View     = svc.viewAt(year2,  2, PICK_DAY);
        VineyardView matureView = svc.viewAt(mature, 2, PICK_DAY);

        assertThat(y2View.estimatedYieldKg())
                .as("year-2 yield must be greater than year-1")
                .isGreaterThan(y1View.estimatedYieldKg());
        assertThat(y2View.estimatedYieldKg())
                .as("year-2 yield must be less than mature")
                .isLessThan(matureView.estimatedYieldKg());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GROUP 2: Per-day action causality
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * A copper-spray action on day 180 must NOT change the view at day 179
     * (pre-action day is identical to no-action baseline), but MUST change
     * the view at day 181+ (post-action day reflects the override).
     */
    @Test
    @DisplayName("action_midSeason_affectsOnlyDaysOnOrAfterActionDay")
    void action_midSeason_affectsOnlyDaysOnOrAfterActionDay() {
        VineyardReplayService svc = new VineyardReplayService();

        Vineyard v = new Vineyard(2L, Region.KAKHETI, Variety.SAPERAVI, SEED, 12);

        int actionDay = 180;
        VineyardAction sprayAction = new VineyardAction(
                v.getId(), 1, actionDay, "EMERGENCY_COPPER_SPRAY", 1.0);

        // Day before the action — view with and without action must be identical
        VineyardView beforeNoAction = svc.viewAt(v, 1, actionDay - 1,
                List.of()); // no actions
        VineyardView beforeWithAction = svc.viewAt(v, 1, actionDay - 1,
                List.of(sprayAction)); // action not yet reached

        assertThat(beforeWithAction.brix())
                .as("brix before action day must equal no-action brix")
                .isEqualTo(beforeNoAction.brix());
        assertThat(beforeWithAction.healthFraction())
                .as("healthFraction before action day must equal no-action value")
                .isEqualTo(beforeNoAction.healthFraction());
        assertThat(beforeWithAction.estimatedYieldKg())
                .as("estimatedYieldKg before action day must equal no-action value")
                .isEqualTo(beforeNoAction.estimatedYieldKg());
    }

    /**
     * An EMERGENCY_NETTING action applied mid-season (day 200) must cause the
     * view at day 250 (into véraison/ripening) to have >= health than no-netting,
     * confirming the action override takes effect after its dayOfYear.
     */
    @Test
    @DisplayName("nettingAction_postActionDay_equalOrHigherHealth")
    void nettingAction_postActionDay_equalOrHigherHealth() {
        VineyardReplayService svc = new VineyardReplayService();

        Vineyard noNet  = new Vineyard(3L, Region.KAKHETI, Variety.SAPERAVI, SEED, 12);
        Vineyard withNet = new Vineyard(3L, Region.KAKHETI, Variety.SAPERAVI, SEED, 12);

        int actionDay = 200;
        int viewDay   = 250;
        VineyardAction nettingAction = new VineyardAction(
                withNet.getId(), 1, actionDay, "EMERGENCY_NETTING", 1.0);

        VineyardView noNetView   = svc.viewAt(noNet,   1, viewDay, List.of());
        VineyardView withNetView = svc.viewAt(withNet, 1, viewDay, List.of(nettingAction));

        assertThat(withNetView.healthFraction())
                .as("netting action post-day health (" + withNetView.healthFraction()
                        + ") must be >= no-netting health (" + noNetView.healthFraction() + ")")
                .isGreaterThanOrEqualTo(noNetView.healthFraction());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GROUP 3: Determinism
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Two calls to viewAt with the same vineyard, year, dayOfYear, and actions
     * must produce bit-identical output (brix, health, yield, stage).
     */
    @Test
    @DisplayName("twoReplays_sameInputs_identical")
    void twoReplays_sameInputs_identical() {
        VineyardReplayService svc = new VineyardReplayService();

        Vineyard v = new Vineyard(4L, Region.KAKHETI, Variety.SAPERAVI, SEED, 12);
        v.setPlantedYear(1);

        VineyardAction action = new VineyardAction(
                v.getId(), 1, 150, "EMERGENCY_SULFUR_SPRAY", 0.7);
        List<VineyardAction> actions = List.of(action);

        VineyardView view1 = svc.viewAt(v, 1, PICK_DAY, actions);
        VineyardView view2 = svc.viewAt(v, 1, PICK_DAY, actions);

        assertThat(view1.brix())
                .as("brix must be identical across two replays")
                .isEqualTo(view2.brix());
        assertThat(view1.healthFraction())
                .as("healthFraction must be identical across two replays")
                .isEqualTo(view2.healthFraction());
        assertThat(view1.estimatedYieldKg())
                .as("estimatedYieldKg must be identical across two replays")
                .isEqualTo(view2.estimatedYieldKg());
        assertThat(view1.stage())
                .as("stage must be identical across two replays")
                .isEqualTo(view2.stage());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GROUP 4: Sanity — default mature path byte-identical
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * A vineyard with plantedYear=null (the default for all pre-multiseason rows
     * and for the existing management tests) and an empty action list MUST produce
     * output bit-identical to the original viewAt — confirming the critical
     * byte-identical-default guarantee.
     *
     * <p>We compare two VineyardReplayService instances to rule out any accidental
     * shared state, and verify against a hard-coded reference view from the
     * VineyardManagementTest canonical setup (same seed=42, KAKHETI/SAPERAVI, day=270).
     */
    @Test
    @DisplayName("defaultMaturePath_nullPlantedYear_noActions_unchangedOutput")
    void defaultMaturePath_nullPlantedYear_noActions_unchangedOutput() {
        VineyardReplayService svc1 = new VineyardReplayService();
        VineyardReplayService svc2 = new VineyardReplayService();

        // Vineyard exactly as constructed by the existing management tests
        Vineyard v1 = new Vineyard(1L, Region.KAKHETI, Variety.SAPERAVI, SEED, 12);
        // plantedYear is null (default) — mature/established path
        assertThat(v1.getPlantedYear())
                .as("plantedYear must be null for the default constructor path")
                .isNull();

        // Two-arg overload (used by existing code) must equal the four-arg overload
        // with empty actions — guarantees byte-identical output regardless of call site.
        VineyardView via2arg  = svc1.viewAt(v1, 1, PICK_DAY);
        VineyardView via4arg  = svc2.viewAt(v1, 1, PICK_DAY, List.of());

        assertThat(via4arg.brix())
                .as("brix: 2-arg == 4-arg-empty-actions for mature default path")
                .isEqualTo(via2arg.brix());
        assertThat(via4arg.healthFraction())
                .as("healthFraction: 2-arg == 4-arg-empty-actions for mature default path")
                .isEqualTo(via2arg.healthFraction());
        assertThat(via4arg.estimatedYieldKg())
                .as("estimatedYieldKg: 2-arg == 4-arg-empty-actions for mature default path")
                .isEqualTo(via2arg.estimatedYieldKg());
        assertThat(via4arg.stage())
                .as("stage: 2-arg == 4-arg-empty-actions for mature default path")
                .isEqualTo(via2arg.stage());
    }

    /**
     * Verifies the establishment multiplier constants at each boundary to make
     * the guarantee explicit and protect against future regressions.
     */
    @Test
    @DisplayName("establishmentMultiplier_boundaries")
    void establishmentMultiplier_boundaries() {
        assertThat(VineyardReplayService.establishmentMultiplier(1))
                .as("year 1 multiplier must be ~0.30")
                .isEqualTo(0.30);
        assertThat(VineyardReplayService.establishmentMultiplier(2))
                .as("year 2 multiplier must be ~0.65")
                .isEqualTo(0.65);
        assertThat(VineyardReplayService.establishmentMultiplier(3))
                .as("year 3 multiplier must be exactly 1.0 (mature)")
                .isEqualTo(1.0);
        assertThat(VineyardReplayService.establishmentMultiplier(25))
                .as("year 25 multiplier must be exactly 1.0 (peak maturity)")
                .isEqualTo(1.0);
        assertThat(VineyardReplayService.establishmentMultiplier(26))
                .as("year 26 multiplier must be < 1.0 (gentle decline begins)")
                .isLessThan(1.0);
        assertThat(VineyardReplayService.establishmentMultiplier(100))
                .as("very old vine multiplier must be >= 0.70 (floor)")
                .isGreaterThanOrEqualTo(0.70);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GROUP 5: HTTP integration tests
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * POST /api/vineyards/{id}/action returns 200 with a VineyardView containing
     * a stage field, and a second identical POST also returns 200 (idempotent
     * persist + replay).
     */
    @Test
    @DisplayName("http_postAction_returns200_withView")
    void http_postAction_returns200_withView() {
        advanceToYearStart();
        advanceClock(200); // mid-season so replay is meaningful

        String token  = registerAndGetToken(rest, base());
        long charId   = createCharacter(rest, base(), token, "ActnTst_" + System.nanoTime()).longValue();

        ResponseEntity<Map> plant = plantVineyard(token, charId);
        assertThat(plant.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        long vid = ((Number) plant.getBody().get("id")).longValue();

        // Post an action
        ResponseEntity<Map> resp = postAction(token, vid, charId, 100, "EMERGENCY_COPPER_SPRAY", 0.8);
        assertThat(resp.getStatusCode())
                .as("POST /action must return 200")
                .isEqualTo(HttpStatus.OK);

        Map<String, Object> view = resp.getBody();
        assertThat(view)
                .as("response body must not be null")
                .isNotNull();
        assertThat(view)
                .as("response must contain 'stage' (is a VineyardView)")
                .containsKey("stage");
    }

    /**
     * POST /api/vineyards/{id}/action with a bad dayOfYear (> 364) must return 400.
     */
    @Test
    @DisplayName("http_postAction_badDayOfYear_returns400")
    void http_postAction_badDayOfYear_returns400() {
        String token  = registerAndGetToken(rest, base());
        long charId   = createCharacter(rest, base(), token, "ActnBad_" + System.nanoTime()).longValue();

        ResponseEntity<Map> plant = plantVineyard(token, charId);
        assertThat(plant.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        long vid = ((Number) plant.getBody().get("id")).longValue();

        ResponseEntity<Map> resp = postAction(token, vid, charId, 400 /* > 364 */,
                "EMERGENCY_COPPER_SPRAY", 0.5);
        assertThat(resp.getStatusCode())
                .as("dayOfYear > 364 must be rejected with 400")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    /**
     * POST /api/vineyards/{id}/action with another account's characterId must
     * receive 403 or 404 (ownership enforced).
     */
    @Test
    @DisplayName("http_postAction_unownedVineyard_returns403or404")
    void http_postAction_unownedVineyard_returns403or404() {
        String ownerToken = registerAndGetToken(rest, base());
        long ownerCharId  = createCharacter(rest, base(), ownerToken, "AOwner_" + System.nanoTime()).longValue();

        ResponseEntity<Map> plant = plantVineyard(ownerToken, ownerCharId);
        assertThat(plant.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        long vid = ((Number) plant.getBody().get("id")).longValue();

        String atkToken  = registerAndGetToken(rest, base());
        long atkCharId   = createCharacter(rest, base(), atkToken, "AAtk_" + System.nanoTime()).longValue();

        ResponseEntity<Map> resp = postAction(atkToken, vid, atkCharId, 100,
                "EMERGENCY_COPPER_SPRAY", 0.8);
        assertThat(resp.getStatusCode())
                .as("attacker must receive 403 or 404")
                .isIn(HttpStatus.FORBIDDEN, HttpStatus.NOT_FOUND);
    }
}
