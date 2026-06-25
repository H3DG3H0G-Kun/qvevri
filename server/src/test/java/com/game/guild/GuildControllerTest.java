package com.game.guild;

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
 * Integration tests for /api/guild/** (LANE GUILDS).
 *
 * <p>Scenarios covered:
 * <ol>
 *   <li>Create guild → FOUNDER membership created; GET guild returns guild + member.</li>
 *   <li>Duplicate guild name → 400.</li>
 *   <li>Character already in a guild cannot create another guild → 400.</li>
 *   <li>Character already in a guild cannot join another guild → 400.</li>
 *   <li>Join guild → MEMBER membership added; GET guild shows both members.</li>
 *   <li>Deposit moves wallet → treasury (verified via GET /api/characters + GET /api/guild).</li>
 *   <li>Withdraw (FOUNDER) moves treasury → wallet.</li>
 *   <li>Non-founder withdraw → 400.</li>
 *   <li>Withdraw more than treasury → 400.</li>
 *   <li>Leave removes membership; founder blocked while members remain → 400.</li>
 * </ol>
 *
 * <p>Uses {@link AccountTestHelper} static helpers. All JSON bodies are
 * {@code Map<String,Object>} so that {@code containsKey(String)} compiles
 * without the generics-capture error that arises with {@code Map<?,?>}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class GuildControllerTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    private String base() {
        return "http://localhost:" + port;
    }

    private static String uniqueName() {
        return "g_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 1. Create guild → FOUNDER membership; GET returns guild + member
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("createGuild_founderMembershipCreated_getReturnsGuildAndMember")
    @SuppressWarnings("unchecked")
    void createGuild_founderMembershipCreated_getReturnsGuildAndMember() {
        String token   = registerAndGetToken(rest, base());
        Number charId  = createCharacter(rest, base(), token, uniqueName());
        long   cid     = charId.longValue();
        String guildName = uniqueName();

        // POST /api/guild/create
        Map<String, Object> createBody = Map.of("characterId", cid, "name", guildName);
        ResponseEntity<Map> createResp = rest.postForEntity(
                base() + "/api/guild/create",
                withToken(createBody, token),
                Map.class);

        assertThat(createResp.getStatusCode())
                .as("POST /api/guild/create must return 200")
                .isEqualTo(HttpStatus.OK);

        Map<String, Object> guild = (Map<String, Object>) createResp.getBody();
        assertThat(guild).isNotNull();
        assertThat(guild).containsKey("id");
        assertThat(guild.get("name")).isEqualTo(guildName);
        assertThat(((Number) guild.get("founderCharacterId")).longValue()).isEqualTo(cid);
        assertThat(((Number) guild.get("treasuryGel")).doubleValue()).isEqualTo(0.0);

        Number guildId = (Number) guild.get("id");

        // GET /api/guild/{guildId}
        ResponseEntity<Map> getResp = rest.exchange(
                base() + "/api/guild/" + guildId.longValue(),
                HttpMethod.GET,
                getWithToken(token),
                Map.class);

        assertThat(getResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> view = (Map<String, Object>) getResp.getBody();
        assertThat(view).isNotNull();
        assertThat(view).containsKey("guild");
        assertThat(view).containsKey("members");

        List<?> members = (List<?>) view.get("members");
        assertThat(members).hasSize(1);

        Map<?, ?> founderMember = (Map<?, ?>) members.get(0);
        assertThat(((Number) founderMember.get("characterId")).longValue()).isEqualTo(cid);
        assertThat(founderMember.get("guildRole")).isEqualTo("FOUNDER");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. Duplicate guild name → 400
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("createGuild_duplicateName_400")
    @SuppressWarnings("unchecked")
    void createGuild_duplicateName_400() {
        String token1  = registerAndGetToken(rest, base());
        Number charId1 = createCharacter(rest, base(), token1, uniqueName());
        String token2  = registerAndGetToken(rest, base());
        Number charId2 = createCharacter(rest, base(), token2, uniqueName());
        String sameName = uniqueName();

        // First guild succeeds
        Map<String, Object> body1 = Map.of("characterId", charId1.longValue(), "name", sameName);
        rest.postForEntity(base() + "/api/guild/create", withToken(body1, token1), Map.class);

        // Second guild with same name → 400
        Map<String, Object> body2 = Map.of("characterId", charId2.longValue(), "name", sameName);
        ResponseEntity<Map> resp = rest.postForEntity(
                base() + "/api/guild/create",
                withToken(body2, token2),
                Map.class);

        assertThat(resp.getStatusCode())
                .as("Duplicate guild name must return 400")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. Character already in a guild cannot create another guild → 400
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("createGuild_characterAlreadyInGuild_400")
    @SuppressWarnings("unchecked")
    void createGuild_characterAlreadyInGuild_400() {
        String token = registerAndGetToken(rest, base());
        Number charId = createCharacter(rest, base(), token, uniqueName());
        long cid = charId.longValue();

        // Join first guild
        Map<String, Object> body1 = Map.of("characterId", cid, "name", uniqueName());
        rest.postForEntity(base() + "/api/guild/create", withToken(body1, token), Map.class);

        // Try to create a second guild with the same character → 400
        Map<String, Object> body2 = Map.of("characterId", cid, "name", uniqueName());
        ResponseEntity<Map> resp = rest.postForEntity(
                base() + "/api/guild/create",
                withToken(body2, token),
                Map.class);

        assertThat(resp.getStatusCode())
                .as("Character already in a guild cannot create another → 400")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. Character already in a guild cannot join another → 400
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("joinGuild_characterAlreadyInGuild_400")
    @SuppressWarnings("unchecked")
    void joinGuild_characterAlreadyInGuild_400() {
        // Founder creates guild A
        String founderToken = registerAndGetToken(rest, base());
        Number founderId    = createCharacter(rest, base(), founderToken, uniqueName());
        Map<String, Object> createBody = Map.of(
                "characterId", founderId.longValue(), "name", uniqueName());
        ResponseEntity<Map> createResp = rest.postForEntity(
                base() + "/api/guild/create", withToken(createBody, founderToken), Map.class);
        Number guildAId = (Number) createResp.getBody().get("id");

        // Founder creates guild B
        String founder2Token = registerAndGetToken(rest, base());
        Number founder2Id    = createCharacter(rest, base(), founder2Token, uniqueName());
        Map<String, Object> create2Body = Map.of(
                "characterId", founder2Id.longValue(), "name", uniqueName());
        ResponseEntity<Map> create2Resp = rest.postForEntity(
                base() + "/api/guild/create", withToken(create2Body, founder2Token), Map.class);
        Number guildBId = (Number) create2Resp.getBody().get("id");

        // Member joins guild A
        String memberToken = registerAndGetToken(rest, base());
        Number memberId    = createCharacter(rest, base(), memberToken, uniqueName());
        long mid = memberId.longValue();

        Map<String, Object> joinA = Map.of("characterId", mid);
        ResponseEntity<Map> joinAResp = rest.postForEntity(
                base() + "/api/guild/" + guildAId.longValue() + "/join",
                withToken(joinA, memberToken), Map.class);
        assertThat(joinAResp.getStatusCode())
                .as("First join must succeed")
                .isEqualTo(HttpStatus.OK);

        // Same character tries to join guild B → 400
        Map<String, Object> joinB = Map.of("characterId", mid);
        ResponseEntity<Map> joinBResp = rest.postForEntity(
                base() + "/api/guild/" + guildBId.longValue() + "/join",
                withToken(joinB, memberToken), Map.class);

        assertThat(joinBResp.getStatusCode())
                .as("Character already in a guild cannot join another → 400")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 5. Join guild adds MEMBER; GET guild shows both members
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("joinGuild_addsMember_getShowsBothMembers")
    @SuppressWarnings("unchecked")
    void joinGuild_addsMember_getShowsBothMembers() {
        // Founder
        String founderToken = registerAndGetToken(rest, base());
        Number founderId    = createCharacter(rest, base(), founderToken, uniqueName());
        long fid = founderId.longValue();
        Map<String, Object> createBody = Map.of("characterId", fid, "name", uniqueName());
        ResponseEntity<Map> createResp = rest.postForEntity(
                base() + "/api/guild/create", withToken(createBody, founderToken), Map.class);
        Number guildId = (Number) createResp.getBody().get("id");

        // Member joins
        String memberToken = registerAndGetToken(rest, base());
        Number memberId    = createCharacter(rest, base(), memberToken, uniqueName());
        long mid = memberId.longValue();
        Map<String, Object> joinBody = Map.of("characterId", mid);
        ResponseEntity<Map> joinResp = rest.postForEntity(
                base() + "/api/guild/" + guildId.longValue() + "/join",
                withToken(joinBody, memberToken), Map.class);

        assertThat(joinResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> newMember = (Map<String, Object>) joinResp.getBody();
        assertThat(newMember).containsKey("id");
        assertThat(newMember.get("guildRole")).isEqualTo("MEMBER");

        // GET guild → 2 members
        ResponseEntity<Map> getResp = rest.exchange(
                base() + "/api/guild/" + guildId.longValue(),
                HttpMethod.GET, getWithToken(founderToken), Map.class);
        assertThat(getResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<?> members = (List<?>) getResp.getBody().get("members");
        assertThat(members).hasSize(2);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 6. Deposit moves wallet → treasury
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("deposit_movesWalletToTreasury")
    @SuppressWarnings("unchecked")
    void deposit_movesWalletToTreasury() {
        String token  = registerAndGetToken(rest, base());
        Number charId = createCharacter(rest, base(), token, uniqueName());
        long cid = charId.longValue();

        // Create guild
        Map<String, Object> createBody = Map.of("characterId", cid, "name", uniqueName());
        ResponseEntity<Map> createResp = rest.postForEntity(
                base() + "/api/guild/create", withToken(createBody, token), Map.class);
        Number guildId = (Number) createResp.getBody().get("id");

        double walletBefore = getWallet(token, cid);
        double depositAmt   = 25.0;

        // Deposit
        Map<String, Object> depositBody = Map.of("characterId", cid, "amountGel", depositAmt);
        ResponseEntity<Map> depositResp = rest.postForEntity(
                base() + "/api/guild/" + guildId.longValue() + "/deposit",
                withToken(depositBody, token), Map.class);

        assertThat(depositResp.getStatusCode())
                .as("Deposit must return 200")
                .isEqualTo(HttpStatus.OK);

        Map<String, Object> updatedGuild = (Map<String, Object>) depositResp.getBody();
        assertThat(((Number) updatedGuild.get("treasuryGel")).doubleValue())
                .as("Treasury must increase by depositAmt")
                .isEqualTo(depositAmt);

        double walletAfter = getWallet(token, cid);
        assertThat(walletAfter)
                .as("Wallet must decrease by depositAmt")
                .isEqualTo(walletBefore - depositAmt);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 7. Withdraw (FOUNDER) moves treasury → wallet
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("withdraw_founderMovesTorsuryToWallet")
    @SuppressWarnings("unchecked")
    void withdraw_founderMovesTreasuryToWallet() {
        String token  = registerAndGetToken(rest, base());
        Number charId = createCharacter(rest, base(), token, uniqueName());
        long cid = charId.longValue();

        // Create guild and deposit first
        Map<String, Object> createBody = Map.of("characterId", cid, "name", uniqueName());
        ResponseEntity<Map> createResp = rest.postForEntity(
                base() + "/api/guild/create", withToken(createBody, token), Map.class);
        Number guildId = (Number) createResp.getBody().get("id");

        double depositAmt = 40.0;
        Map<String, Object> depositBody = Map.of("characterId", cid, "amountGel", depositAmt);
        rest.postForEntity(base() + "/api/guild/" + guildId.longValue() + "/deposit",
                withToken(depositBody, token), Map.class);

        double walletAfterDeposit = getWallet(token, cid);
        double withdrawAmt = 15.0;

        // Withdraw
        Map<String, Object> withdrawBody = Map.of("characterId", cid, "amountGel", withdrawAmt);
        ResponseEntity<Map> withdrawResp = rest.postForEntity(
                base() + "/api/guild/" + guildId.longValue() + "/withdraw",
                withToken(withdrawBody, token), Map.class);

        assertThat(withdrawResp.getStatusCode())
                .as("Founder withdraw must return 200")
                .isEqualTo(HttpStatus.OK);

        Map<String, Object> updatedGuild = (Map<String, Object>) withdrawResp.getBody();
        assertThat(((Number) updatedGuild.get("treasuryGel")).doubleValue())
                .as("Treasury must decrease by withdrawAmt")
                .isEqualTo(depositAmt - withdrawAmt);

        double walletAfterWithdraw = getWallet(token, cid);
        assertThat(walletAfterWithdraw)
                .as("Wallet must increase by withdrawAmt")
                .isEqualTo(walletAfterDeposit + withdrawAmt);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 8. Non-founder withdraw → 400
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("withdraw_nonFounder_400")
    @SuppressWarnings("unchecked")
    void withdraw_nonFounder_400() {
        // Founder creates guild and deposits
        String founderToken = registerAndGetToken(rest, base());
        Number founderId    = createCharacter(rest, base(), founderToken, uniqueName());
        long fid = founderId.longValue();
        Map<String, Object> createBody = Map.of("characterId", fid, "name", uniqueName());
        ResponseEntity<Map> createResp = rest.postForEntity(
                base() + "/api/guild/create", withToken(createBody, founderToken), Map.class);
        Number guildId = (Number) createResp.getBody().get("id");

        Map<String, Object> depositBody = Map.of("characterId", fid, "amountGel", 50.0);
        rest.postForEntity(base() + "/api/guild/" + guildId.longValue() + "/deposit",
                withToken(depositBody, founderToken), Map.class);

        // Member joins
        String memberToken = registerAndGetToken(rest, base());
        Number memberId    = createCharacter(rest, base(), memberToken, uniqueName());
        long mid = memberId.longValue();
        Map<String, Object> joinBody = Map.of("characterId", mid);
        rest.postForEntity(base() + "/api/guild/" + guildId.longValue() + "/join",
                withToken(joinBody, memberToken), Map.class);

        // Member tries to withdraw → 400
        Map<String, Object> withdrawBody = Map.of("characterId", mid, "amountGel", 10.0);
        ResponseEntity<Map> resp = rest.postForEntity(
                base() + "/api/guild/" + guildId.longValue() + "/withdraw",
                withToken(withdrawBody, memberToken), Map.class);

        assertThat(resp.getStatusCode())
                .as("Non-founder withdraw must return 400")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 9. Withdraw more than treasury → 400
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("withdraw_exceedsTreasury_400")
    @SuppressWarnings("unchecked")
    void withdraw_exceedsTreasury_400() {
        String token  = registerAndGetToken(rest, base());
        Number charId = createCharacter(rest, base(), token, uniqueName());
        long cid = charId.longValue();

        Map<String, Object> createBody = Map.of("characterId", cid, "name", uniqueName());
        ResponseEntity<Map> createResp = rest.postForEntity(
                base() + "/api/guild/create", withToken(createBody, token), Map.class);
        Number guildId = (Number) createResp.getBody().get("id");

        // Deposit 10 GEL
        Map<String, Object> depositBody = Map.of("characterId", cid, "amountGel", 10.0);
        rest.postForEntity(base() + "/api/guild/" + guildId.longValue() + "/deposit",
                withToken(depositBody, token), Map.class);

        // Try to withdraw 999 GEL → 400
        Map<String, Object> withdrawBody = Map.of("characterId", cid, "amountGel", 999.0);
        ResponseEntity<Map> resp = rest.postForEntity(
                base() + "/api/guild/" + guildId.longValue() + "/withdraw",
                withToken(withdrawBody, token), Map.class);

        assertThat(resp.getStatusCode())
                .as("Withdrawing more than treasury must return 400")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 10. Leave removes membership; founder blocked while members remain → 400
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("leave_removesMembership_founderBlockedWithMembers")
    @SuppressWarnings("unchecked")
    void leave_removesMembership_founderBlockedWithMembers() {
        // Founder
        String founderToken = registerAndGetToken(rest, base());
        Number founderId    = createCharacter(rest, base(), founderToken, uniqueName());
        long fid = founderId.longValue();
        Map<String, Object> createBody = Map.of("characterId", fid, "name", uniqueName());
        ResponseEntity<Map> createResp = rest.postForEntity(
                base() + "/api/guild/create", withToken(createBody, founderToken), Map.class);
        Number guildId = (Number) createResp.getBody().get("id");

        // Member joins
        String memberToken = registerAndGetToken(rest, base());
        Number memberId    = createCharacter(rest, base(), memberToken, uniqueName());
        long mid = memberId.longValue();
        Map<String, Object> joinBody = Map.of("characterId", mid);
        rest.postForEntity(base() + "/api/guild/" + guildId.longValue() + "/join",
                withToken(joinBody, memberToken), Map.class);

        // Founder tries to leave while member is still in the guild → 400
        Map<String, Object> founderLeaveBody = Map.of("characterId", fid);
        ResponseEntity<Map> founderLeaveResp = rest.postForEntity(
                base() + "/api/guild/" + guildId.longValue() + "/leave",
                withToken(founderLeaveBody, founderToken), Map.class);

        assertThat(founderLeaveResp.getStatusCode())
                .as("Founder cannot leave while other members remain → 400")
                .isEqualTo(HttpStatus.BAD_REQUEST);

        // Member leaves successfully
        Map<String, Object> memberLeaveBody = Map.of("characterId", mid);
        ResponseEntity<Void> memberLeaveResp = rest.postForEntity(
                base() + "/api/guild/" + guildId.longValue() + "/leave",
                withToken(memberLeaveBody, memberToken), Void.class);

        assertThat(memberLeaveResp.getStatusCode())
                .as("Member leave must succeed with 200")
                .isEqualTo(HttpStatus.OK);

        // GET guild → only 1 member (founder) remains
        ResponseEntity<Map> getResp = rest.exchange(
                base() + "/api/guild/" + guildId.longValue(),
                HttpMethod.GET, getWithToken(founderToken), Map.class);
        List<?> members = (List<?>) getResp.getBody().get("members");
        assertThat(members).hasSize(1);

        // Now founder can leave (last member)
        ResponseEntity<Void> founderLeaveNow = rest.postForEntity(
                base() + "/api/guild/" + guildId.longValue() + "/leave",
                withToken(founderLeaveBody, founderToken), Void.class);

        assertThat(founderLeaveNow.getStatusCode())
                .as("Founder can leave when they are the last member")
                .isEqualTo(HttpStatus.OK);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    /** Fetch the character's wallet balance via GET /api/characters/{id}. */
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
}
