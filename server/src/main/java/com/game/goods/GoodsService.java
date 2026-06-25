package com.game.goods;

import com.game.exception.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Core service for the goods/inventory system.
 *
 * <p>Exposes {@link #grant} as the primary write path consumed by:
 * <ul>
 *   <li>{@link ShopController} (NPC buy flow)</li>
 *   <li>Lane P (Profession) — starter kits and crafted outputs</li>
 * </ul>
 *
 * <p>All public methods are {@code @Transactional}.
 */
@Service
@Transactional
public class GoodsService {

    private final OwnedGoodRepository ownedGoodRepository;

    public GoodsService(OwnedGoodRepository ownedGoodRepository) {
        this.ownedGoodRepository = ownedGoodRepository;
    }

    /**
     * Grants {@code qty} units of {@code goodTypeId} to {@code characterId}.
     *
     * <p>If the character already owns an {@link OwnedGood} row for this
     * goodTypeId the quantity is incremented (stacked). Otherwise a new row
     * is created.
     *
     * <p>This is the <strong>canonical entry point for Lane P</strong>; the
     * Profession lane must call this method (do not touch OwnedGoodRepository
     * directly from that package).
     *
     * @param characterId the character receiving the good
     * @param goodTypeId  stable catalog id (e.g. "qvevri_500l")
     * @param qty         number of units to add; must be &gt; 0
     * @return the persisted (and potentially updated) {@link OwnedGood}
     * @throws ApiException BAD_REQUEST if qty ≤ 0 or goodTypeId is unknown
     */
    public OwnedGood grant(long characterId, String goodTypeId, double qty) {
        if (qty <= 0) {
            throw ApiException.badRequest("Quantity must be > 0");
        }
        if (GoodsCatalog.find(goodTypeId) == null) {
            throw ApiException.badRequest("Unknown goodTypeId: " + goodTypeId);
        }

        OwnedGood existing = ownedGoodRepository
                .findByCharacterIdAndGoodTypeId(characterId, goodTypeId)
                .orElse(null);

        if (existing != null) {
            existing.setQuantity(existing.getQuantity() + qty);
            return ownedGoodRepository.save(existing);
        } else {
            return ownedGoodRepository.save(new OwnedGood(characterId, goodTypeId, qty));
        }
    }

    /**
     * Decrements {@code qty} units from an {@link OwnedGood} row identified by
     * {@code ownedGoodId}. Removes the row entirely when quantity reaches zero.
     *
     * @param ownedGoodId database PK of the OwnedGood record
     * @param qty         units to sell/consume; must be &gt; 0
     * @return the updated (or deleted) {@link OwnedGood}; the returned object
     *         may have quantity == 0 and no longer be in the database
     * @throws ApiException BAD_REQUEST if qty ≤ 0, row not found, or
     *                      quantity would go negative
     */
    public OwnedGood decrement(long ownedGoodId, double qty) {
        if (qty <= 0) {
            throw ApiException.badRequest("Quantity must be > 0");
        }
        OwnedGood og = ownedGoodRepository.findById(ownedGoodId)
                .orElseThrow(() -> ApiException.badRequest(
                        "OwnedGood not found: " + ownedGoodId));

        double remaining = og.getQuantity() - qty;
        if (remaining < 0) {
            throw ApiException.badRequest(
                    "Cannot sell " + qty + " of " + og.getGoodTypeId()
                            + "; only " + og.getQuantity() + " owned");
        }
        if (remaining == 0) {
            ownedGoodRepository.delete(og);
            og.setQuantity(0);
        } else {
            og.setQuantity(remaining);
            ownedGoodRepository.save(og);
        }
        return og;
    }
}
