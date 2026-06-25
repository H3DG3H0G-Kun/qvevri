package com.game.character;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.game.exception.ApiException;
import com.game.world.CareerType;
import com.game.world.Region;

/**
 * Business logic for character lifecycle.
 *
 * <p><b>Public API (consumed by MA controllers and by MB market lane):</b>
 * <ul>
 *   <li>{@link #create(Long, String, CareerType, Region)}</li>
 *   <li>{@link #forAccount(Long)}</li>
 *   <li>{@link #getOwned(Long, Long)}</li>
 *   <li>{@link #adjustWallet(Long, double)}</li>
 *   <li>{@link #save(Character)}</li>
 * </ul>
 */
@Service
@Transactional
public class CharacterService {

    private final CharacterRepository characterRepository;

    public CharacterService(CharacterRepository characterRepository) {
        this.characterRepository = characterRepository;
    }

    /**
     * Creates and persists a new character for the given account.
     *
     * @param accountId  owning account id
     * @param name       character name (must not be blank)
     * @param careerType chosen career
     * @param homeRegion chosen home region
     * @return the saved Character with id populated
     */
    public Character create(Long accountId, String name, CareerType careerType, Region homeRegion) {
        if (name == null || name.isBlank()) {
            throw ApiException.badRequest("Character name must not be blank");
        }
        Character character = new Character(accountId, name.strip(), careerType,
                homeRegion, Instant.now().toEpochMilli());
        return characterRepository.save(character);
    }

    /**
     * Returns all characters belonging to the given account.
     *
     * @param accountId the owning account
     * @return list of characters (may be empty)
     */
    @Transactional(readOnly = true)
    public List<Character> forAccount(Long accountId) {
        return characterRepository.findAllByAccountId(accountId);
    }

    /**
     * Returns a character by id only if it is owned by the given account.
     *
     * @param characterId the character id to look up
     * @param accountId   expected owning account
     * @return the character, or Optional.empty() if not found or not owned
     */
    @Transactional(readOnly = true)
    public Optional<Character> getOwned(Long characterId, Long accountId) {
        return characterRepository.findByIdAndAccountId(characterId, accountId);
    }

    /**
     * Adjusts the character's wallet by {@code delta} GEL.
     * Throws if a negative delta would reduce the wallet below zero.
     *
     * @param characterId target character
     * @param delta       amount to add (positive) or subtract (negative)
     * @throws ApiException INSUFFICIENT_FUNDS if delta is negative and wallet would go below 0
     * @throws ApiException BAD_REQUEST if the character does not exist
     */
    public void adjustWallet(Long characterId, double delta) {
        Character c = characterRepository.findById(characterId)
                .orElseThrow(() -> ApiException.badRequest("Character not found: " + characterId));
        double newBalance = c.getWalletGel() + delta;
        if (newBalance < 0.0) {
            throw new ApiException("INSUFFICIENT_FUNDS",
                    "Insufficient funds: wallet " + c.getWalletGel()
                            + " GEL, need " + (-delta) + " GEL",
                    HttpStatus.PAYMENT_REQUIRED);
        }
        c.setWalletGel(newBalance);
        characterRepository.save(c);
    }

    /**
     * Saves (or merges) a character entity.
     * Used by MB to persist changes to character state without bypassing service logic.
     *
     * @param character the character to save
     * @return the saved instance
     */
    public Character save(Character character) {
        return characterRepository.save(character);
    }
}
