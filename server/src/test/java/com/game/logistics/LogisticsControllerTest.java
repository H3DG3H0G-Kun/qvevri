package com.game.logistics;

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
import java.util.UUID;

import static com.game.account.AccountTestHelper.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for LANE LOGISTICS (/api/logistics/**).
 *
 * <p>Scenarios:
 * <ol>
 *   <li>Ship GOODS → inventory debited + arriveDay &gt; departDay.</li>
 *   <li>GET /{characterId} → lists the shipment.</li>
 *   <li>Collect before arrival → 400 NOT_ARRIVED.</li>
 *   <li>Advance clock past arriveDay → collect grants goods to recipient.</li>
 *   <li>Collect twice → 400.</li>
 *   <li>travelDays(Kakheti→GURIA_ADJARA) &gt; travelDays(Kakheti→KARTLI) — farther region is strictly more days.</li>
 *   <li>Unknown toRegion → 400.</li>
 *   <li>Ship more than owned → 400.</li>
 * </ol>
 *
 * <p>Clock is frozen in test profile (world.real-seconds-per-sim-day=86400000);
 * time is advanced via POST /api/world/advance.
 *
 * <p>Goods are seeded via POST /api/shop/buy (pruning_shears, basePrice 45 GEL).
 * Characters start with 100 GEL, so buying 1 unit (45 GEL) leaves 55 GEL.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class LogisticsControllerTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    private String base() {
        return "http://localhost:" + port;
    }

    private static String uniqueName() {
        return "lg_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    // ── 1. Ship GOODS debits inventory + sets arriveDay > departDay ───────────

    @Test
    @DisplayName("ship_goodsStack_debitsInventory_arriveDayAfterDepartDay")
    @SuppressWarnings("unchecked")
    void ship_goodsStack_debitsInventory_arriveDayAfterDepartDay() {
        String token  = registerAndGetToken(rest, base());
        Number charId = createCharacter(rest, base(), token, uniqueName());
        long cid = charId.longValue();

        // Seed 2 pruning_shears (45 GEL each = 90 total; wallet 100 → 10)
        buyGoods(token, cid, "pruning_shears", 2.0);

        int invBefore = countInventoryUnits(token, cid, "pruning_shears");
        assertThat(invBefore).as("must own 2 before shipping").isEqualTo(2);

        int dayBefore = currentAbsoluteDay();

        // Ship 1 unit from KAKHETI to KARTLI
        Map<String, Object> body = Map.of(
                "characterId", cid,
                "kind",        "GOODS",
                "refId",       "pruning_shears",
                "quantity",    1.0,
                "fromRegion",  "KAKHETI",
                "toRegion",    "KARTLI");

        ResponseEntity<Map> resp = rest.postForEntity(
                base() + "/api/logistics/ship",
                withToken(body, token),
                Map.class);

        assertThat(resp.getStatusCode())
                .as("POST /api/logistics/ship must return 200")
                .isEqualTo(HttpStatus.OK);

        Map<?, ?> shipment = resp.getBody();
        assertThat(shipment).isNotNull();
        assertThat(shipment.get("shipStatus")).isEqualTo("IN_TRANSIT");
        assertThat(shipment.get("kind")).isEqualTo("GOODS");
        assertThat(shipment.get("refId")).isEqualTo("pruning_shears");

        long departDay = ((Number) shipment.get("departDay")).longValue();
        long arriveDay = ((Number) shipment.get("arriveDay")).longValue();

        assertThat(departDay)
                .as("departDay must equal the current sim day")
                .isEqualTo(dayBefore);
        assertThat(arriveDay)
                .as("arriveDay must be strictly greater than departDay (travel takes time)")
                .isGreaterThan(departDay);

        // Inventory must be debited by 1
        int invAfter = countInventoryUnits(token, cid, "pruning_shears");
        assertThat(invAfter)
                .as("inventory must decrease by 1 after shipping")
                .isEqualTo(invBefore - 1);
    }

    // ── 2. GET /{characterId} lists the shipment ──────────────────────────────

    @Test
    @DisplayName("listShipments_containsCreatedShipment")
    @SuppressWarnings("unchecked")
    void listShipments_containsCreatedShipment() {
        String token  = registerAndGetToken(rest, base());
        Number charId = createCharacter(rest, base(), token, uniqueName());
        long cid = charId.longValue();

        buyGoods(token, cid, "pruning_shears", 1.0);

        Map<String, Object> shipBody = Map.of(
                "characterId", cid,
                "kind",        "GOODS",
                "refId",       "pruning_shears",
                "quantity",    1.0,
                "fromRegion",  "KAKHETI",
                "toRegion",    "IMERETI");

        ResponseEntity<Map> shipResp = rest.postForEntity(
                base() + "/api/logistics/ship",
                withToken(shipBody, token),
                Map.class);
        assertThat(shipResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Number shipmentId = (Number) shipResp.getBody().get("id");

        // List shipments
        ResponseEntity<List> listResp = rest.exchange(
                base() + "/api/logistics/" + cid,
                HttpMethod.GET,
                getWithToken(token),
                List.class);

        assertThat(listResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<?> shipments = listResp.getBody();
        assertThat(shipments).isNotNull().isNotEmpty();

        boolean found = shipments.stream()
                .anyMatch(s -> shipmentId.longValue() ==
                        ((Number) ((Map<?, ?>) s).get("id")).longValue());
        assertThat(found)
                .as("The created shipment must appear in GET /api/logistics/{characterId}")
                .isTrue();
    }

    // ── 3. Collect before arrival → 400 NOT_ARRIVED ───────────────────────────

    @Test
    @DisplayName("collect_beforeArrival_400NotArrived")
    @SuppressWarnings("unchecked")
    void collect_beforeArrival_400NotArrived() {
        String token  = registerAndGetToken(rest, base());
        Number charId = createCharacter(rest, base(), token, uniqueName());
        long cid = charId.longValue();

        buyGoods(token, cid, "pruning_shears", 1.0);

        // Ship to a distant region (GURIA_ADJARA is far from KAKHETI — many travel days)
        Map<String, Object> shipBody = Map.of(
                "characterId", cid,
                "kind",        "GOODS",
                "refId",       "pruning_shears",
                "quantity",    1.0,
                "fromRegion",  "KAKHETI",
                "toRegion",    "GURIA_ADJARA");

        ResponseEntity<Map> shipResp = rest.postForEntity(
                base() + "/api/logistics/ship",
                withToken(shipBody, token),
                Map.class);
        assertThat(shipResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Number shipmentId = (Number) shipResp.getBody().get("id");

        // Try to collect immediately (no clock advance) → must be 400
        Map<String, Object> collectBody = Map.of(
                "characterId", cid,
                "shipmentId",  shipmentId.longValue());

        ResponseEntity<Map> collectResp = rest.postForEntity(
                base() + "/api/logistics/collect",
                withToken(collectBody, token),
                Map.class);

        assertThat(collectResp.getStatusCode())
                .as("Collecting before arrival must return 400")
                .isEqualTo(HttpStatus.BAD_REQUEST);

        // Verify the error code is NOT_ARRIVED
        Map<?, ?> errEnvelope = collectResp.getBody();
        assertThat(errEnvelope).isNotNull();
        // Error envelope: { "error": { "code": "...", "message": "..." } }
        Object errorObj = errEnvelope.get("error");
        assertThat(errorObj).as("response must contain 'error' field").isNotNull();
        Map<?, ?> errorMap = (Map<?, ?>) errorObj;
        assertThat(errorMap.get("code"))
                .as("error code must be NOT_ARRIVED")
                .isEqualTo("NOT_ARRIVED");
    }

    // ── 4. Advance clock + collect → transfers goods to recipient ─────────────

    @Test
    @DisplayName("collect_afterClockAdvance_grantsGoodsToRecipient")
    @SuppressWarnings("unchecked")
    void collect_afterClockAdvance_grantsGoodsToRecipient() {
        // Sender account
        String senderToken  = registerAndGetToken(rest, base());
        Number senderCharId = createCharacter(rest, base(), senderToken, uniqueName());
        long scid = senderCharId.longValue();

        // Recipient account
        String recipientToken  = registerAndGetToken(rest, base());
        Number recipientCharId = createCharacter(rest, base(), recipientToken, uniqueName());
        long rcid = recipientCharId.longValue();

        // Seed goods for sender
        buyGoods(senderToken, scid, "pruning_shears", 1.0);

        // Ship from KAKHETI to KARTLI with explicit recipient
        Map<String, Object> shipBody = Map.of(
                "characterId",          scid,
                "kind",                 "GOODS",
                "refId",                "pruning_shears",
                "quantity",             1.0,
                "fromRegion",           "KAKHETI",
                "toRegion",             "KARTLI",
                "recipientCharacterId", rcid);

        ResponseEntity<Map> shipResp = rest.postForEntity(
                base() + "/api/logistics/ship",
                withToken(shipBody, senderToken),
                Map.class);
        assertThat(shipResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        Map<?, ?> shipment = shipResp.getBody();
        assertThat(shipment).isNotNull();
        Number shipmentId = (Number) shipment.get("id");
        long arriveDay    = ((Number) shipment.get("arriveDay")).longValue();
        long departDay    = ((Number) shipment.get("departDay")).longValue();

        // Advance clock to at least arriveDay
        int currentDay = currentAbsoluteDay();
        int daysToAdvance = (int) (arriveDay - currentDay) + 1;
        advanceClock(daysToAdvance);

        // Recipient's inventory before collect
        int recipientInvBefore = countInventoryUnits(recipientToken, rcid, "pruning_shears");

        // Sender collects (owner)
        Map<String, Object> collectBody = Map.of(
                "characterId", scid,
                "shipmentId",  shipmentId.longValue());

        ResponseEntity<Map> collectResp = rest.postForEntity(
                base() + "/api/logistics/collect",
                withToken(collectBody, senderToken),
                Map.class);

        assertThat(collectResp.getStatusCode())
                .as("collect after arrival must return 200")
                .isEqualTo(HttpStatus.OK);

        Map<?, ?> collected = collectResp.getBody();
        assertThat(collected).isNotNull();
        assertThat(collected.get("shipStatus"))
                .as("status must be COLLECTED")
                .isEqualTo("COLLECTED");

        // Recipient now owns the goods
        int recipientInvAfter = countInventoryUnits(recipientToken, rcid, "pruning_shears");
        assertThat(recipientInvAfter)
                .as("recipient must receive 1 pruning_shears after collect")
                .isEqualTo(recipientInvBefore + 1);
    }

    // ── 5. Collect twice → 400 ────────────────────────────────────────────────

    @Test
    @DisplayName("collect_twice_400")
    @SuppressWarnings("unchecked")
    void collect_twice_400() {
        String token  = registerAndGetToken(rest, base());
        Number charId = createCharacter(rest, base(), token, uniqueName());
        long cid = charId.longValue();

        buyGoods(token, cid, "pruning_shears", 1.0);

        // Ship to KARTLI (short distance)
        Map<String, Object> shipBody = Map.of(
                "characterId", cid,
                "kind",        "GOODS",
                "refId",       "pruning_shears",
                "quantity",    1.0,
                "fromRegion",  "KAKHETI",
                "toRegion",    "KARTLI");

        ResponseEntity<Map> shipResp = rest.postForEntity(
                base() + "/api/logistics/ship",
                withToken(shipBody, token),
                Map.class);
        assertThat(shipResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> shipment = shipResp.getBody();
        Number shipmentId = (Number) shipment.get("id");
        long arriveDay    = ((Number) shipment.get("arriveDay")).longValue();

        // Advance to after arriveDay
        int currentDay    = currentAbsoluteDay();
        advanceClock((int) (arriveDay - currentDay) + 1);

        // First collect must succeed
        Map<String, Object> collectBody = Map.of(
                "characterId", cid,
                "shipmentId",  shipmentId.longValue());

        ResponseEntity<Map> first = rest.postForEntity(
                base() + "/api/logistics/collect",
                withToken(collectBody, token),
                Map.class);
        assertThat(first.getStatusCode())
                .as("first collect must return 200")
                .isEqualTo(HttpStatus.OK);

        // Second collect must return 400
        ResponseEntity<Map> second = rest.postForEntity(
                base() + "/api/logistics/collect",
                withToken(collectBody, token),
                Map.class);
        assertThat(second.getStatusCode())
                .as("second collect on same shipment must return 400")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ── 6. travelDays: Kakheti→GURIA_ADJARA > Kakheti→KARTLI ────────────────

    @Test
    @DisplayName("travelDays_furtherRegion_isStrictlyMore")
    @SuppressWarnings("unchecked")
    void travelDays_furtherRegion_isStrictlyMore() {
        com.game.world.Region kakheti     = com.game.world.Region.KAKHETI;
        com.game.world.Region kartli      = com.game.world.Region.KARTLI;
        com.game.world.Region guriaAdjara = com.game.world.Region.GURIA_ADJARA;

        int kakhetiToKartli      = GeoUtil.travelDays(kakheti, kartli);
        int kakhetiToGuriaAdjara = GeoUtil.travelDays(kakheti, guriaAdjara);

        assertThat(kakhetiToGuriaAdjara)
                .as("Travel from Kakheti to Guria/Adjara must take strictly more days "
                        + "than Kakheti to Kartli (Guria/Adjara is further)")
                .isGreaterThan(kakhetiToKartli);

        // Both must be positive
        assertThat(kakhetiToKartli).isGreaterThanOrEqualTo(1);
        assertThat(kakhetiToGuriaAdjara).isGreaterThanOrEqualTo(1);
    }

    // ── 7. Unknown toRegion → 400 ─────────────────────────────────────────────

    @Test
    @DisplayName("ship_unknownToRegion_400")
    @SuppressWarnings("unchecked")
    void ship_unknownToRegion_400() {
        String token  = registerAndGetToken(rest, base());
        Number charId = createCharacter(rest, base(), token, uniqueName());
        long cid = charId.longValue();

        buyGoods(token, cid, "pruning_shears", 1.0);

        Map<String, Object> body = Map.of(
                "characterId", cid,
                "kind",        "GOODS",
                "refId",       "pruning_shears",
                "quantity",    1.0,
                "fromRegion",  "KAKHETI",
                "toRegion",    "ATLANTIS");

        ResponseEntity<Map> resp = rest.postForEntity(
                base() + "/api/logistics/ship",
                withToken(body, token),
                Map.class);

        assertThat(resp.getStatusCode())
                .as("Unknown toRegion must return 400")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ── 8. Ship more than owned → 400 ─────────────────────────────────────────

    @Test
    @DisplayName("ship_moreThanOwned_400")
    @SuppressWarnings("unchecked")
    void ship_moreThanOwned_400() {
        String token  = registerAndGetToken(rest, base());
        Number charId = createCharacter(rest, base(), token, uniqueName());
        long cid = charId.longValue();

        buyGoods(token, cid, "pruning_shears", 1.0);

        // Try to ship 5 when only 1 is owned
        Map<String, Object> body = Map.of(
                "characterId", cid,
                "kind",        "GOODS",
                "refId",       "pruning_shears",
                "quantity",    5.0,
                "fromRegion",  "KAKHETI",
                "toRegion",    "KARTLI");

        ResponseEntity<Map> resp = rest.postForEntity(
                base() + "/api/logistics/ship",
                withToken(body, token),
                Map.class);

        assertThat(resp.getStatusCode())
                .as("Shipping more than owned must return 400")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /** Buy goods from the NPC shop to seed inventory. */
    @SuppressWarnings("unchecked")
    private void buyGoods(String token, long characterId, String goodTypeId, double qty) {
        Map<String, Object> body = Map.of(
                "characterId", characterId,
                "goodTypeId",  goodTypeId,
                "quantity",    qty);
        ResponseEntity<Map> resp = rest.postForEntity(
                base() + "/api/shop/buy",
                withToken(body, token),
                Map.class);
        assertThat(resp.getStatusCode())
                .as("Shop buy must succeed for seeding test data")
                .isEqualTo(HttpStatus.OK);
    }

    /** Count how many units of goodTypeId the character owns. */
    @SuppressWarnings("unchecked")
    private int countInventoryUnits(String token, long characterId, String goodTypeId) {
        ResponseEntity<List> resp = rest.exchange(
                base() + "/api/goods/" + characterId,
                HttpMethod.GET,
                getWithToken(token),
                List.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<?> inv = resp.getBody();
        if (inv == null || inv.isEmpty()) return 0;
        return inv.stream()
                .filter(o -> goodTypeId.equals(((Map<?, ?>) o).get("goodTypeId")))
                .mapToInt(o -> ((Number) ((Map<?, ?>) o).get("quantity")).intValue())
                .sum();
    }

    /** Read the current absolute sim day. */
    @SuppressWarnings("unchecked")
    private int currentAbsoluteDay() {
        ResponseEntity<Map> resp = rest.getForEntity(
                base() + "/api/world/clock", Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return ((Number) resp.getBody().get("absoluteDay")).intValue();
    }

    /** Advance the world clock by n sim days via POST /api/world/advance. */
    @SuppressWarnings("unchecked")
    private void advanceClock(int days) {
        if (days < 1) return;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> body = Map.of("days", days);
        ResponseEntity<Map> resp = rest.postForEntity(
                base() + "/api/world/advance",
                new HttpEntity<>(body, headers),
                Map.class);
        assertThat(resp.getStatusCode())
                .as("POST /api/world/advance must return 200")
                .isEqualTo(HttpStatus.OK);
    }
}
