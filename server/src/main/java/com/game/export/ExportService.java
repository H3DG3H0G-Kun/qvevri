package com.game.export;

import com.game.bonus.BonusService;
import com.game.bonus.BonusTypes;
import com.game.character.Character;
import com.game.character.CharacterRepository;
import com.game.character.CharacterService;
import com.game.exception.ApiException;
import com.game.market.CellarItem;
import com.game.market.CellarItemRepository;
import com.game.world.clock.WorldClockService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Core service for the EXPORT lane — selling wine to foreign markets.
 *
 * <p>Pricing (documented constant K = 0.5 GEL per quality point):
 * <pre>
 *   perBottleBase = item.quality × K
 *   gross         = perBottleBase × quantity × market.priceMultiplier
 *   tariff        = gross × market.tariffRate
 *   net           = gross − tariff   (credited to the seller's wallet)
 * </pre>
 */
@Service
@Transactional
public class ExportService {

    /** GEL per quality point — the base value of one litre of wine before market factors. */
    static final double K = 0.5;

    private final CellarItemRepository  cellarItemRepository;
    private final CharacterService      characterService;
    private final CharacterRepository   characterRepository;
    private final WorldClockService     clock;
    private final ExportRecordRepository exportRecordRepository;
    private final BonusService          bonusService;

    public ExportService(CellarItemRepository cellarItemRepository,
                         CharacterService characterService,
                         CharacterRepository characterRepository,
                         WorldClockService clock,
                         ExportRecordRepository exportRecordRepository,
                         BonusService bonusService) {
        this.cellarItemRepository   = cellarItemRepository;
        this.characterService       = characterService;
        this.characterRepository    = characterRepository;
        this.clock                  = clock;
        this.exportRecordRepository = exportRecordRepository;
        this.bonusService           = bonusService;
    }

    /** All foreign markets. */
    public List<ForeignMarket> markets() {
        return ForeignMarketCatalog.all();
    }

    /** That character's export history (most recent first). */
    public List<ExportRecord> history(long characterId) {
        return exportRecordRepository.findBySellerCharacterIdOrderByCreatedAtDesc(characterId);
    }

    /**
     * Sells {@code quantity} litres of a cellar item to a foreign market, crediting the
     * net (gross − tariff) to the seller's wallet and decrementing the cellar item.
     */
    public SellResponse sell(long characterId, String foreignMarketId, long cellarItemId, double quantity) {
        ForeignMarket market = ForeignMarketCatalog.find(foreignMarketId)
                .orElseThrow(() -> ApiException.notFound("Unknown foreign market: " + foreignMarketId));

        CellarItem item = cellarItemRepository.findByIdAndCharacterId(cellarItemId, characterId)
                .orElseThrow(() -> ApiException.notFound(
                        "CellarItem " + cellarItemId + " not found for character " + characterId));

        if (quantity <= 0) {
            throw ApiException.badRequest("quantity must be > 0");
        }
        if (quantity > item.getQuantity()) {
            throw ApiException.badRequest(
                    "quantity " + quantity + " exceeds owned " + item.getQuantity());
        }

        // INTEGRATION: a better seller commands a higher gross price. SELL_MARGIN is the
        // character's aggregated sell bonus (career + skills); 0.0 for a default character,
        // so the no-bonus outcome is unchanged.
        double sellMargin = bonusService.total(characterId, BonusTypes.SELL_MARGIN);

        double perBottleBase = item.getQuality() * K;
        double gross  = perBottleBase * quantity * market.priceMultiplier() * (1.0 + sellMargin);
        double tariff = gross * market.tariffRate();
        double net    = gross - tariff;

        // Credit the seller's wallet with the net proceeds.
        characterService.adjustWallet(characterId, net);

        // Decrement (or remove) the exported volume from the cellar item.
        double remaining = item.getQuantity() - quantity;
        if (remaining <= 0.0) {
            cellarItemRepository.delete(item);
        } else {
            item.setQuantity(remaining);
            cellarItemRepository.save(item);
        }

        ExportRecord record = exportRecordRepository.save(new ExportRecord(
                characterId, market.id(), cellarItemId, quantity,
                gross, tariff, net, clock.currentAbsoluteDay()));

        double wallet = characterRepository.findById(characterId)
                .map(Character::getWalletGel)
                .orElse(0.0);

        return new SellResponse(record, wallet);
    }
}
