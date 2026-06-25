package com.game.chat;

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
 * Integration tests for /api/chat/** (LANE CHAT).
 *
 * <p>Scenarios covered:
 * <ol>
 *   <li>Send to GLOBAL then GET returns the message (newest-50 path).</li>
 *   <li>REGION post from a character whose home region differs → 400.</li>
 *   <li>REGION post from a character whose home region matches → 200.</li>
 *   <li>GUILD channel — non-member send → 403; founder member can send → 200.</li>
 *   <li>DM between A and B is readable by A and B; third character C gets 403.</li>
 *   <li>Empty body → 400.</li>
 *   <li>Body longer than 500 chars → 400.</li>
 *   <li>sinceId polling returns only newer messages.</li>
 * </ol>
 *
 * <p>Uses {@link AccountTestHelper} static helpers. All JSON bodies used with
 * {@code containsKey} are declared as {@code Map<String,Object>} (not {@code Map<?,?>}).
 * 4xx calls read with {@code String.class}; list endpoints with {@code List.class}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@SuppressWarnings("unchecked")
class ChatControllerTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    private String base() {
        return "http://localhost:" + port;
    }

    private static String uniqueName() {
        return "ch_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    // ── Helper: create a character with a given homeRegion ────────────────────

    private Number createCharacterWithRegion(TestRestTemplate rest, String base,
                                              String token, String name, String region) {
        Map<String, String> body = Map.of(
                "name", name,
                "careerType", "GROWER",
                "homeRegion", region);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        ResponseEntity<Map> resp = rest.postForEntity(
                base + "/api/characters",
                new HttpEntity<>(body, headers),
                Map.class);
        assertThat(resp.getStatusCode())
                .as("Character creation with region " + region + " must return 201")
                .isEqualTo(HttpStatus.CREATED);
        return (Number) resp.getBody().get("id");
    }

    // ── Helper: send a chat message ───────────────────────────────────────────

    private ResponseEntity<Map> sendMessage(String token, long characterId,
                                             String channel, String body) {
        Map<String, Object> req = Map.of(
                "characterId", characterId,
                "channel", channel,
                "body", body);
        return rest.postForEntity(
                base() + "/api/chat/send",
                withToken(req, token),
                Map.class);
    }

    // ── 1. Send to GLOBAL then GET returns the message ───────────────────────

    @Test
    @DisplayName("global_sendAndGet_returnsMessage")
    void global_sendAndGet_returnsMessage() {
        String token  = registerAndGetToken(rest, base());
        Number charId = createCharacter(rest, base(), token, uniqueName());
        long cid = charId.longValue();

        // Send a message to GLOBAL
        ResponseEntity<Map> sendResp = sendMessage(token, cid, "GLOBAL", "Hello world!");
        assertThat(sendResp.getStatusCode())
                .as("POST /api/chat/send to GLOBAL must return 200")
                .isEqualTo(HttpStatus.OK);
        Map<String, Object> sent = (Map<String, Object>) sendResp.getBody();
        assertThat(sent).containsKey("id");
        assertThat(sent.get("channel")).isEqualTo("GLOBAL");
        assertThat(sent.get("bodyText")).isEqualTo("Hello world!");
        assertThat(sent.get("senderName")).isNotNull();

        // GET /api/chat/GLOBAL?characterId={cid}
        ResponseEntity<List> getResp = rest.exchange(
                base() + "/api/chat/GLOBAL?characterId=" + cid,
                HttpMethod.GET,
                getWithToken(token),
                List.class);
        assertThat(getResp.getStatusCode())
                .as("GET /api/chat/GLOBAL must return 200")
                .isEqualTo(HttpStatus.OK);
        List<?> messages = getResp.getBody();
        assertThat(messages).isNotNull();
        assertThat(messages).isNotEmpty();

        // The message we just sent must appear in the result
        boolean found = messages.stream()
                .map(m -> (Map<?, ?>) m)
                .anyMatch(m -> "Hello world!".equals(m.get("bodyText")));
        assertThat(found).as("Sent message must appear in GET response").isTrue();
    }

    // ── 2. REGION post from wrong-region character → 400 ─────────────────────

    @Test
    @DisplayName("region_wrongHomeRegion_400")
    void region_wrongHomeRegion_400() {
        // Create a character in KARTLI
        String token = registerAndGetToken(rest, base());
        Number charId = createCharacterWithRegion(rest, base(), token, uniqueName(), "KARTLI");
        long cid = charId.longValue();

        // Try to post to REGION:KAKHETI — should fail with 400
        ResponseEntity<String> resp = rest.postForEntity(
                base() + "/api/chat/send",
                withToken(Map.of("characterId", cid, "channel", "REGION:KAKHETI", "body", "hi"), token),
                String.class);
        assertThat(resp.getStatusCode())
                .as("Wrong-region post must return 400")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ── 3. REGION post from matching-region character → 200 ──────────────────

    @Test
    @DisplayName("region_correctHomeRegion_200")
    void region_correctHomeRegion_200() {
        // createCharacter from AccountTestHelper defaults to KAKHETI
        String token = registerAndGetToken(rest, base());
        Number charId = createCharacter(rest, base(), token, uniqueName());
        long cid = charId.longValue();

        // Post to REGION:KAKHETI — should succeed
        ResponseEntity<Map> resp = sendMessage(token, cid, "REGION:KAKHETI", "Gamarjoba!");
        assertThat(resp.getStatusCode())
                .as("Correct-region post must return 200")
                .isEqualTo(HttpStatus.OK);
        Map<String, Object> msg = (Map<String, Object>) resp.getBody();
        assertThat(msg.get("channel")).isEqualTo("REGION:KAKHETI");
        assertThat(msg.get("bodyText")).isEqualTo("Gamarjoba!");
    }

    // ── 4a. GUILD channel — non-member send → 403 ────────────────────────────

    @Test
    @DisplayName("guild_nonMemberSend_403")
    void guild_nonMemberSend_403() {
        // Founder creates guild
        String founderToken = registerAndGetToken(rest, base());
        Number founderId    = createCharacter(rest, base(), founderToken, uniqueName());
        long fid = founderId.longValue();

        Map<String, Object> createBody = Map.of("characterId", fid, "name", uniqueName());
        ResponseEntity<Map> createResp = rest.postForEntity(
                base() + "/api/guild/create",
                withToken(createBody, founderToken),
                Map.class);
        assertThat(createResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> guild = (Map<String, Object>) createResp.getBody();
        long guildId = ((Number) guild.get("id")).longValue();

        // Non-member character
        String nonMemberToken = registerAndGetToken(rest, base());
        Number nonMemberId    = createCharacter(rest, base(), nonMemberToken, uniqueName());
        long nmid = nonMemberId.longValue();

        // Non-member tries to send to GUILD channel → 403
        String guildChannel = "GUILD:" + guildId;
        ResponseEntity<String> resp = rest.postForEntity(
                base() + "/api/chat/send",
                withToken(Map.of("characterId", nmid, "channel", guildChannel, "body", "Intruder!"),
                          nonMemberToken),
                String.class);
        assertThat(resp.getStatusCode())
                .as("Non-member send to GUILD channel must return 403")
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ── 4b. GUILD channel — founder member can send → 200 ────────────────────

    @Test
    @DisplayName("guild_memberSend_200")
    void guild_memberSend_200() {
        // Founder creates guild
        String founderToken = registerAndGetToken(rest, base());
        Number founderId    = createCharacter(rest, base(), founderToken, uniqueName());
        long fid = founderId.longValue();

        Map<String, Object> createBody = Map.of("characterId", fid, "name", uniqueName());
        ResponseEntity<Map> createResp = rest.postForEntity(
                base() + "/api/guild/create",
                withToken(createBody, founderToken),
                Map.class);
        assertThat(createResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> guild = (Map<String, Object>) createResp.getBody();
        long guildId = ((Number) guild.get("id")).longValue();

        String guildChannel = "GUILD:" + guildId;

        // Founder sends to guild channel → 200
        ResponseEntity<Map> resp = sendMessage(founderToken, fid, guildChannel, "Guild meeting!");
        assertThat(resp.getStatusCode())
                .as("Guild member (founder) send must return 200")
                .isEqualTo(HttpStatus.OK);
        Map<String, Object> msg = (Map<String, Object>) resp.getBody();
        assertThat(msg.get("channel")).isEqualTo(guildChannel);
        assertThat(msg.get("bodyText")).isEqualTo("Guild meeting!");
    }

    // ── 5. DM: A and B can read; C gets 403 ──────────────────────────────────

    @Test
    @DisplayName("dm_participantsCanRead_thirdCharacterForbidden")
    void dm_participantsCanRead_thirdCharacterForbidden() {
        String tokenA = registerAndGetToken(rest, base());
        Number charA  = createCharacter(rest, base(), tokenA, uniqueName());
        long aidLong  = charA.longValue();

        String tokenB = registerAndGetToken(rest, base());
        Number charB  = createCharacter(rest, base(), tokenB, uniqueName());
        long bidLong  = charB.longValue();

        String tokenC = registerAndGetToken(rest, base());
        Number charC  = createCharacter(rest, base(), tokenC, uniqueName());
        long cidLong  = charC.longValue();

        // Build canonical DM channel
        long lo = Math.min(aidLong, bidLong);
        long hi = Math.max(aidLong, bidLong);
        String dmChannel = "DM:" + lo + ":" + hi;

        // A sends a DM to B → 200
        ResponseEntity<Map> sendResp = sendMessage(tokenA, aidLong, dmChannel, "Hey B!");
        assertThat(sendResp.getStatusCode())
                .as("A sending DM must return 200")
                .isEqualTo(HttpStatus.OK);

        // A reads the DM → 200
        ResponseEntity<List> aRead = rest.exchange(
                base() + "/api/chat/" + dmChannel + "?characterId=" + aidLong,
                HttpMethod.GET,
                getWithToken(tokenA),
                List.class);
        assertThat(aRead.getStatusCode())
                .as("A reading DM must return 200")
                .isEqualTo(HttpStatus.OK);
        assertThat(aRead.getBody()).isNotEmpty();

        // B reads the DM → 200
        ResponseEntity<List> bRead = rest.exchange(
                base() + "/api/chat/" + dmChannel + "?characterId=" + bidLong,
                HttpMethod.GET,
                getWithToken(tokenB),
                List.class);
        assertThat(bRead.getStatusCode())
                .as("B reading DM must return 200")
                .isEqualTo(HttpStatus.OK);

        // C tries to read the DM → 403
        ResponseEntity<String> cRead = rest.exchange(
                base() + "/api/chat/" + dmChannel + "?characterId=" + cidLong,
                HttpMethod.GET,
                getWithToken(tokenC),
                String.class);
        assertThat(cRead.getStatusCode())
                .as("C reading A-B DM must return 403")
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ── 6. Empty body → 400 ──────────────────────────────────────────────────

    @Test
    @DisplayName("send_emptyBody_400")
    void send_emptyBody_400() {
        String token  = registerAndGetToken(rest, base());
        Number charId = createCharacter(rest, base(), token, uniqueName());
        long cid = charId.longValue();

        ResponseEntity<String> resp = rest.postForEntity(
                base() + "/api/chat/send",
                withToken(Map.of("characterId", cid, "channel", "GLOBAL", "body", ""), token),
                String.class);
        assertThat(resp.getStatusCode())
                .as("Empty body must return 400")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ── 7. Body longer than 500 chars → 400 ──────────────────────────────────

    @Test
    @DisplayName("send_bodyTooLong_400")
    void send_bodyTooLong_400() {
        String token  = registerAndGetToken(rest, base());
        Number charId = createCharacter(rest, base(), token, uniqueName());
        long cid = charId.longValue();

        String longBody = "x".repeat(501);

        ResponseEntity<String> resp = rest.postForEntity(
                base() + "/api/chat/send",
                withToken(Map.of("characterId", cid, "channel", "GLOBAL", "body", longBody), token),
                String.class);
        assertThat(resp.getStatusCode())
                .as("Body longer than 500 chars must return 400")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ── 8. sinceId polling returns only newer messages ────────────────────────

    @Test
    @DisplayName("get_sinceId_returnsOnlyNewerMessages")
    void get_sinceId_returnsOnlyNewerMessages() {
        String token  = registerAndGetToken(rest, base());
        Number charId = createCharacter(rest, base(), token, uniqueName());
        long cid = charId.longValue();

        // Use a unique sub-channel name so we don't see messages from other tests
        String channel = "GLOBAL";

        // Send first message
        ResponseEntity<Map> first = sendMessage(token, cid, channel, "First message");
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        long firstId = ((Number) first.getBody().get("id")).longValue();

        // Send second message
        ResponseEntity<Map> second = sendMessage(token, cid, channel, "Second message");
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
        long secondId = ((Number) second.getBody().get("id")).longValue();

        // Send third message
        ResponseEntity<Map> third = sendMessage(token, cid, channel, "Third message");
        assertThat(third.getStatusCode()).isEqualTo(HttpStatus.OK);
        long thirdId = ((Number) third.getBody().get("id")).longValue();

        // Poll with sinceId=firstId → should get second and third (id > firstId)
        ResponseEntity<List> pollResp = rest.exchange(
                base() + "/api/chat/" + channel + "?characterId=" + cid + "&sinceId=" + firstId,
                HttpMethod.GET,
                getWithToken(token),
                List.class);
        assertThat(pollResp.getStatusCode())
                .as("sinceId GET must return 200")
                .isEqualTo(HttpStatus.OK);

        List<?> polled = pollResp.getBody();
        assertThat(polled).isNotNull();

        // All returned messages must have id > firstId
        boolean allNewer = polled.stream()
                .map(m -> (Map<?, ?>) m)
                .allMatch(m -> ((Number) m.get("id")).longValue() > firstId);
        assertThat(allNewer)
                .as("All polled messages must have id > sinceId=" + firstId)
                .isTrue();

        // The first message itself must NOT appear
        boolean firstPresent = polled.stream()
                .map(m -> (Map<?, ?>) m)
                .anyMatch(m -> ((Number) m.get("id")).longValue() == firstId);
        assertThat(firstPresent)
                .as("First message must not appear in sinceId poll")
                .isFalse();

        // Second and third must appear
        boolean secondPresent = polled.stream()
                .map(m -> (Map<?, ?>) m)
                .anyMatch(m -> ((Number) m.get("id")).longValue() == secondId);
        boolean thirdPresent = polled.stream()
                .map(m -> (Map<?, ?>) m)
                .anyMatch(m -> ((Number) m.get("id")).longValue() == thirdId);
        assertThat(secondPresent).as("Second message must appear in poll").isTrue();
        assertThat(thirdPresent).as("Third message must appear in poll").isTrue();
    }

    // ── 9. GUILD channel — joined member can send and read ───────────────────

    @Test
    @DisplayName("guild_joinedMemberCanSendAndRead")
    void guild_joinedMemberCanSendAndRead() {
        // Founder creates guild
        String founderToken = registerAndGetToken(rest, base());
        Number founderId    = createCharacter(rest, base(), founderToken, uniqueName());
        long fid = founderId.longValue();

        Map<String, Object> createBody = Map.of("characterId", fid, "name", uniqueName());
        ResponseEntity<Map> createResp = rest.postForEntity(
                base() + "/api/guild/create",
                withToken(createBody, founderToken),
                Map.class);
        assertThat(createResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        long guildId = ((Number) createResp.getBody().get("id")).longValue();
        String guildChannel = "GUILD:" + guildId;

        // Member joins guild
        String memberToken = registerAndGetToken(rest, base());
        Number memberId    = createCharacter(rest, base(), memberToken, uniqueName());
        long mid = memberId.longValue();

        Map<String, Object> joinBody = Map.of("characterId", mid);
        ResponseEntity<Map> joinResp = rest.postForEntity(
                base() + "/api/guild/" + guildId + "/join",
                withToken(joinBody, memberToken),
                Map.class);
        assertThat(joinResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Member sends to guild channel → 200
        ResponseEntity<Map> sendResp = sendMessage(memberToken, mid, guildChannel, "Hi guild!");
        assertThat(sendResp.getStatusCode())
                .as("Joined member send to GUILD channel must return 200")
                .isEqualTo(HttpStatus.OK);

        // Member reads guild channel → sees the message
        ResponseEntity<List> getResp = rest.exchange(
                base() + "/api/chat/" + guildChannel + "?characterId=" + mid,
                HttpMethod.GET,
                getWithToken(memberToken),
                List.class);
        assertThat(getResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<?> messages = getResp.getBody();
        assertThat(messages).isNotEmpty();

        boolean found = messages.stream()
                .map(m -> (Map<?, ?>) m)
                .anyMatch(m -> "Hi guild!".equals(m.get("bodyText")));
        assertThat(found).as("Guild member's message must appear in GET").isTrue();
    }
}
