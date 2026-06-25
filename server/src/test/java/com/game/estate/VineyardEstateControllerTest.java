package com.game.estate;

import com.game.account.AccountTestHelper;
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

import static com.game.account.AccountTestHelper.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the living-vineyard estate endpoints (WA2 lane, WORLD-CLOCK-SPEC §4).
 *
 * <p>The test profile freezes the auto-scheduler
 * ({@code world.real-seconds-per-sim-day=86400000}). All time progression is via
 * POST /api/world/advance.
 *
 * <p>The code under test ({@code com.game.estate}) is written in parallel by WA2.
 * Until WA2 delivers, all tests that hit /api/vineyards/** will fail with 404
 * or 405 — that is the correct signal; never weaken a test to make it pass.
 *
 * <p>Date-math constants: absoluteDay = (year-1)*365 + dayOfYear.
 * "Ripe" = stage RIPENING AND brix >= 22. From a fresh year start (day 0),
 * approximately 270 days covers the Kakheti Saperavi ripening window per the sim.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class VineyardEstateControllerTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    private String base() {
        return "http://localhost:" + port;
    }

    // ── HTTP helpers ──────────────────────────────────────────────────────────

    /** GET /api/world/clock */
    @SuppressWarnings("unchecked")
    private Map<String, Object> getClock() {
        ResponseEntity<Map> r = rest.getForEntity(base() + "/api/world/clock", Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (Map<String, Object>) r.getBody();
    }

    /** POST /api/world/advance { days: n } */
    @SuppressWarnings("unchecked")
    private Map<String, Object> advanceClock(int days) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> body = Map.of("days", days);
        ResponseEntity<Map> r = rest.postForEntity(
                base() + "/api/world/advance",
                new HttpEntity<>(body, h),
                Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (Map<String, Object>) r.getBody();
    }

    /** Advance clock to the next year-start (dayOfYear == 0). */
    private void advanceToYearStart() {
        Map<String, Object> clock = getClock();
        int doy = ((Number) clock.get("dayOfYear")).intValue();
        if (doy != 0) {
            advanceClock(365 - doy);
        }
        assertThat(((Number) getClock().get("dayOfYear")).intValue())
                .as("dayOfYear must be 0 after advancing to year start")
                .isEqualTo(0);
    }

    /**
     * POST /api/vineyards { characterId, region, variety }
     * Returns 201 + the created vineyard entity.
     */
    @SuppressWarnings("unchecked")
    private ResponseEntity<Map> plantVineyard(String token, long characterId,
                                               String region, String variety) {
        Map<String, Object> body = Map.of(
                "characterId", characterId,
                "region", region,
                "variety", variety);
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.setBearerAuth(token);
        return rest.postForEntity(
                base() + "/api/vineyards",
                new HttpEntity<>(body, h),
                Map.class);
    }

    /**
     * GET /api/vineyards/{characterId} → VineyardView[]
     */
    @SuppressWarnings("unchecked")
    private ResponseEntity<List> listVineyards(String token, long characterId) {
        return rest.exchange(
                base() + "/api/vineyards/" + characterId,
                HttpMethod.GET,
                getWithToken(token),
                List.class);
    }

    /**
     * GET /api/vineyards/detail/{vineyardId} → single VineyardView
     */
    @SuppressWarnings("unchecked")
    private ResponseEntity<Map> getVineyardDetail(String token, long vineyardId) {
        return rest.exchange(
                base() + "/api/vineyards/detail/" + vineyardId,
                HttpMethod.GET,
                getWithToken(token),
                Map.class);
    }

    /**
     * POST /api/vineyards/{vineyardId}/harvest { characterId }
     */
    @SuppressWarnings("unchecked")
    private ResponseEntity<Map> harvest(String token, long vineyardId, long characterId) {
        Map<String, Object> body = Map.of("characterId", characterId);
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.setBearerAuth(token);
        return rest.postForEntity(
                base() + "/api/vineyards/" + vineyardId + "/harvest",
                new HttpEntity<>(body, h),
                Map.class);
    }

    /**
     * GET /api/cellar/{characterId} → CellarItem[]
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getCellar(String token, long characterId) {
        ResponseEntity<List> r = rest.exchange(
                base() + "/api/cellar/" + characterId,
                HttpMethod.GET,
                getWithToken(token),
                List.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody()).isNotNull();
        return (List<Map<String, Object>>) r.getBody();
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    /**
     * plant_returns201_growing
     *
     * POST /api/vineyards with a valid characterId, region, variety must return
     * 201 Created with a body that includes an id and status=GROWING.
     *
     * WORLD-CLOCK-SPEC §4: POST /api/vineyards → 201 Vineyard.
     */
    @Test
    @DisplayName("plant_returns201_growing")
    @SuppressWarnings("unchecked")
    void plant_returns201_growing() {
        String token   = registerAndGetToken(rest, base());
        Number charId  = createCharacter(rest, base(), token, "Nino_" + System.nanoTime());
        long cid       = charId.longValue();

        ResponseEntity<Map> resp = plantVineyard(token, cid, "KAKHETI", "SAPERAVI");

        assertThat(resp.getStatusCode())
                .as("POST /api/vineyards must return 201")
                .isEqualTo(HttpStatus.CREATED);

        Map<String, Object> body = resp.getBody();
        assertThat(body).as("response body must not be null").isNotNull();
        assertThat(body).as("response must contain 'id'").containsKey("id");
        assertThat(body).as("response must contain 'status'").containsKey("status");

        String status = (String) body.get("status");
        assertThat(status)
                .as("newly planted vineyard must have status GROWING")
                .isEqualTo("GROWING");
    }

    /**
     * list_showsPlantedVineyard_atCurrentDay
     *
     * After planting, GET /api/vineyards/{characterId} must return at least one
     * entry containing the planted vineyard's id with a non-null stage field.
     *
     * WORLD-CLOCK-SPEC §4: GET /api/vineyards/{characterId} → VineyardView[].
     */
    @Test
    @DisplayName("list_showsPlantedVineyard_atCurrentDay")
    @SuppressWarnings("unchecked")
    void list_showsPlantedVineyard_atCurrentDay() {
        String token  = registerAndGetToken(rest, base());
        Number charId = createCharacter(rest, base(), token, "Giorgi_" + System.nanoTime());
        long cid      = charId.longValue();

        ResponseEntity<Map> plant = plantVineyard(token, cid, "KAKHETI", "SAPERAVI");
        assertThat(plant.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        long vid = ((Number) plant.getBody().get("id")).longValue();

        ResponseEntity<List> listResp = listVineyards(token, cid);
        assertThat(listResp.getStatusCode())
                .as("GET /api/vineyards/{characterId} must return 200")
                .isEqualTo(HttpStatus.OK);

        List<?> views = listResp.getBody();
        assertThat(views)
                .as("list must contain at least the newly planted vineyard")
                .isNotNull()
                .isNotEmpty();

        // Find our vineyard in the list
        boolean found = views.stream()
                .map(v -> (Map<?, ?>) v)
                .anyMatch(v -> v.containsKey("vineyardId")
                        && ((Number) v.get("vineyardId")).longValue() == vid);

        assertThat(found)
                .as("planted vineyard id=" + vid + " must appear in the list view")
                .isTrue();

        // Each view must have a stage field
        @SuppressWarnings("unchecked")
        Map<String, Object> firstView = views.stream()
                .map(v -> (Map<String, Object>) v)
                .filter(v -> v.containsKey("vineyardId")
                        && ((Number) v.get("vineyardId")).longValue() == vid)
                .findFirst().orElseThrow();
        assertThat(firstView)
                .as("VineyardView must contain a 'stage' field")
                .containsKey("stage");
    }

    /**
     * advancingClock_changesView
     *
     * Capture brix and stage at an early day; advance ~150 days; verify that
     * brix is higher (or equal — vines may plateau) and/or stage is later.
     *
     * WORLD-CLOCK-SPEC §7: "advancing the clock changes the view (later stage/higher brix)".
     */
    @Test
    @DisplayName("advancingClock_changesView")
    @SuppressWarnings("unchecked")
    void advancingClock_changesView() {
        // Start from a clean year so the advance puts us in the growing season
        advanceToYearStart();

        String token  = registerAndGetToken(rest, base());
        Number charId = createCharacter(rest, base(), token, "Tamar_" + System.nanoTime());
        long cid      = charId.longValue();

        ResponseEntity<Map> plant = plantVineyard(token, cid, "KAKHETI", "SAPERAVI");
        assertThat(plant.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        long vid = ((Number) plant.getBody().get("id")).longValue();

        // Capture view at day 0 (year start — vine in dormancy/early growth)
        ResponseEntity<Map> early = getVineyardDetail(token, vid);
        assertThat(early.getStatusCode())
                .as("GET /api/vineyards/detail/{id} must return 200")
                .isEqualTo(HttpStatus.OK);
        Map<String, Object> earlyView = early.getBody();
        assertThat(earlyView).as("early view must not be null").isNotNull();

        String earlyStage = (String) earlyView.get("stage");
        double earlyBrix  = earlyView.containsKey("brix")
                ? ((Number) earlyView.get("brix")).doubleValue()
                : 0.0;

        // Advance 150 sim-days into the season
        advanceClock(150);

        ResponseEntity<Map> later = getVineyardDetail(token, vid);
        assertThat(later.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> laterView = later.getBody();
        assertThat(laterView).as("later view must not be null").isNotNull();

        String laterStage = (String) laterView.get("stage");
        double laterBrix  = laterView.containsKey("brix")
                ? ((Number) laterView.get("brix")).doubleValue()
                : 0.0;

        // Either brix has increased, or stage has progressed (or both)
        boolean brixIncreased  = laterBrix > earlyBrix;
        boolean stageProgressed = stageIsLaterOrEqual(earlyStage, laterStage)
                && !laterStage.equals(earlyStage);

        assertThat(brixIncreased || stageProgressed)
                .as("After advancing 150 days, brix must be higher or stage must advance. "
                        + "earlyStage=" + earlyStage + " earlyBrix=" + earlyBrix
                        + " laterStage=" + laterStage + " laterBrix=" + laterBrix)
                .isTrue();
    }

    /**
     * harvest_whenRipe_depositsBottleInCellar
     *
     * From a year start, advance ~270 days to put the vine deep into ripening.
     * Then harvest; expect 200 and a new bottle in GET /api/cellar/{characterId}.
     *
     * WORLD-CLOCK-SPEC §4: harvest → deposit CellarItem; §3: ripe = RIPENING + brix>=22.
     */
    @Test
    @DisplayName("harvest_whenRipe_depositsBottleInCellar")
    @SuppressWarnings("unchecked")
    void harvest_whenRipe_depositsBottleInCellar() {
        advanceToYearStart();

        String token  = registerAndGetToken(rest, base());
        Number charId = createCharacter(rest, base(), token, "David_" + System.nanoTime());
        long cid      = charId.longValue();

        ResponseEntity<Map> plant = plantVineyard(token, cid, "KAKHETI", "SAPERAVI");
        assertThat(plant.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        long vid = ((Number) plant.getBody().get("id")).longValue();

        // 270 days from year start puts us well into Kakheti ripening
        advanceClock(270);

        // Verify the vine is ripe before harvesting
        ResponseEntity<Map> detail = getVineyardDetail(token, vid);
        assertThat(detail.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> view = detail.getBody();
        assertThat(view).isNotNull();
        boolean ripe = view.containsKey("ripe") && Boolean.TRUE.equals(view.get("ripe"));
        // If not ripe yet, push forward a bit more
        if (!ripe) {
            advanceClock(40); // extra 40 days — total 310
            detail = getVineyardDetail(token, vid);
            view   = detail.getBody();
            assertThat(view).isNotNull();
        }

        // Count cellar before
        List<Map<String, Object>> before = getCellar(token, cid);
        int sizeBefore = before.size();

        // Harvest
        ResponseEntity<Map> harvestResp = harvest(token, vid, cid);
        assertThat(harvestResp.getStatusCode())
                .as("harvest of a ripe vineyard must return 200")
                .isEqualTo(HttpStatus.OK);

        Map<String, Object> harvestBody = harvestResp.getBody();
        assertThat(harvestBody)
                .as("harvest response must contain 'cellarItem'")
                .containsKey("cellarItem");

        // Cellar must have one more item
        List<Map<String, Object>> after = getCellar(token, cid);
        assertThat(after)
                .as("cellar must contain one more item after harvest")
                .hasSizeGreaterThan(sizeBefore);
    }

    /**
     * harvest_twiceSameYear_rejected_400
     *
     * After a successful harvest, a second harvest in the same world-year
     * must be rejected with 400.
     *
     * WORLD-CLOCK-SPEC §4: "reject (400) if alreadyHarvestedThisYear".
     */
    @Test
    @DisplayName("harvest_twiceSameYear_rejected_400")
    @SuppressWarnings("unchecked")
    void harvest_twiceSameYear_rejected_400() {
        advanceToYearStart();

        String token  = registerAndGetToken(rest, base());
        Number charId = createCharacter(rest, base(), token, "Keti_" + System.nanoTime());
        long cid      = charId.longValue();

        ResponseEntity<Map> plant = plantVineyard(token, cid, "KAKHETI", "SAPERAVI");
        assertThat(plant.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        long vid = ((Number) plant.getBody().get("id")).longValue();

        // Advance deep into the season so the vine is ripe
        advanceClock(270);

        // First harvest — must succeed
        ResponseEntity<Map> first = harvest(token, vid, cid);
        assertThat(first.getStatusCode())
                .as("first harvest must succeed (200)")
                .isEqualTo(HttpStatus.OK);

        // Second harvest in the same world-year — must be rejected
        ResponseEntity<Map> second = harvest(token, vid, cid);
        assertThat(second.getStatusCode())
                .as("second harvest in the same year must be rejected with 400")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    /**
     * harvest_whenUnripe_rejected_400
     *
     * A fresh vineyard immediately after planting (year start, day 0) is in
     * DORMANCY — not ripe. Harvest must be rejected with 400.
     *
     * WORLD-CLOCK-SPEC §4: "reject (400) if ... not yet ripe".
     */
    @Test
    @DisplayName("harvest_whenUnripe_rejected_400")
    @SuppressWarnings("unchecked")
    void harvest_whenUnripe_rejected_400() {
        advanceToYearStart();

        String token  = registerAndGetToken(rest, base());
        Number charId = createCharacter(rest, base(), token, "Ana_" + System.nanoTime());
        long cid      = charId.longValue();

        ResponseEntity<Map> plant = plantVineyard(token, cid, "KAKHETI", "SAPERAVI");
        assertThat(plant.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        long vid = ((Number) plant.getBody().get("id")).longValue();

        // At day 0 of the year the vine is in DORMANCY — definitely not ripe
        ResponseEntity<Map> harvestResp = harvest(token, vid, cid);
        assertThat(harvestResp.getStatusCode())
                .as("harvest of unripe (dormant) vineyard must return 400")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    /**
     * ownership_otherAccount_cannotViewOrHarvest
     *
     * An authenticated account that does not own a vineyard must receive
     * 403 or 404 when attempting to view (GET /api/vineyards/detail/{id})
     * or harvest (POST /api/vineyards/{id}/harvest) it.
     *
     * WORLD-CLOCK-SPEC §4: "verify character ownership".
     */
    @Test
    @DisplayName("ownership_otherAccount_cannotViewOrHarvest")
    @SuppressWarnings("unchecked")
    void ownership_otherAccount_cannotViewOrHarvest() {
        // Owner account
        String ownerToken  = registerAndGetToken(rest, base());
        Number ownerCharId = createCharacter(rest, base(), ownerToken, "Owner_" + System.nanoTime());
        long ownCid        = ownerCharId.longValue();

        // Plant a vineyard for the owner
        ResponseEntity<Map> plant = plantVineyard(ownerToken, ownCid, "KAKHETI", "SAPERAVI");
        assertThat(plant.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        long vid = ((Number) plant.getBody().get("id")).longValue();

        // Attacker account
        String attackerToken  = registerAndGetToken(rest, base());
        Number attackerCharId = createCharacter(rest, base(), attackerToken, "Attacker_" + System.nanoTime());
        long atkCid           = attackerCharId.longValue();

        // Attacker tries to view the owner's vineyard
        ResponseEntity<Map> viewResp = getVineyardDetail(attackerToken, vid);
        assertThat(viewResp.getStatusCode())
                .as("attacker must receive 403 or 404 when viewing another account's vineyard")
                .isIn(HttpStatus.FORBIDDEN, HttpStatus.NOT_FOUND);

        // Attacker tries to harvest the owner's vineyard using their own characterId
        ResponseEntity<Map> harvestResp = harvest(attackerToken, vid, atkCid);
        assertThat(harvestResp.getStatusCode())
                .as("attacker must receive 403 or 404 when harvesting another account's vineyard")
                .isIn(HttpStatus.FORBIDDEN, HttpStatus.NOT_FOUND);
    }

    /**
     * replayDeterminism_sameDay_sameView
     *
     * GET /api/vineyards/detail/{vineyardId} called twice at the same world-clock
     * day must return identical brix and quality fields.
     *
     * WORLD-CLOCK-SPEC §0 ("replay don't persist") + §7 ("replay determinism").
     */
    @Test
    @DisplayName("replayDeterminism_sameDay_sameView")
    @SuppressWarnings("unchecked")
    void replayDeterminism_sameDay_sameView() {
        advanceToYearStart();

        String token  = registerAndGetToken(rest, base());
        Number charId = createCharacter(rest, base(), token, "Elene_" + System.nanoTime());
        long cid      = charId.longValue();

        ResponseEntity<Map> plant = plantVineyard(token, cid, "KAKHETI", "SAPERAVI");
        assertThat(plant.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        long vid = ((Number) plant.getBody().get("id")).longValue();

        // Advance to a day in the growing season where brix is meaningful
        advanceClock(200);

        // Call detail twice — no clock advance between calls
        ResponseEntity<Map> resp1 = getVineyardDetail(token, vid);
        ResponseEntity<Map> resp2 = getVineyardDetail(token, vid);

        assertThat(resp1.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp2.getStatusCode()).isEqualTo(HttpStatus.OK);

        Map<String, Object> view1 = resp1.getBody();
        Map<String, Object> view2 = resp2.getBody();
        assertThat(view1).isNotNull();
        assertThat(view2).isNotNull();

        // brix must be identical (replay is deterministic)
        if (view1.containsKey("brix") && view2.containsKey("brix")) {
            double brix1 = ((Number) view1.get("brix")).doubleValue();
            double brix2 = ((Number) view2.get("brix")).doubleValue();
            assertThat(brix1)
                    .as("brix must be identical on two calls at the same world day (replay determinism)")
                    .isEqualTo(brix2);
        }

        // stage must also match
        if (view1.containsKey("stage") && view2.containsKey("stage")) {
            assertThat(view1.get("stage"))
                    .as("stage must be identical on two calls at the same world day")
                    .isEqualTo(view2.get("stage"));
        }

        // year/dayOfYear must match
        if (view1.containsKey("year") && view2.containsKey("year")) {
            assertThat(view1.get("year"))
                    .as("year field must be identical on two calls at the same world day")
                    .isEqualTo(view2.get("year"));
        }
        if (view1.containsKey("dayOfYear") && view2.containsKey("dayOfYear")) {
            assertThat(view1.get("dayOfYear"))
                    .as("dayOfYear field must be identical on two calls at the same world day")
                    .isEqualTo(view2.get("dayOfYear"));
        }
    }

    // ── Utility ───────────────────────────────────────────────────────────────

    /**
     * Returns true if {@code laterStage} is the same or a later phenological stage
     * than {@code earlyStage} in the Kakheti vine cycle.
     *
     * Order: DORMANCY → BUD_SWELL → BUDBREAK → SHOOT_GROWTH → FLOWERING →
     *        FRUIT_SET → BERRY_DEVELOPMENT → VERAISON → RIPENING → HARVESTED → LEAF_FALL
     */
    private boolean stageIsLaterOrEqual(String early, String later) {
        List<String> order = List.of(
                "DORMANCY", "BUD_SWELL", "BUDBREAK", "SHOOT_GROWTH", "FLOWERING",
                "FRUIT_SET", "BERRY_DEVELOPMENT", "VERAISON", "RIPENING",
                "HARVESTED", "LEAF_FALL");
        int ei = order.indexOf(early);
        int li = order.indexOf(later);
        if (ei == -1 || li == -1) return true; // unknown stage — skip
        return li >= ei;
    }
}
