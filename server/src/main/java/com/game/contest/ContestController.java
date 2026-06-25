package com.game.contest;

import com.game.market.TokenHelper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for the Lane Contest wine competition system.
 *
 * <p>All endpoints require a valid {@code Authorization: Bearer <token>} header;
 * character ownership is verified via {@link TokenHelper}. Security is handled
 * inline — {@code /api/contest/**} is already listed in SecurityConfig's
 * permitAll matcher per the CONTEST-ACHIEVEMENT-CHAT-SPEC.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>POST /api/contest/create          — any authed character creates an OPEN contest</li>
 *   <li>GET  /api/contest/open            — all OPEN contests (lazy judge first)</li>
 *   <li>POST /api/contest/{id}/enter      — enter a cellar item; snapshots quality</li>
 *   <li>POST /api/contest/{id}/judge      — explicit judge after endDay</li>
 *   <li>GET  /api/contest/{id}/results    — entries with placements</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/contest")
public class ContestController {

    private final TokenHelper     tokenHelper;
    private final ContestService  contestService;

    public ContestController(TokenHelper tokenHelper,
                             ContestService contestService) {
        this.tokenHelper    = tokenHelper;
        this.contestService = contestService;
    }

    // ── POST /api/contest/create ──────────────────────────────────────────────

    /**
     * Creates an OPEN contest.
     *
     * <p>Any authenticated account may call this; no specific character is required
     * for creation — only a valid bearer token is checked. prizeGel is NPC-funded
     * (creator is not debited — v1 design note).
     *
     * <p>Request body: {@code {name, description, durationDays, prizeGel}}
     *
     * @return 200 with the OPEN {@link Contest}
     */
    @PostMapping("/create")
    public ResponseEntity<Contest> create(
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {

        // Auth — any valid bearer token suffices; no character ownership required
        tokenHelper.requireAccountId(request);

        String name        = (String)  body.get("name");
        String description = (String)  body.get("description");
        int durationDays   = ((Number) body.get("durationDays")).intValue();
        double prizeGel    = ((Number) body.get("prizeGel")).doubleValue();

        Contest contest = contestService.create(name, description, durationDays, prizeGel);
        return ResponseEntity.ok(contest);
    }

    // ── GET /api/contest/open ─────────────────────────────────────────────────

    /**
     * Returns all OPEN contests, lazily judging any past their endDay first.
     * Any authenticated user may browse.
     *
     * @return 200 with the list of currently OPEN {@link Contest} records
     */
    @GetMapping("/open")
    public ResponseEntity<List<Contest>> listOpen(HttpServletRequest request) {
        tokenHelper.requireAccountId(request);
        return ResponseEntity.ok(contestService.listOpen());
    }

    // ── POST /api/contest/{id}/enter ──────────────────────────────────────────

    /**
     * Enters a character's cellar item into an OPEN contest.
     *
     * <p>Request body: {@code {characterId, cellarItemId}}
     *
     * <p>Guards enforced by {@link ContestService#enter}:
     * contest OPEN, not past endDay, character owns the item, one entry per character.
     *
     * @return 200 with the {@link ContestEntry}; 400 on validation failure; 404 if item not owned
     */
    @PostMapping("/{id}/enter")
    public ResponseEntity<ContestEntry> enter(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {

        Long characterId  = ((Number) body.get("characterId")).longValue();
        Long cellarItemId = ((Number) body.get("cellarItemId")).longValue();

        // Verify the bearer token owns this character
        tokenHelper.requireOwnedCharacter(request, characterId);

        ContestEntry entry = contestService.enter(id, characterId, cellarItemId);
        return ResponseEntity.ok(entry);
    }

    // ── POST /api/contest/{id}/judge ──────────────────────────────────────────

    /**
     * Explicit judge endpoint. Idempotent — already-JUDGED contests return
     * the current state without error.
     *
     * <p>Request body: {@code {}} (any authed account may trigger judging).
     *
     * @return 200 with the JUDGED {@link Contest}; 400 if contest not yet expired
     */
    @PostMapping("/{id}/judge")
    public ResponseEntity<Contest> judge(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> body,
            HttpServletRequest request) {

        tokenHelper.requireAccountId(request);
        Contest contest = contestService.judge(id);
        return ResponseEntity.ok(contest);
    }

    // ── GET /api/contest/{id}/results ─────────────────────────────────────────

    /**
     * Returns all entries for the contest, ordered by database insertion.
     * After judging, each entry carries its assigned {@code placement}.
     *
     * @return 200 with the list of {@link ContestEntry} records
     */
    @GetMapping("/{id}/results")
    public ResponseEntity<List<ContestEntry>> results(
            @PathVariable Long id,
            HttpServletRequest request) {

        tokenHelper.requireAccountId(request);
        return ResponseEntity.ok(contestService.results(id));
    }
}
