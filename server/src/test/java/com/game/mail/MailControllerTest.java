package com.game.mail;

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
 * Integration tests for /api/mail/** (LANE MAIL).
 *
 * <p>Scenarios covered:
 * <ol>
 *   <li>Send with GEL attachment → sender wallet debited; recipient inbox shows the mail.</li>
 *   <li>GET /api/mail/{characterId} → recipient sees mail.</li>
 *   <li>POST /api/mail/{mailId}/read → isRead becomes true.</li>
 *   <li>POST /api/mail/{mailId}/claim → recipient wallet credited; double-claim → 400.</li>
 *   <li>Send with GOODS attachment (seeded via shop/buy) → claim grants goods to recipient.</li>
 *   <li>Ownership enforcement: non-recipient claim/read → 404 (not their mail).</li>
 *   <li>Delete mail with unclaimed attachment → 400; delete after claim → 200.</li>
 *   <li>No-attachment send → persists; claim on no-attachment mail → 400.</li>
 * </ol>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class MailControllerTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    private String base() {
        return "http://localhost:" + port;
    }

    private static String uniqueName() {
        return "ml_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 1. Send with GEL attachment debits sender wallet
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("sendGelMail_debitsSenderWallet")
    @SuppressWarnings("unchecked")
    void sendGelMail_debitsSenderWallet() {
        String senderToken = registerAndGetToken(rest, base());
        Number senderId = createCharacter(rest, base(), senderToken, uniqueName());
        long sid = senderId.longValue();

        String recipientToken = registerAndGetToken(rest, base());
        Number recipientId = createCharacter(rest, base(), recipientToken, uniqueName());
        long rid = recipientId.longValue();

        double walletBefore = getWallet(senderToken, sid);
        double gelAttach = 10.0;

        Map<String, Object> sendBody = Map.of(
                "senderCharacterId",    sid,
                "recipientCharacterId", rid,
                "subject",              "Hello",
                "body",                 "Take this GEL",
                "attachKind",           "GEL",
                "attachAmount",         gelAttach);

        ResponseEntity<Map> resp = rest.postForEntity(
                base() + "/api/mail/send",
                withToken(sendBody, senderToken),
                Map.class);
        assertThat(resp.getStatusCode())
                .as("POST /api/mail/send must return 200")
                .isEqualTo(HttpStatus.OK);

        Map<String, Object> mail = resp.getBody();
        assertThat(mail).isNotNull();
        assertThat(mail).containsKey("id");
        assertThat(mail.get("attachKind")).isEqualTo("GEL");
        assertThat(((Number) mail.get("attachAmount")).doubleValue()).isEqualTo(gelAttach);
        assertThat(mail.get("claimed")).isEqualTo(false);

        double walletAfter = getWallet(senderToken, sid);
        assertThat(walletAfter)
                .as("Sender wallet must decrease by GEL attachment amount")
                .isEqualTo(walletBefore - gelAttach);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. GET /api/mail/{characterId} — recipient sees the mail
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("inbox_recipientSeesMailAfterSend")
    @SuppressWarnings("unchecked")
    void inbox_recipientSeesMailAfterSend() {
        String senderToken = registerAndGetToken(rest, base());
        Number senderId = createCharacter(rest, base(), senderToken, uniqueName());
        long sid = senderId.longValue();

        String recipientToken = registerAndGetToken(rest, base());
        Number recipientId = createCharacter(rest, base(), recipientToken, uniqueName());
        long rid = recipientId.longValue();

        // Send a plain (no-attachment) mail
        Map<String, Object> sendBody = Map.of(
                "senderCharacterId",    sid,
                "recipientCharacterId", rid,
                "subject",              "Test subject",
                "body",                 "Test body");
        ResponseEntity<Map> sendResp = rest.postForEntity(
                base() + "/api/mail/send",
                withToken(sendBody, senderToken),
                Map.class);
        assertThat(sendResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Number mailId = (Number) sendResp.getBody().get("id");

        // Recipient fetches inbox
        ResponseEntity<List> inboxResp = rest.exchange(
                base() + "/api/mail/" + rid,
                HttpMethod.GET,
                getWithToken(recipientToken),
                List.class);
        assertThat(inboxResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        List<?> inbox = inboxResp.getBody();
        assertThat(inbox).isNotNull().isNotEmpty();

        boolean found = inbox.stream()
                .anyMatch(m -> mailId.longValue() ==
                        ((Number) ((Map<?, ?>) m).get("id")).longValue());
        assertThat(found)
                .as("Sent mail must appear in recipient's inbox")
                .isTrue();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. Mark as read
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("markRead_setsIsReadTrue")
    @SuppressWarnings("unchecked")
    void markRead_setsIsReadTrue() {
        String senderToken = registerAndGetToken(rest, base());
        Number senderId = createCharacter(rest, base(), senderToken, uniqueName());
        long sid = senderId.longValue();

        String recipientToken = registerAndGetToken(rest, base());
        Number recipientId = createCharacter(rest, base(), recipientToken, uniqueName());
        long rid = recipientId.longValue();

        Number mailId = sendPlainMail(senderToken, sid, rid);

        // Before reading — isRead should be false
        Map<String, Object> before = getMailFromInbox(recipientToken, rid, mailId.longValue());
        assertThat(before.get("read")).isEqualTo(false);

        // Mark as read
        Map<String, Object> readBody = Map.of("characterId", rid);
        ResponseEntity<Map> readResp = rest.postForEntity(
                base() + "/api/mail/" + mailId.longValue() + "/read",
                withToken(readBody, recipientToken),
                Map.class);
        assertThat(readResp.getStatusCode())
                .as("POST /api/mail/{id}/read must return 200")
                .isEqualTo(HttpStatus.OK);
        assertThat(readResp.getBody().get("read")).isEqualTo(true);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. Claim GEL → credits recipient; double-claim → 400
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("claimGel_creditsRecipient_doubleClaimIs400")
    @SuppressWarnings("unchecked")
    void claimGel_creditsRecipient_doubleClaimIs400() {
        String senderToken = registerAndGetToken(rest, base());
        Number senderId = createCharacter(rest, base(), senderToken, uniqueName());
        long sid = senderId.longValue();

        String recipientToken = registerAndGetToken(rest, base());
        Number recipientId = createCharacter(rest, base(), recipientToken, uniqueName());
        long rid = recipientId.longValue();

        double gelAttach = 15.0;
        double recipientWalletBefore = getWallet(recipientToken, rid);

        // Send GEL mail
        Map<String, Object> sendBody = Map.of(
                "senderCharacterId",    sid,
                "recipientCharacterId", rid,
                "subject",              "A gift",
                "body",                 "Enjoy",
                "attachKind",           "GEL",
                "attachAmount",         gelAttach);
        ResponseEntity<Map> sendResp = rest.postForEntity(
                base() + "/api/mail/send",
                withToken(sendBody, senderToken),
                Map.class);
        assertThat(sendResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Number mailId = (Number) sendResp.getBody().get("id");

        // First claim — should succeed
        Map<String, Object> claimBody = Map.of("characterId", rid);
        ResponseEntity<Map> claimResp = rest.postForEntity(
                base() + "/api/mail/" + mailId.longValue() + "/claim",
                withToken(claimBody, recipientToken),
                Map.class);
        assertThat(claimResp.getStatusCode())
                .as("First claim must return 200")
                .isEqualTo(HttpStatus.OK);
        assertThat(claimResp.getBody().get("claimed")).isEqualTo(true);

        // Recipient wallet must have increased
        double recipientWalletAfter = getWallet(recipientToken, rid);
        assertThat(recipientWalletAfter)
                .as("Recipient wallet must increase by GEL attachment amount")
                .isEqualTo(recipientWalletBefore + gelAttach);

        // Second claim — must return 400
        ResponseEntity<String> doubleClaim = rest.postForEntity(
                base() + "/api/mail/" + mailId.longValue() + "/claim",
                withToken(claimBody, recipientToken),
                String.class);
        assertThat(doubleClaim.getStatusCode())
                .as("Double-claim must return 400")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 5. GOODS attachment — send debits sender; claim grants to recipient
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("sendGoodsMail_claimGrantsGoodsToRecipient")
    @SuppressWarnings("unchecked")
    void sendGoodsMail_claimGrantsGoodsToRecipient() {
        // Sender buys 2 units from shop, then sends 1 as attachment
        String senderToken = registerAndGetToken(rest, base());
        Number senderId = createCharacter(rest, base(), senderToken, uniqueName());
        long sid = senderId.longValue();

        String recipientToken = registerAndGetToken(rest, base());
        Number recipientId = createCharacter(rest, base(), recipientToken, uniqueName());
        long rid = recipientId.longValue();

        buyGoods(senderToken, sid, "hoe", 2.0);

        // Verify sender owns hoe before send
        List<?> senderInvBefore = getInventory(senderToken, sid);
        double senderQtyBefore = goodsQty(senderInvBefore, "hoe");
        assertThat(senderQtyBefore).as("Sender must own hoe before send").isGreaterThan(0);

        // Send with GOODS attachment (1 hoe)
        Map<String, Object> sendBody = Map.of(
                "senderCharacterId",    sid,
                "recipientCharacterId", rid,
                "subject",              "Here's a hoe",
                "body",                 "Use it well",
                "attachKind",           "GOODS",
                "attachRefId",          "hoe",
                "attachAmount",         1.0);
        ResponseEntity<Map> sendResp = rest.postForEntity(
                base() + "/api/mail/send",
                withToken(sendBody, senderToken),
                Map.class);
        assertThat(sendResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Number mailId = (Number) sendResp.getBody().get("id");

        // Sender's hoe quantity must have decreased by 1
        List<?> senderInvAfterSend = getInventory(senderToken, sid);
        double senderQtyAfterSend = goodsQty(senderInvAfterSend, "hoe");
        assertThat(senderQtyAfterSend)
                .as("Sender hoe qty must decrease by 1 on send (escrowed)")
                .isEqualTo(senderQtyBefore - 1.0);

        // Recipient claims
        Map<String, Object> claimBody = Map.of("characterId", rid);
        ResponseEntity<Map> claimResp = rest.postForEntity(
                base() + "/api/mail/" + mailId.longValue() + "/claim",
                withToken(claimBody, recipientToken),
                Map.class);
        assertThat(claimResp.getStatusCode())
                .as("Claim must return 200")
                .isEqualTo(HttpStatus.OK);

        // Recipient must now own hoe
        List<?> recipientInv = getInventory(recipientToken, rid);
        double recipientQty = goodsQty(recipientInv, "hoe");
        assertThat(recipientQty)
                .as("Recipient must own at least 1 hoe after claiming GOODS mail")
                .isGreaterThanOrEqualTo(1.0);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 6. Ownership enforcement: non-recipient operations return 404
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("ownership_nonRecipientClaimIs404_nonRecipientReadIs404")
    @SuppressWarnings("unchecked")
    void ownership_nonRecipientClaimIs404_nonRecipientReadIs404() {
        String senderToken = registerAndGetToken(rest, base());
        Number senderId = createCharacter(rest, base(), senderToken, uniqueName());
        long sid = senderId.longValue();

        String recipientToken = registerAndGetToken(rest, base());
        Number recipientId = createCharacter(rest, base(), recipientToken, uniqueName());
        long rid = recipientId.longValue();

        // Third party
        String thirdToken = registerAndGetToken(rest, base());
        Number thirdId = createCharacter(rest, base(), thirdToken, uniqueName());
        long tid = thirdId.longValue();

        Number mailId = sendPlainMail(senderToken, sid, rid);

        // Third party tries to read
        Map<String, Object> readBody = Map.of("characterId", tid);
        ResponseEntity<String> readResp = rest.postForEntity(
                base() + "/api/mail/" + mailId.longValue() + "/read",
                withToken(readBody, thirdToken),
                String.class);
        assertThat(readResp.getStatusCode())
                .as("Non-recipient read must return 404")
                .isEqualTo(HttpStatus.NOT_FOUND);

        // Sender also cannot read recipient's mail as a different character
        Map<String, Object> sendBody2 = Map.of(
                "senderCharacterId",    sid,
                "recipientCharacterId", rid,
                "subject",              "Gel mail",
                "body",                 "Take it",
                "attachKind",           "GEL",
                "attachAmount",         5.0);
        ResponseEntity<Map> gelMailResp = rest.postForEntity(
                base() + "/api/mail/send",
                withToken(sendBody2, senderToken),
                Map.class);
        assertThat(gelMailResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Number gelMailId = (Number) gelMailResp.getBody().get("id");

        // Third party tries to claim
        Map<String, Object> claimBody = Map.of("characterId", tid);
        ResponseEntity<String> claimResp = rest.postForEntity(
                base() + "/api/mail/" + gelMailId.longValue() + "/claim",
                withToken(claimBody, thirdToken),
                String.class);
        assertThat(claimResp.getStatusCode())
                .as("Non-recipient claim must return 404")
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 7. Delete blocked while unclaimed attachment present; ok after claim
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("delete_blockedByUnclaimedAttachment_okAfterClaim")
    @SuppressWarnings("unchecked")
    void delete_blockedByUnclaimedAttachment_okAfterClaim() {
        String senderToken = registerAndGetToken(rest, base());
        Number senderId = createCharacter(rest, base(), senderToken, uniqueName());
        long sid = senderId.longValue();

        String recipientToken = registerAndGetToken(rest, base());
        Number recipientId = createCharacter(rest, base(), recipientToken, uniqueName());
        long rid = recipientId.longValue();

        // Send mail with GEL attachment
        Map<String, Object> sendBody = Map.of(
                "senderCharacterId",    sid,
                "recipientCharacterId", rid,
                "subject",              "Take it",
                "body",                 "Or else",
                "attachKind",           "GEL",
                "attachAmount",         7.0);
        ResponseEntity<Map> sendResp = rest.postForEntity(
                base() + "/api/mail/send",
                withToken(sendBody, senderToken),
                Map.class);
        assertThat(sendResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Number mailId = (Number) sendResp.getBody().get("id");

        // Try to delete before claiming — should return 400
        Map<String, Object> deleteBody = Map.of("characterId", rid);
        ResponseEntity<String> deleteBeforeClaim = rest.postForEntity(
                base() + "/api/mail/" + mailId.longValue() + "/delete",
                withToken(deleteBody, recipientToken),
                String.class);
        assertThat(deleteBeforeClaim.getStatusCode())
                .as("Delete with unclaimed attachment must return 400")
                .isEqualTo(HttpStatus.BAD_REQUEST);

        // Claim the attachment
        Map<String, Object> claimBody = Map.of("characterId", rid);
        ResponseEntity<Map> claimResp = rest.postForEntity(
                base() + "/api/mail/" + mailId.longValue() + "/claim",
                withToken(claimBody, recipientToken),
                Map.class);
        assertThat(claimResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Now delete should succeed
        ResponseEntity<String> deleteAfterClaim = rest.postForEntity(
                base() + "/api/mail/" + mailId.longValue() + "/delete",
                withToken(deleteBody, recipientToken),
                String.class);
        assertThat(deleteAfterClaim.getStatusCode())
                .as("Delete after claiming attachment must return 200")
                .isEqualTo(HttpStatus.OK);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 8. No-attachment mail persists; claim returns 400
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("noAttachmentMail_persists_claimReturns400")
    @SuppressWarnings("unchecked")
    void noAttachmentMail_persists_claimReturns400() {
        String senderToken = registerAndGetToken(rest, base());
        Number senderId = createCharacter(rest, base(), senderToken, uniqueName());
        long sid = senderId.longValue();

        String recipientToken = registerAndGetToken(rest, base());
        Number recipientId = createCharacter(rest, base(), recipientToken, uniqueName());
        long rid = recipientId.longValue();

        Number mailId = sendPlainMail(senderToken, sid, rid);

        // Claim on a no-attachment mail must fail with 400
        Map<String, Object> claimBody = Map.of("characterId", rid);
        ResponseEntity<String> claimResp = rest.postForEntity(
                base() + "/api/mail/" + mailId.longValue() + "/claim",
                withToken(claimBody, recipientToken),
                String.class);
        assertThat(claimResp.getStatusCode())
                .as("Claim on no-attachment mail must return 400")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 9. Insufficient GEL on send → 400; sender wallet unchanged
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("send_insufficientGel_returns400_walletUnchanged")
    @SuppressWarnings("unchecked")
    void send_insufficientGel_returns400_walletUnchanged() {
        String senderToken = registerAndGetToken(rest, base());
        Number senderId = createCharacter(rest, base(), senderToken, uniqueName());
        long sid = senderId.longValue();

        String recipientToken = registerAndGetToken(rest, base());
        Number recipientId = createCharacter(rest, base(), recipientToken, uniqueName());
        long rid = recipientId.longValue();

        double walletBefore = getWallet(senderToken, sid);

        // Try to send more than the wallet has
        Map<String, Object> sendBody = Map.of(
                "senderCharacterId",    sid,
                "recipientCharacterId", rid,
                "subject",              "Big gift",
                "body",                 "Way too much",
                "attachKind",           "GEL",
                "attachAmount",         walletBefore + 1000.0);

        ResponseEntity<String> resp = rest.postForEntity(
                base() + "/api/mail/send",
                withToken(sendBody, senderToken),
                String.class);
        assertThat(resp.getStatusCode())
                .as("Sending more GEL than wallet must return 400")
                .isEqualTo(HttpStatus.BAD_REQUEST);

        // Sender wallet must be unchanged
        double walletAfter = getWallet(senderToken, sid);
        assertThat(walletAfter)
                .as("Sender wallet must not change on a failed send")
                .isEqualTo(walletBefore);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 10. Delete a plain mail (no attachment) — should succeed immediately
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("delete_plainMail_succeeds")
    @SuppressWarnings("unchecked")
    void delete_plainMail_succeeds() {
        String senderToken = registerAndGetToken(rest, base());
        Number senderId = createCharacter(rest, base(), senderToken, uniqueName());
        long sid = senderId.longValue();

        String recipientToken = registerAndGetToken(rest, base());
        Number recipientId = createCharacter(rest, base(), recipientToken, uniqueName());
        long rid = recipientId.longValue();

        Number mailId = sendPlainMail(senderToken, sid, rid);

        Map<String, Object> deleteBody = Map.of("characterId", rid);
        ResponseEntity<String> deleteResp = rest.postForEntity(
                base() + "/api/mail/" + mailId.longValue() + "/delete",
                withToken(deleteBody, recipientToken),
                String.class);
        assertThat(deleteResp.getStatusCode())
                .as("Delete on plain mail (no attachment) must return 200")
                .isEqualTo(HttpStatus.OK);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    /** Send a plain (no-attachment) mail and return its id. */
    @SuppressWarnings("unchecked")
    private Number sendPlainMail(String senderToken, long senderCharId, long recipientCharId) {
        Map<String, Object> body = Map.of(
                "senderCharacterId",    senderCharId,
                "recipientCharacterId", recipientCharId,
                "subject",              "Hello",
                "body",                 "World");
        ResponseEntity<Map> resp = rest.postForEntity(
                base() + "/api/mail/send",
                withToken(body, senderToken),
                Map.class);
        assertThat(resp.getStatusCode())
                .as("Plain mail send must return 200")
                .isEqualTo(HttpStatus.OK);
        return (Number) resp.getBody().get("id");
    }

    /** Retrieve the wallet balance for a character. */
    @SuppressWarnings("unchecked")
    private double getWallet(String token, long characterId) {
        ResponseEntity<Map> resp = rest.exchange(
                base() + "/api/characters/" + characterId,
                HttpMethod.GET,
                getWithToken(token),
                Map.class);
        assertThat(resp.getStatusCode())
                .as("GET /api/characters/{id} must succeed")
                .isEqualTo(HttpStatus.OK);
        return ((Number) resp.getBody().get("walletGel")).doubleValue();
    }

    /** Fetch the character's goods inventory. */
    @SuppressWarnings("unchecked")
    private List<?> getInventory(String token, long characterId) {
        ResponseEntity<List> resp = rest.exchange(
                base() + "/api/goods/" + characterId,
                HttpMethod.GET,
                getWithToken(token),
                List.class);
        assertThat(resp.getStatusCode())
                .as("GET /api/goods/{characterId} must return 200")
                .isEqualTo(HttpStatus.OK);
        return resp.getBody();
    }

    /** Buy goods from the NPC shop. */
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
                .as("Shop buy must succeed for test seeding")
                .isEqualTo(HttpStatus.OK);
    }

    /**
     * Find a specific mail in the character's inbox.
     * Calls GET /api/mail/{characterId} and returns the matching mail as a Map.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> getMailFromInbox(String token, long characterId, long mailId) {
        ResponseEntity<List> resp = rest.exchange(
                base() + "/api/mail/" + characterId,
                HttpMethod.GET,
                getWithToken(token),
                List.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<?> inbox = resp.getBody();
        assertThat(inbox).isNotNull();
        return inbox.stream()
                .map(m -> (Map<String, Object>) m)
                .filter(m -> ((Number) m.get("id")).longValue() == mailId)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Mail " + mailId + " not found in inbox"));
    }

    /** Extract the quantity of a specific goodTypeId from an inventory list. */
    private double goodsQty(List<?> inventory, String goodTypeId) {
        return inventory.stream()
                .map(o -> (Map<?, ?>) o)
                .filter(o -> goodTypeId.equals(o.get("goodTypeId")))
                .mapToDouble(o -> ((Number) o.get("quantity")).doubleValue())
                .sum();
    }
}
