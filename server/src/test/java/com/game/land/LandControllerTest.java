package com.game.land;

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
 * Integration tests for the LAND lane — POST /api/land/parcels,
 * GET /api/land/{characterId}, GET /api/land/parcel/{parcelId},
 * and POST /api/land/parcels/{parcelId}/attach-vineyard.
 *
 * <p>All tests use the "test" profile (H2 in-memory, frozen world clock).
 *
 * <p>Test coverage:
 * <ol>
 *   <li>buy_deductsWallet_createsParcel — wallet is reduced by price;
 *       parcel coordinates are inside Georgia's bounding box; parcel is
 *       near the requested region's centre.</li>
 *   <li>buy_insufficientFunds_returns400 — buying when the wallet balance is too
 *       low returns 400 (INSUFFICIENT_FUNDS).</li>
 *   <li>list_returnsOwnedParcels — GET /api/land/{characterId} returns the
 *       purchased parcel for the owner.</li>
 *   <li>list_otherAccount_returns404 — another account's token cannot list
 *       parcels for a character it does not own.</li>
 *   <li>detail_ownerCanAccess — GET /api/land/parcel/{parcelId} succeeds for
 *       the owner.</li>
 *   <li>detail_otherAccount_returns404 — another account's token cannot access
 *       a parcel it does not own.</li>
 *   <li>buy_twoParcels_differentCoords — two parcels in the same region land
 *       at different coordinates (jitter works).</li>
 *   <li>coordinates_insideGeorgia_allRegions — buying one parcel in each of the
 *       seven Georgian regions always yields coordinates inside the bounding box
 *       (lat 41.0–43.6, lon 40.0–46.8).</li>
 * </ol>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class LandControllerTest {

    /** Georgia's bounding box (WGS84). */
    private static final double LAT_MIN = 41.0;
    private static final double LAT_MAX = 43.6;
    private static final double LON_MIN = 40.0;
    private static final double LON_MAX = 46.8;

    /** Per-region centre coordinates from WorldCatalog (for the "near centre" check). */
    private static final double KAKHETI_LAT = 41.92;
    private static final double KAKHETI_LON = 45.47;
    /** Tolerance: must be within MAX_JITTER + epsilon of the centre. */
    private static final double NEAR_DELTA  = 0.10; // slightly > MAX_JITTER=0.08

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    private String base() {
        return "http://localhost:" + port;
    }

    // ── HTTP helpers ──────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private ResponseEntity<Map> buyParcel(String token, long characterId,
                                          String region, String name, double sizeHa) {
        Map<String, Object> body = Map.of(
                "characterId", characterId,
                "region", region,
                "name", name,
                "sizeHectares", sizeHa);
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.setBearerAuth(token);
        return rest.postForEntity(
                base() + "/api/land/parcels",
                new HttpEntity<>(body, h),
                Map.class);
    }

    @SuppressWarnings("unchecked")
    private ResponseEntity<List> listParcels(String token, long characterId) {
        return rest.exchange(
                base() + "/api/land/" + characterId,
                HttpMethod.GET,
                getWithToken(token),
                List.class);
    }

    @SuppressWarnings("unchecked")
    private ResponseEntity<Map> getParcel(String token, long parcelId) {
        return rest.exchange(
                base() + "/api/land/parcel/" + parcelId,
                HttpMethod.GET,
                getWithToken(token),
                Map.class);
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    /**
     * buy_deductsWallet_createsParcel
     *
     * Buying 1 ha at 200 GEL/ha on an account starting with 100 GEL...wait,
     * characters start with 100 GEL.  We need a larger wallet.  Use 0.1 ha
     * (= 20 GEL cost) so the default 100 GEL wallet is sufficient.
     *
     * POST /api/land/parcels must:
     *   - return 201
     *   - return a parcel with latitude inside [41.0, 43.6] and longitude inside [40.0, 46.8]
     *   - return a parcel whose lat/lon are within ~0.10° of KAKHETI centre
     *   - return newWalletGel = 100.0 - 20.0 = 80.0
     */
    @Test
    @DisplayName("buy_deductsWallet_createsParcel")
    @SuppressWarnings("unchecked")
    void buy_deductsWallet_createsParcel() {
        String token  = registerAndGetToken(rest, base());
        Number charId = createCharacter(rest, base(), token, "Luka_" + System.nanoTime());
        long cid      = charId.longValue();

        // 0.1 ha costs 20 GEL; well within the 100 GEL starting wallet
        ResponseEntity<Map> resp = buyParcel(token, cid, "KAKHETI", "Iveria Estate", 0.1);

        assertThat(resp.getStatusCode())
                .as("POST /api/land/parcels must return 201")
                .isEqualTo(HttpStatus.CREATED);

        Map<String, Object> body = resp.getBody();
        assertThat(body).as("response body must not be null").isNotNull();
        assertThat(body).as("response must contain 'parcel'").containsKey("parcel");
        assertThat(body).as("response must contain 'newWalletGel'").containsKey("newWalletGel");

        // Wallet check
        double newWallet = ((Number) body.get("newWalletGel")).doubleValue();
        assertThat(newWallet)
                .as("wallet must be 80.0 after spending 20 GEL (0.1 ha × 200 GEL/ha)")
                .isEqualTo(80.0);

        // Parcel coordinate checks
        Map<String, Object> parcel = (Map<String, Object>) body.get("parcel");
        assertThat(parcel).as("parcel must not be null").isNotNull();
        assertThat(parcel).as("parcel must have latitude").containsKey("latitude");
        assertThat(parcel).as("parcel must have longitude").containsKey("longitude");
        assertThat(parcel).as("parcel must have region").containsKey("region");

        double lat = ((Number) parcel.get("latitude")).doubleValue();
        double lon = ((Number) parcel.get("longitude")).doubleValue();

        assertThat(lat).as("latitude must be inside Georgia's bounding box")
                .isBetween(LAT_MIN, LAT_MAX);
        assertThat(lon).as("longitude must be inside Georgia's bounding box")
                .isBetween(LON_MIN, LON_MAX);

        // Must be near Kakheti centre (within NEAR_DELTA degrees)
        assertThat(Math.abs(lat - KAKHETI_LAT))
                .as("latitude must be within " + NEAR_DELTA + "° of Kakheti centre")
                .isLessThanOrEqualTo(NEAR_DELTA);
        assertThat(Math.abs(lon - KAKHETI_LON))
                .as("longitude must be within " + NEAR_DELTA + "° of Kakheti centre")
                .isLessThanOrEqualTo(NEAR_DELTA);

        // Region stored correctly
        assertThat(parcel.get("region"))
                .as("parcel region must be KAKHETI")
                .isEqualTo("KAKHETI");
    }

    /**
     * buy_insufficientFunds_returns400
     *
     * Buying 1 ha (= 200 GEL) on an account with only 100 GEL must fail with 400.
     */
    @Test
    @DisplayName("buy_insufficientFunds_returns400")
    @SuppressWarnings("unchecked")
    void buy_insufficientFunds_returns400() {
        String token  = registerAndGetToken(rest, base());
        Number charId = createCharacter(rest, base(), token, "Mariam_" + System.nanoTime());
        long cid      = charId.longValue();

        // 1 ha costs 200 GEL, wallet is 100 GEL — should fail
        ResponseEntity<Map> resp = buyParcel(token, cid, "KAKHETI", "Too Expensive", 1.0);

        assertThat(resp.getStatusCode())
                .as("buying land with insufficient funds must return 400 or 402")
                .isIn(HttpStatus.BAD_REQUEST, HttpStatus.PAYMENT_REQUIRED);

        Map<String, Object> body = resp.getBody();
        assertThat(body).as("error body must not be null").isNotNull();
        // Standard error envelope must have an 'error' key
        assertThat(body).as("error response must have 'error' key").containsKey("error");
    }

    /**
     * list_returnsOwnedParcels
     *
     * GET /api/land/{characterId} must return at least the parcel just bought.
     */
    @Test
    @DisplayName("list_returnsOwnedParcels")
    @SuppressWarnings("unchecked")
    void list_returnsOwnedParcels() {
        String token  = registerAndGetToken(rest, base());
        Number charId = createCharacter(rest, base(), token, "Nino_" + System.nanoTime());
        long cid      = charId.longValue();

        ResponseEntity<Map> buy = buyParcel(token, cid, "IMERETI", "Kutaisi Slopes", 0.1);
        assertThat(buy.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        Map<String, Object> buyBody = buy.getBody();
        Map<String, Object> parcel  = (Map<String, Object>) buyBody.get("parcel");
        long parcelId = ((Number) parcel.get("id")).longValue();

        ResponseEntity<List> listResp = listParcels(token, cid);
        assertThat(listResp.getStatusCode())
                .as("GET /api/land/{characterId} must return 200")
                .isEqualTo(HttpStatus.OK);

        List<?> parcels = listResp.getBody();
        assertThat(parcels)
                .as("list must contain at least the newly bought parcel")
                .isNotNull()
                .isNotEmpty();

        boolean found = parcels.stream()
                .map(p -> (Map<?, ?>) p)
                .anyMatch(p -> p.containsKey("id")
                        && ((Number) p.get("id")).longValue() == parcelId);

        assertThat(found)
                .as("parcel id=" + parcelId + " must appear in the character's parcel list")
                .isTrue();
    }

    /**
     * list_otherAccount_returns404
     *
     * An account that does not own the character cannot list its parcels.
     */
    @Test
    @DisplayName("list_otherAccount_returns404")
    @SuppressWarnings("unchecked")
    void list_otherAccount_returns404() {
        // Owner sets up a parcel
        String ownerToken  = registerAndGetToken(rest, base());
        Number ownerCharId = createCharacter(rest, base(), ownerToken, "Owner_" + System.nanoTime());
        long ownCid        = ownerCharId.longValue();
        buyParcel(ownerToken, ownCid, "KARTLI", "Owner's Land", 0.1);

        // Attacker tries to list parcels for owner's character.
        // Use String.class: a 404 returns the error envelope (a JSON object), which
        // can't be deserialized into List — we only care about the status here.
        String attackerToken = registerAndGetToken(rest, base());
        ResponseEntity<String> listResp = rest.exchange(
                base() + "/api/land/" + ownCid,
                HttpMethod.GET,
                getWithToken(attackerToken),
                String.class);

        assertThat(listResp.getStatusCode())
                .as("attacker must receive 404 when listing another account's character parcels")
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    /**
     * detail_ownerCanAccess
     *
     * GET /api/land/parcel/{parcelId} returns 200 for the owner.
     */
    @Test
    @DisplayName("detail_ownerCanAccess")
    @SuppressWarnings("unchecked")
    void detail_ownerCanAccess() {
        String token  = registerAndGetToken(rest, base());
        Number charId = createCharacter(rest, base(), token, "David_" + System.nanoTime());
        long cid      = charId.longValue();

        ResponseEntity<Map> buy = buyParcel(token, cid, "SAMEGRELO", "Zugdidi Fields", 0.1);
        assertThat(buy.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        Map<String, Object> parcel  = (Map<String, Object>) buy.getBody().get("parcel");
        long parcelId = ((Number) parcel.get("id")).longValue();

        ResponseEntity<Map> detail = getParcel(token, parcelId);
        assertThat(detail.getStatusCode())
                .as("owner must receive 200 on GET /api/land/parcel/{parcelId}")
                .isEqualTo(HttpStatus.OK);

        Map<String, Object> detailBody = detail.getBody();
        assertThat(detailBody).as("detail body must not be null").isNotNull();
        assertThat(((Number) detailBody.get("id")).longValue())
                .as("returned parcel id must match")
                .isEqualTo(parcelId);
    }

    /**
     * detail_otherAccount_returns404
     *
     * Another account's token must receive 404 on the detail endpoint.
     */
    @Test
    @DisplayName("detail_otherAccount_returns404")
    @SuppressWarnings("unchecked")
    void detail_otherAccount_returns404() {
        String ownerToken  = registerAndGetToken(rest, base());
        Number ownerCharId = createCharacter(rest, base(), ownerToken, "OwnerD_" + System.nanoTime());
        long ownCid        = ownerCharId.longValue();

        ResponseEntity<Map> buy = buyParcel(ownerToken, ownCid, "MESKHETI", "Akhaltsikhe Plot", 0.1);
        assertThat(buy.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<String, Object> parcel = (Map<String, Object>) buy.getBody().get("parcel");
        long parcelId = ((Number) parcel.get("id")).longValue();

        String attackerToken = registerAndGetToken(rest, base());
        ResponseEntity<Map> detail = getParcel(attackerToken, parcelId);

        assertThat(detail.getStatusCode())
                .as("attacker must receive 404 when accessing another account's parcel detail")
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    /**
     * buy_twoParcels_differentCoords
     *
     * Buying two parcels in the same region must yield different coordinates
     * (jitter works; parcels don't all stack).
     */
    @Test
    @DisplayName("buy_twoParcels_differentCoords")
    @SuppressWarnings("unchecked")
    void buy_twoParcels_differentCoords() throws InterruptedException {
        String token  = registerAndGetToken(rest, base());
        Number charId = createCharacter(rest, base(), token, "Tamar_" + System.nanoTime());
        long cid      = charId.longValue();

        ResponseEntity<Map> buy1 = buyParcel(token, cid, "RACHA_LECHKHUMI", "Parcel A", 0.05);
        assertThat(buy1.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Small sleep to ensure the epoch-millis seed differs
        Thread.sleep(2);

        ResponseEntity<Map> buy2 = buyParcel(token, cid, "RACHA_LECHKHUMI", "Parcel B", 0.05);
        assertThat(buy2.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        Map<String, Object> p1 = (Map<String, Object>) buy1.getBody().get("parcel");
        Map<String, Object> p2 = (Map<String, Object>) buy2.getBody().get("parcel");

        double lat1 = ((Number) p1.get("latitude")).doubleValue();
        double lon1 = ((Number) p1.get("longitude")).doubleValue();
        double lat2 = ((Number) p2.get("latitude")).doubleValue();
        double lon2 = ((Number) p2.get("longitude")).doubleValue();

        assertThat(lat1 == lat2 && lon1 == lon2)
                .as("two parcels bought at different times must not share the exact same coordinates")
                .isFalse();
    }

    /**
     * coordinates_insideGeorgia_allRegions
     *
     * Buying one parcel in each of the 7 Georgian regions must always produce
     * coordinates inside Georgia's bounding box.
     */
    @Test
    @DisplayName("coordinates_insideGeorgia_allRegions")
    @SuppressWarnings("unchecked")
    void coordinates_insideGeorgia_allRegions() {
        String token  = registerAndGetToken(rest, base());
        Number charId = createCharacter(rest, base(), token, "Ana_" + System.nanoTime());
        long cid      = charId.longValue();

        String[] regions = {
            "KAKHETI", "KARTLI", "IMERETI",
            "RACHA_LECHKHUMI", "SAMEGRELO", "GURIA_ADJARA", "MESKHETI"
        };

        for (String region : regions) {
            ResponseEntity<Map> resp = buyParcel(token, cid, region, region + "_plot", 0.01);
            assertThat(resp.getStatusCode())
                    .as("buying parcel in " + region + " must return 201")
                    .isEqualTo(HttpStatus.CREATED);

            Map<String, Object> parcel = (Map<String, Object>) resp.getBody().get("parcel");
            double lat = ((Number) parcel.get("latitude")).doubleValue();
            double lon = ((Number) parcel.get("longitude")).doubleValue();

            assertThat(lat)
                    .as(region + " parcel latitude must be inside Georgia (41.0–43.6)")
                    .isBetween(LAT_MIN, LAT_MAX);
            assertThat(lon)
                    .as(region + " parcel longitude must be inside Georgia (40.0–46.8)")
                    .isBetween(LON_MIN, LON_MAX);
        }
    }
}
