package com.game.festival;

import com.game.character.CharacterService;
import com.game.exception.ApiException;
import com.game.world.clock.WorldClockService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

/**
 * Business logic for the Festival lane.
 *
 * <p>All time effects resolve lazily off {@link WorldClockService} — no
 * scheduler. Festival windows are checked against the current
 * {@code currentDayOfYear()} at request time.
 *
 * <p>Idempotent participation: per (characterId, festivalId, seasonYear).
 * First call grants {@code rewardGel} via {@link CharacterService#adjustWallet}
 * and persists the row. A repeat in the same world year throws
 * ALREADY_PARTICIPATED (400). Participating when the festival is not active
 * throws NOT_ACTIVE (400).
 */
@Service
@Transactional
public class FestivalService {

    private final FestivalParticipationRepository participationRepository;
    private final CharacterService                characterService;
    private final WorldClockService               worldClockService;

    public FestivalService(FestivalParticipationRepository participationRepository,
                           CharacterService characterService,
                           WorldClockService worldClockService) {
        this.participationRepository = participationRepository;
        this.characterService        = characterService;
        this.worldClockService       = worldClockService;
    }

    // ── Calendar (read-only) ──────────────────────────────────────────────────

    /**
     * Returns all festival definitions from the static calendar.
     *
     * @return all {@link FestivalDefinition}s in catalog order
     */
    @Transactional(readOnly = true)
    public Collection<FestivalDefinition> getCalendar() {
        return FestivalCalendar.all();
    }

    /**
     * Returns festivals whose window contains the current world day-of-year.
     * Resolved lazily from {@link WorldClockService#currentDayOfYear()}.
     *
     * @return list of active {@link FestivalDefinition}s (may be empty)
     */
    @Transactional(readOnly = true)
    public List<FestivalDefinition> getActive() {
        int dayOfYear = worldClockService.currentDayOfYear();
        return FestivalCalendar.active(dayOfYear);
    }

    // ── Participation ─────────────────────────────────────────────────────────

    /**
     * Records a character's participation in a festival.
     *
     * <p>Business rules:
     * <ol>
     *   <li>Unknown festivalId → 404 NOT_FOUND.</li>
     *   <li>Festival not currently active (current day outside window) → 400 NOT_ACTIVE.</li>
     *   <li>Already participated this season year → 400 ALREADY_PARTICIPATED.</li>
     *   <li>First valid call: grant {@code rewardGel} via {@link CharacterService#adjustWallet}
     *       and persist a {@link FestivalParticipation} row.</li>
     * </ol>
     *
     * @param characterId the participating character (ownership verified by controller)
     * @param festivalId  stable catalog festival id
     * @return the newly created participation record
     * @throws ApiException 404 if festivalId is unknown
     * @throws ApiException 400 NOT_ACTIVE if the festival window does not include today
     * @throws ApiException 400 ALREADY_PARTICIPATED if the character already claimed this year
     */
    public FestivalParticipation participate(Long characterId, String festivalId) {
        // 1. Resolve festival definition (404 if unknown)
        FestivalDefinition def = FestivalCalendar.find(festivalId);
        if (def == null) {
            throw ApiException.notFound("Unknown festivalId: '" + festivalId + "'");
        }

        // 2. Check window against current day-of-year (lazy clock read)
        int dayOfYear = worldClockService.currentDayOfYear();
        if (dayOfYear < def.getStartDayOfYear() || dayOfYear > def.getEndDayOfYear()) {
            throw new ApiException(
                    "NOT_ACTIVE",
                    "Festival '" + festivalId + "' is not currently active "
                    + "(window: days " + def.getStartDayOfYear() + "–" + def.getEndDayOfYear()
                    + ", current day: " + dayOfYear + ")",
                    HttpStatus.BAD_REQUEST);
        }

        // 3. Idempotency guard: one participation per (character, festival, year)
        int seasonYear = worldClockService.currentYear();
        participationRepository
                .findByCharacterIdAndFestivalIdAndSeasonYear(characterId, festivalId, seasonYear)
                .ifPresent(existing -> {
                    throw new ApiException(
                            "ALREADY_PARTICIPATED",
                            "Character " + characterId
                            + " has already participated in festival '" + festivalId
                            + "' in year " + seasonYear,
                            HttpStatus.BAD_REQUEST);
                });

        // 4. Grant reward and record participation (atomic within the transaction)
        characterService.adjustWallet(characterId, def.getRewardGel());

        FestivalParticipation participation =
                new FestivalParticipation(characterId, festivalId, seasonYear);
        return participationRepository.save(participation);
    }
}
