package com.game.travel;

import com.game.bonus.BonusService;
import com.game.bonus.BonusTypes;
import com.game.character.Character;
import com.game.exception.ApiException;
import com.game.logistics.GeoUtil;
import com.game.world.Region;
import com.game.world.clock.WorldClockService;
import com.game.character.CharacterService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Arrays;

/**
 * Business logic for LANE TRAVEL — character movement between Georgian wine regions.
 *
 * <h2>Travel-time model</h2>
 * Delegates to {@link GeoUtil#travelDays(Region, Region)}: max(1, ceil(haversineKm / 40)).
 * At 40 km/day this yields 1–10 sim-days for the seven regions.
 *
 * <h2>GEL cost</h2>
 * A flat <b>5 GEL</b> per departure (modest, always within a starter character's wallet).
 * Deducted via {@link CharacterService#adjustWallet} before the TRAVELLING state is set;
 * returns 400 INSUFFICIENT_FUNDS if the wallet is too low.
 *
 * <h2>Lazy arrival</h2>
 * {@link #getLocation} checks whether a TRAVELLING character has reached their
 * destination ({@code currentAbsoluteDay >= arriveDay}) and flips the record to
 * SETTLED before returning it. No scheduler required.
 */
@Service
@Transactional
public class TravelService {

    /** Flat GEL cost per departure trip (kept modest for early-game characters). */
    static final double TRAVEL_COST_GEL = 5.0;

    private final CharacterLocationRepository locationRepo;
    private final WorldClockService           clockService;
    private final CharacterService            characterService;
    private final BonusService                bonusService;

    public TravelService(CharacterLocationRepository locationRepo,
                         WorldClockService clockService,
                         CharacterService characterService,
                         BonusService bonusService) {
        this.locationRepo    = locationRepo;
        this.clockService    = clockService;
        this.characterService = characterService;
        this.bonusService    = bonusService;
    }

    // ── GET /api/travel/{characterId} ────────────────────────────────────────

    /**
     * Returns a character's location, lazy-creating it at their homeRegion (SETTLED)
     * on first access. Also lazily resolves arrival: if TRAVELLING and
     * {@code currentAbsoluteDay >= arriveDay}, flips to SETTLED at destRegion.
     *
     * @param character the verified, owned character
     * @return persisted {@link CharacterLocation}
     */
    public CharacterLocation getLocation(Character character) {
        CharacterLocation loc = locationRepo.findByCharacterId(character.getId())
                .orElseGet(() -> createSettled(character));

        // Lazy arrival check
        if ("TRAVELLING".equals(loc.getTravelStatus())) {
            int currentDay = clockService.currentAbsoluteDay();
            if (currentDay >= loc.getArriveDay()) {
                loc.setCurrentRegion(loc.getDestRegion());
                loc.setTravelStatus("SETTLED");
                loc.setDestRegion(null);
                locationRepo.save(loc);
            }
        }
        return loc;
    }

    // ── POST /api/travel/{characterId}/depart ─────────────────────────────

    /**
     * Initiates travel from the character's current region to {@code toRegionName}.
     *
     * <p>Rules (all violations throw 400 BAD_REQUEST):
     * <ul>
     *   <li>Character must be SETTLED.</li>
     *   <li>{@code toRegionName} must be a valid {@link Region} enum value.</li>
     *   <li>{@code toRegionName} must differ from the current region.</li>
     *   <li>Character wallet must cover {@value #TRAVEL_COST_GEL} GEL.</li>
     * </ul>
     *
     * @param character    the verified, owned character
     * @param toRegionName destination region enum name (e.g. "KARTLI")
     * @return updated {@link CharacterLocation} in TRAVELLING state
     */
    public CharacterLocation depart(Character character, String toRegionName) {
        // Load (or lazy-create) the location — also performs lazy arrival
        CharacterLocation loc = getLocation(character);

        // Must be SETTLED
        if ("TRAVELLING".equals(loc.getTravelStatus())) {
            throw new ApiException("ALREADY_TRAVELLING",
                    "Character is already travelling to " + loc.getDestRegion(),
                    org.springframework.http.HttpStatus.BAD_REQUEST);
        }

        // Validate destination region
        Region toRegion = parseRegion(toRegionName);

        // Must differ from current region
        Region fromRegion = parseRegion(loc.getCurrentRegion());
        if (fromRegion == toRegion) {
            throw ApiException.badRequest(
                    "Destination region must differ from the current region: " + toRegionName);
        }

        // Compute travel days and cost
        int travelDays = GeoUtil.travelDays(fromRegion, toRegion);
        int currentDay = clockService.currentAbsoluteDay();
        long arriveDay  = (long) currentDay + travelDays;

        // INTEGRATION: a Hauler (or anyone with SHIPPING_DISCOUNT) travels cheaper.
        // 0.0 for a default character, so the base 5 GEL cost is unchanged.
        double discount = bonusService.total(character.getId(), BonusTypes.SHIPPING_DISCOUNT);
        double cost = TRAVEL_COST_GEL * (1.0 - discount);

        // Deduct travel cost (throws INSUFFICIENT_FUNDS if wallet < cost)
        characterService.adjustWallet(character.getId(), -cost);

        // Update the location record
        loc.setTravelStatus("TRAVELLING");
        loc.setDestRegion(toRegion.name());
        loc.setDepartDay(currentDay);
        loc.setArriveDay(arriveDay);
        return locationRepo.save(loc);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /** Lazy-create a SETTLED location row at the character's homeRegion. */
    private CharacterLocation createSettled(Character character) {
        CharacterLocation loc = new CharacterLocation(
                character.getId(),
                character.getHomeRegion().name(),
                Instant.now().toEpochMilli());
        return locationRepo.save(loc);
    }

    /**
     * Parses a region name string into a {@link Region} enum value.
     *
     * @throws ApiException 400 if the name is not a valid Region
     */
    private static Region parseRegion(String name) {
        try {
            return Region.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest(
                    "Unknown region: '" + name + "'. Valid regions: "
                            + Arrays.toString(Region.values()));
        }
    }
}
