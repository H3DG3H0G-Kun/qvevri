package com.game.contest;

import com.game.character.CharacterService;
import com.game.exception.ApiException;
import com.game.market.CellarItemRepository;
import com.game.world.clock.WorldClockService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

/**
 * Business logic for the Lane Contest timed wine competition system.
 *
 * <h3>Entry model</h3>
 * <ul>
 *   <li>Character must own the CellarItem ({@code findByIdAndCharacterId}).</li>
 *   <li>One entry per character per contest; second entry → 400.</li>
 *   <li>Cannot enter after endDay (currentDay &ge; endDay) → 400.</li>
 *   <li>{@code qualityScore} = snapshot of {@code CellarItem.quality} at entry time;
 *       post-entry quality changes do not affect results.</li>
 * </ul>
 *
 * <h3>Judging model</h3>
 * <ul>
 *   <li>Triggered when {@code currentDay &ge; endDay} and contest is OPEN.</li>
 *   <li>Entries sorted by {@code qualityScore} descending; tie-break by entry
 *       {@code id} ascending (insertion order → deterministic).</li>
 *   <li>Placements assigned 1..n in sorted order.</li>
 *   <li>Winner (placement 1) receives {@code prizeGel} via
 *       {@link CharacterService#adjustWallet(Long, double)} — winner-takes-all, v1.</li>
 *   <li>Idempotent: already-JUDGED contests are returned unchanged.</li>
 * </ul>
 *
 * <h3>Settlement laziness</h3>
 * {@link #listOpen()} lazily auto-judges any OPEN contest whose endDay has
 * passed before returning the remaining OPEN contests (mirrors the auction lane pattern).
 * {@link #judge(Long)} is the explicit endpoint; both paths call {@link #judgeInternal}.
 *
 * <h3>v1 prize note</h3>
 * Prize GEL is NPC-funded — the contest creator is never debited. A later pass
 * will introduce funded prizes (creator escrows the prize at creation time).
 */
@Service
@Transactional
public class ContestService {

    static final String STATUS_OPEN   = "OPEN";
    static final String STATUS_JUDGED = "JUDGED";

    private final ContestRepository      contestRepo;
    private final ContestEntryRepository entryRepo;
    private final CellarItemRepository   cellarItemRepo;
    private final CharacterService       characterService;
    private final WorldClockService      clockService;

    public ContestService(ContestRepository contestRepo,
                          ContestEntryRepository entryRepo,
                          CellarItemRepository cellarItemRepo,
                          CharacterService characterService,
                          WorldClockService clockService) {
        this.contestRepo      = contestRepo;
        this.entryRepo        = entryRepo;
        this.cellarItemRepo   = cellarItemRepo;
        this.characterService = characterService;
        this.clockService     = clockService;
    }

    // ── Create ────────────────────────────────────────────────────────────────

    /**
     * Creates an OPEN contest.
     *
     * <p>{@code endDay = currentAbsoluteDay + durationDays}.
     * v1: any authenticated character may create; creator is NOT debited.
     *
     * @param name         display name of the contest
     * @param description  theme / rule description
     * @param durationDays number of sim-days the contest stays open (must be &ge; 1)
     * @param prizeGel     prize for placement 1 in GEL (must be &gt; 0)
     * @return the newly created OPEN {@link Contest}
     */
    public Contest create(String name, String description, int durationDays, double prizeGel) {
        if (durationDays < 1) {
            throw ApiException.badRequest("durationDays must be >= 1");
        }
        if (prizeGel <= 0) {
            throw ApiException.badRequest("prizeGel must be > 0");
        }
        long endDay = (long) clockService.currentAbsoluteDay() + durationDays;
        Contest contest = new Contest(name, description, endDay, prizeGel);
        return contestRepo.save(contest);
    }

    // ── List open (with lazy auto-judge) ─────────────────────────────────────

    /**
     * Returns all OPEN contests, first lazily judging any whose {@code endDay}
     * has passed (currentDay &ge; endDay).
     *
     * @return list of contests that are still OPEN after the judging pass
     */
    @Transactional
    public List<Contest> listOpen() {
        int currentDay = clockService.currentAbsoluteDay();
        List<Contest> allOpen = contestRepo.findByContestStatus(STATUS_OPEN);
        for (Contest c : allOpen) {
            if (currentDay >= c.getEndDay()) {
                judgeInternal(c);
            }
        }
        return contestRepo.findByContestStatus(STATUS_OPEN);
    }

    // ── Enter ─────────────────────────────────────────────────────────────────

