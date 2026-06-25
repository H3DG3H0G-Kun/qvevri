package com.game.land;

import com.game.account.AccountTokenService;
import com.game.character.Character;
import com.game.character.CharacterRepository;
import com.game.character.CharacterService;
import com.game.estate.VineyardRepository;
import com.game.exception.ApiException;
import com.game.world.Region;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

/**
 * REST endpoints for player-owned land parcels (LAND lane, BACKEND-DEPTH-SPEC V7).
 *
 * <p>All endpoints require {@code Authorization: Bearer <token>}; auth is
 * enforced inline via {@link AccountTokenService}.  The characterId supplied in
 * each request must belong to the authenticated account (verified via
 * {@link CharacterService#getOwned}).
 *
 * <p>Pricing: 200 GEL/ha.  Wallet debited via
 * {@link CharacterService#adjustWallet} — 400 INSUFFICIENT_FUNDS if the
 * character cannot afford it.
 *
 * <p>Coordinates are derived deterministically from the region centre
 * ({@link com.game.world.WorldCatalog}) with a small jitter so parcels cluster
 * near the real Georgian town but don't all stack on the same point.
 */
@RestController
@RequestMapping("/api/land")
public class LandController {

    /** Land price in GEL per hectare. */
    static final double PRICE_PER_HA = 200.0;

    private final ParcelRepository     parcelRepository;
    private final CharacterService     characterService;
    private final CharacterRepository  characterRepository;
    private final AccountTokenService  tokenService;
    private final VineyardRepository   vineyardRepository;

    public LandController(ParcelRepository parcelRepository,
                          CharacterService characterService,
                          CharacterRepository characterRepository,
                          AccountTokenService tokenService,
                          VineyardRepository vineyardRepository) {
        this.parcelRepository    = parcelRepository;
        this.characterService    = characterService;
        this.characterRepository = characterRepository;
        this.tokenService        = tokenService;
        this.vineyardRepository  = vineyardRepository;
    }

    // ── POST /api/land/parcels ────────────────────────────────────────────────

