package com.game.ranking;

import com.game.account.AccountTokenService;
import com.game.character.CharacterService;
import com.game.exception.ApiException;
import com.game.market.TokenHelper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * REST controller for leaderboard endpoints.
 *
 * <p>All endpoints require a valid {@code Authorization: Bearer <token>} header,
 * validated inline via {@link TokenHelper} (same pattern as CellarController,
 * MarketController, GuildController). Security for {@code /api/ranking/**} is
 * already configured in SecurityConfig as permitAll + inline bearer check.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>GET  /api/ranking/wealth                        — live top-20 by wallet GEL</li>
 *   <li>GET  /api/ranking/vintner                       — live top-20 by best bottle quality</li>
 *   <li>GET  /api/ranking/guild                         — live top-20 guilds by member count</li>
 *   <li>GET  /api/ranking/me?board=&characterId=        — caller's rank on a board</li>
 *   <li>POST /api/ranking/snapshot  {board}             — persist + return snapshot rows</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/ranking")
public class RankingController {

    private final RankingService   rankingService;
    private final TokenHelper      tokenHelper;

    public RankingController(RankingService rankingService,
                             TokenHelper tokenHelper) {
        this.rankingService = rankingService;
        this.tokenHelper    = tokenHelper;
    }

    // ── GET /api/ranking/wealth ───────────────────────────────────────────────

    /**
     * Returns the live top-20 WEALTH leaderboard (characters by walletGel desc).
     *
     * @param request HTTP request (bearer token)
     * @return 200 {@code RankEntry[]}; 401 if token missing/invalid
     */
    @GetMapping("/wealth")
    public ResponseEntity<List<RankEntry>> wealth(HttpServletRequest request) {
        requireAuth(request);
        return ResponseEntity.ok(rankingService.wealthBoard());
    }

    // ── GET /api/ranking/vintner ──────────────────────────────────────────────

    /**
     * Returns the live top-20 VINTNER leaderboard (characters by best bottle quality desc).
     *
     * @param request HTTP request (bearer token)
     * @return 200 {@code RankEntry[]}; 401 if token missing/invalid
     */
    @GetMapping("/vintner")
    public ResponseEntity<List<RankEntry>> vintner(HttpServletRequest request) {
        requireAuth(request);
        return ResponseEntity.ok(rankingService.vintnerBoard());
    }

    // ── GET /api/ranking/guild ────────────────────────────────────────────────

    /**
     * Returns the live top-20 GUILD leaderboard (guilds by member count desc,
     * tie-broken by treasury desc). Returns an empty list if no guilds exist.
     *
     * @param request HTTP request (bearer token)
     * @return 200 {@code RankEntry[]}; 401 if token missing/invalid
     */
    @GetMapping("/guild")
    public ResponseEntity<List<RankEntry>> guild(HttpServletRequest request) {
        requireAuth(request);
        return ResponseEntity.ok(rankingService.guildBoard());
    }

    // ── GET /api/ranking/me ───────────────────────────────────────────────────

    /**
     * Returns the caller's rank + score on the given board.
     *
     * <p>Auth: token must be valid and the {@code characterId} must be owned by
     * the token's account. Returns 200 with {@code rankPos=0} (absent field) in
     * the response if the character is not currently on the board.
     *
     * @param board       query param: "wealth" or "vintner" (guild for guilds is
     *                    not per-character, but "guild" is accepted and returns
     *                    the guild's rank if the character is a guild founder/member)
     * @param characterId query param: the character to look up
     * @param request     HTTP request (bearer token)
     * @return 200 {@code RankEntry} or a map with rankPos=0; 401/404 on auth/ownership error
     */
    @GetMapping("/me")
    public ResponseEntity<?> me(
            @RequestParam String board,
            @RequestParam Long characterId,
            HttpServletRequest request) {

        // Auth + ownership check — character must belong to the token's account
        tokenHelper.requireOwnedCharacter(request, characterId);

        // Validate board param (only wealth|vintner supported for per-character lookup)
        String boardLower = board.toLowerCase();
        if (!boardLower.equals("wealth") && !boardLower.equals("vintner")) {
            throw ApiException.badRequest("board must be one of: wealth, vintner");
        }

        Optional<RankEntry> entry = rankingService.meRank(boardLower, characterId);
        if (entry.isPresent()) {
            return ResponseEntity.ok(entry.get());
        }
        // Not on the board — return rankPos=0 to indicate absence
        return ResponseEntity.ok(new RankEntry(0, characterId, "", 0.0));
    }

    // ── POST /api/ranking/snapshot ────────────────────────────────────────────

    /**
     * Computes the current state of the given board and persists each entry as a
     * {@link RankingSnapshot} row. Returns the persisted rows.
     *
     * <p>Request body: {@code { "board": "wealth" }} (case-insensitive).
     * Valid values: "wealth", "vintner", "guild".
     *
     * @param body    request body containing the board name
     * @param request HTTP request (bearer token)
     * @return 200 {@code RankingSnapshot[]}; 400 if board unknown; 401 if token invalid
     */
    @PostMapping("/snapshot")
    public ResponseEntity<List<RankingSnapshot>> snapshot(
            @RequestBody Map<String, String> body,
            HttpServletRequest request) {

        requireAuth(request);

        String board = body.get("board");
        if (board == null || board.isBlank()) {
            throw ApiException.badRequest("board is required");
        }
        String boardLower = board.toLowerCase();
        if (!boardLower.equals("wealth") && !boardLower.equals("vintner") && !boardLower.equals("guild")) {
            throw ApiException.badRequest("board must be one of: wealth, vintner, guild");
        }

        List<RankingSnapshot> rows = rankingService.snapshot(boardLower);
        return ResponseEntity.ok(rows);
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    /**
     * Validates the bearer token without checking character ownership.
     * Throws 401 if the token is missing or invalid.
     */
    private void requireAuth(HttpServletRequest request) {
        tokenHelper.requireAccountId(request);
    }
}