    /**
     * Enters a character's cellar item into an OPEN contest.
     *
     * <p>Guards (400 on failure unless noted):
     * <ul>
     *   <li>Contest must exist (404 if not).</li>
     *   <li>Contest must be OPEN.</li>
     *   <li>currentDay must be &lt; endDay (contest has not expired).</li>
     *   <li>Character must own the CellarItem (404 if not).</li>
     *   <li>Character must not have already entered this contest.</li>
     * </ul>
     *
     * @param contestId    the contest to enter
     * @param characterId  the entering character (already ownership-verified by controller)
     * @param cellarItemId the cellar item to submit
     * @return the newly created {@link ContestEntry}
     */
    @Transactional
    public ContestEntry enter(Long contestId, Long characterId, Long cellarItemId) {
        Contest contest = requireContest(contestId);

        if (!STATUS_OPEN.equals(contest.getContestStatus())) {
            throw ApiException.badRequest(
                    "Contest " + contestId + " is not OPEN (status: "
                            + contest.getContestStatus() + ")");
        }

        int currentDay = clockService.currentAbsoluteDay();
        if (currentDay >= contest.getEndDay()) {
            throw ApiException.badRequest(
                    "Contest " + contestId + " has already expired (endDay="
                            + contest.getEndDay() + ", currentDay=" + currentDay + ")");
        }

        // Ownership check — 404 if not owned
        var item = cellarItemRepo.findByIdAndCharacterId(cellarItemId, characterId)
                .orElseThrow(() -> ApiException.notFound(
                        "CellarItem " + cellarItemId
                                + " not found or not owned by character " + characterId));

        // One-entry-per-character guard
        entryRepo.findByContestIdAndCharacterId(contestId, characterId).ifPresent(existing -> {
            throw ApiException.badRequest(
                    "Character " + characterId
                            + " has already entered contest " + contestId);
        });

        // Snapshot quality at entry time
        double qualityScore = item.getQuality();
        ContestEntry entry = new ContestEntry(contestId, characterId, cellarItemId, qualityScore);
        return entryRepo.save(entry);
    }

    // ── Judge (explicit endpoint) ─────────────────────────────────────────────

    /**
     * Explicit judge: resolves the contest when {@code currentDay &ge; endDay}.
     * Idempotent — already-JUDGED contests are returned unchanged.
     *
     * @param contestId the contest to judge
     * @return the (now JUDGED) {@link Contest}
     */
    @Transactional
    public Contest judge(Long contestId) {
        Contest contest = requireContest(contestId);

        // Idempotent: already judged → no-op
        if (STATUS_JUDGED.equals(contest.getContestStatus())) {
            return contest;
        }

        if (!STATUS_OPEN.equals(contest.getContestStatus())) {
            throw ApiException.badRequest(
                    "Contest " + contestId + " cannot be judged (status: "
                            + contest.getContestStatus() + ")");
        }

        int currentDay = clockService.currentAbsoluteDay();
        if (currentDay < contest.getEndDay()) {
            throw ApiException.badRequest(
                    "Contest " + contestId + " has not expired yet (endDay="
                            + contest.getEndDay() + ", currentDay=" + currentDay + ")");
        }

        return judgeInternal(contest);
    }

    // ── Results ───────────────────────────────────────────────────────────────

    /**
     * Returns all entries for a contest (with placements set if JUDGED).
     *
     * @param contestId the contest id
     * @return list of entries
     */
    @Transactional(readOnly = true)
    public List<ContestEntry> results(Long contestId) {
        requireContest(contestId);
        return entryRepo.findByContestId(contestId);
    }

    // ── Core judging logic ────────────────────────────────────────────────────

    /**
     * Performs the actual judging. Called both lazily (from {@link #listOpen()})
     * and explicitly (from {@link #judge(Long)}).
     *
     * <p>Algorithm:
     * <ol>
     *   <li>Load all entries for the contest.</li>
     *   <li>Sort by {@code qualityScore} descending; tie-break by entry {@code id}
     *       ascending (deterministic — lower id = earlier entry wins ties).</li>
     *   <li>Assign placement 1..n in sorted order.</li>
     *   <li>If at least one entry exists, award {@code prizeGel} to placement-1
     *       winner via {@link CharacterService#adjustWallet(Long, double)}
     *       (winner-takes-all, v1).</li>
     *   <li>Mark contest as JUDGED.</li>
     * </ol>
     *
     * <p>Idempotent guard: already-JUDGED → returns immediately.
     */
    @Transactional
    Contest judgeInternal(Contest contest) {
        if (STATUS_JUDGED.equals(contest.getContestStatus())) {
            return contest; // idempotent guard
        }

        List<ContestEntry> entries = entryRepo.findByContestId(contest.getId());

        // Sort: qualityScore desc, id asc (tie-break deterministic by insertion order)
        entries.sort(Comparator
                .comparingDouble(ContestEntry::getQualityScore).reversed()
                .thenComparingLong(ContestEntry::getId));

        // Assign placements and award prize
        for (int i = 0; i < entries.size(); i++) {
            ContestEntry entry = entries.get(i);
            entry.setPlacement(i + 1);
            entryRepo.save(entry);

            // Winner-takes-all: award prizeGel to placement 1
            if (i == 0) {
                characterService.adjustWallet(entry.getCharacterId(), contest.getPrizeGel());
            }
        }

        contest.setContestStatus(STATUS_JUDGED);
        return contestRepo.save(contest);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private Contest requireContest(Long contestId) {
        return contestRepo.findById(contestId)
                .orElseThrow(() -> ApiException.notFound(
                        "Contest " + contestId + " not found"));
    }
}