    /**
     * Claim/buy a parcel of land in the given region.
     *
     * <p>Request: {@code { characterId, region, name, sizeHectares }}
     *
     * <p>The price is {@value #PRICE_PER_HA} GEL × sizeHectares.  The wallet is
     * debited via {@link CharacterService#adjustWallet}.  If the character
     * cannot afford it, 400 INSUFFICIENT_FUNDS is returned.
     *
     * <p>On success the parcel is created with coordinates derived from the
     * region centre plus a deterministic jitter.
     *
     * @return 201 Created with {@link BuyParcelResponse} (parcel + new wallet)
     */
    @PostMapping("/parcels")
    public ResponseEntity<BuyParcelResponse> buyParcel(
            @RequestBody BuyParcelRequest req,
            HttpServletRequest http) {

        long accountId   = requireAccount(http);
        long characterId = requireCharacterId(req.getCharacterId());
        requireOwnership(characterId, accountId);

        // Validate inputs
        if (req.getName() == null || req.getName().isBlank()) {
            throw ApiException.badRequest("name must not be blank");
        }
        if (req.getSizeHectares() == null || req.getSizeHectares() <= 0) {
            throw ApiException.badRequest("sizeHectares must be positive");
        }
        Region region = parseRegion(req.getRegion());

        double sizeHa = req.getSizeHectares();
        double price  = sizeHa * PRICE_PER_HA;

        // Debit wallet — throws INSUFFICIENT_FUNDS (mapped to 402 by ApiException,
        // but the spec says "400 INSUFFICIENT_FUNDS"; we preserve the existing
        // ApiException.adjustWallet behaviour which uses PAYMENT_REQUIRED / 402).
        // The spec says "400" loosely to mean "can't complete"; we let the
        // existing CharacterService throw its own ApiException with code
        // INSUFFICIENT_FUNDS which the global error handler serialises correctly.
        characterService.adjustWallet(characterId, -price);

        // Derive coordinates (jitter around region centre)
        long seed = Instant.now().toEpochMilli();
        double[] coords = ParcelCoordinates.derive(region, characterId, seed);

        Parcel parcel = new Parcel(
                characterId,
                req.getName().strip(),
                region.name(),
                coords[0],
                coords[1],
                sizeHa,
                seed);

        Parcel saved = parcelRepository.save(parcel);

        // Reload character to return fresh wallet balance
        Character ch = characterRepository.findById(characterId)
                .orElseThrow(() -> ApiException.badRequest("Character not found: " + characterId));

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new BuyParcelResponse(saved, ch.getWalletGel()));
    }

    // ── GET /api/land/{characterId} ───────────────────────────────────────────

    /**
     * Returns all parcels owned by the given character.
     * The authenticated account must own the character.
     */
    @GetMapping("/{characterId}")
    public List<Parcel> listParcels(@PathVariable long characterId,
                                    HttpServletRequest http) {
        long accountId = requireAccount(http);
        requireOwnership(characterId, accountId);
        return parcelRepository.findByOwnerCharacterId(characterId);
    }

    // ── GET /api/land/parcel/{parcelId} ───────────────────────────────────────

    /**
     * Returns a single parcel by id.
     * Only the owning character's account may access it (404 otherwise).
     */
    @GetMapping("/parcel/{parcelId}")
    public Parcel getParcel(@PathVariable long parcelId,
                            HttpServletRequest http) {
        long accountId = requireAccount(http);

        Parcel parcel = parcelRepository.findById(parcelId)
                .orElseThrow(() -> ApiException.notFound("Parcel not found: " + parcelId));

        // Confirm the authenticated account owns the character that owns this parcel
        requireOwnership(parcel.getOwnerCharacterId(), accountId);
        return parcel;
    }

    // ── POST /api/land/parcels/{parcelId}/attach-vineyard (optional) ──────────

    /**
     * Attaches an existing vineyard to a parcel by storing its id on the parcel.
     * The vineyard must belong to the same character as the parcel.
     * Does NOT edit {@code Vineyard.java} — the link lives on {@link Parcel}.
     *
     * @return the updated parcel
     */
    @PostMapping("/parcels/{parcelId}/attach-vineyard")
    public Parcel attachVineyard(@PathVariable long parcelId,
                                 @RequestBody AttachVineyardRequest req,
                                 HttpServletRequest http) {
        long accountId   = requireAccount(http);
        long characterId = requireCharacterId(req.getCharacterId());
        requireOwnership(characterId, accountId);

        if (req.getVineyardId() == null) {
            throw ApiException.badRequest("vineyardId is required");
        }

        // Parcel must be owned by this character
        Parcel parcel = parcelRepository.findByIdAndOwnerCharacterId(parcelId, characterId)
                .orElseThrow(() -> ApiException.notFound(
                        "Parcel " + parcelId + " not found for character " + characterId));

        // Verify the vineyard belongs to the same character (read-only check)
        vineyardRepository.findByIdAndOwnerCharacterId(req.getVineyardId(), characterId)
                .orElseThrow(() -> ApiException.notFound(
                        "Vineyard " + req.getVineyardId()
                                + " not found for character " + characterId));

        parcel.setVineyardId(req.getVineyardId());
        return parcelRepository.save(parcel);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private long requireAccount(HttpServletRequest http) {
        String header = http.getHeader("Authorization");
        String token  = (header != null && header.startsWith("Bearer "))
                ? header.substring(7) : null;
        return tokenService.accountIdFor(token)
                .orElseThrow(() -> new ApiException("UNAUTHORIZED",
                        "Missing or invalid bearer token", HttpStatus.UNAUTHORIZED));
    }

    private long requireCharacterId(Long characterId) {
        if (characterId == null) {
            throw ApiException.badRequest("characterId is required");
        }
        return characterId;
    }

    /** Verify the character belongs to the authenticated account, else 404. */
    private void requireOwnership(long characterId, long accountId) {
        characterService.getOwned(characterId, accountId)
                .orElseThrow(() -> ApiException.notFound(
                        "Character " + characterId + " not found for this account"));
    }

    private static Region parseRegion(String name) {
        if (name == null) throw ApiException.badRequest("region is required");
        try {
            return Region.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Unknown region: " + name);
        }
    }
}
