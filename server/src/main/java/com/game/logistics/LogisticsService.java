package com.game.logistics;

import com.game.exception.ApiException;
import com.game.goods.GoodsService;
import com.game.goods.OwnedGood;
import com.game.goods.OwnedGoodRepository;
import com.game.market.CellarItem;
import com.game.market.CellarItemRepository;
import com.game.world.Region;
import com.game.world.WorldCatalog;
import com.game.world.clock.WorldClockService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Business logic for the LANE LOGISTICS shipment system.
 *
 * <p>Owns the three core operations:
 * <ol>
 *   <li>{@link #ship} — validate ownership, debit/escrow the asset, persist IN_TRANSIT.</li>
 *   <li>{@link #listShipments} — list a character's shipments.</li>
 *   <li>{@link #collect} — deliver the asset once arriveDay is reached.</li>
 * </ol>
 *
 * <p>All public methods are {@code @Transactional}.
 */
@Service
@Transactional
public class LogisticsService {

    private static final String KIND_GOODS       = "GOODS";
    private static final String KIND_CELLAR_ITEM = "CELLAR_ITEM";

    private final ShipmentRepository     shipmentRepository;
    private final OwnedGoodRepository    ownedGoodRepository;
    private final GoodsService           goodsService;
    private final CellarItemRepository   cellarItemRepository;
    private final WorldClockService      clockService;

    public LogisticsService(ShipmentRepository shipmentRepository,
                            OwnedGoodRepository ownedGoodRepository,
                            GoodsService goodsService,
                            CellarItemRepository cellarItemRepository,
                            WorldClockService clockService) {
        this.shipmentRepository  = shipmentRepository;
        this.ownedGoodRepository = ownedGoodRepository;
        this.goodsService        = goodsService;
        this.cellarItemRepository = cellarItemRepository;
        this.clockService        = clockService;
    }

    // ── ship ─────────────────────────────────────────────────────────────────

    /**
     * Creates a new IN_TRANSIT shipment for the given sender.
     *
     * <p>For GOODS: finds the OwnedGood row by (characterId, goodTypeId) and
     * calls {@link GoodsService#decrement} to remove the quantity from inventory.
     *
     * <p>For CELLAR_ITEM: loads the CellarItem (must be owned by sender and not
     * already escrowed/in-transit for another purpose), then sets
     * {@code escrowed = true} to block double-handling.
     *
     * @param ownerCharacterId    the sender's character id
     * @param kind                {@code "GOODS"} or {@code "CELLAR_ITEM"}
     * @param refId               goodTypeId or cellarItemId (as String)
     * @param quantity            quantity to ship (must be &gt; 0 for GOODS; ignored for CELLAR_ITEM — always 1)
     * @param fromRegion          origin region (sender's home or explicitly provided)
     * @param toRegion            destination region name (must be a valid {@link Region} enum value)
     * @param recipientCharacterId nullable; if null, defaults to ownerCharacterId on collect
     * @return the persisted {@link Shipment}
     * @throws ApiException 400 on validation failures, unknown region, or insufficient quantity
     */
    public Shipment ship(Long ownerCharacterId,
                         String kind,
                         String refId,
                         double quantity,
                         String fromRegion,
                         String toRegion,
                         Long recipientCharacterId) {

        // Validate and parse region enums
        Region from = parseRegion(fromRegion);
        Region to   = parseRegion(toRegion);

        long currentDay = clockService.currentAbsoluteDay();
        int  travelDays = GeoUtil.travelDays(from, to);
        long arriveDay  = currentDay + travelDays;

        String refIdToStore;
        double quantityToStore;

        if (KIND_GOODS.equals(kind)) {
            if (quantity <= 0) {
                throw ApiException.badRequest("Quantity must be > 0 for GOODS shipments");
            }
            // Find the owned-good row for this character + goodTypeId
            OwnedGood owned = ownedGoodRepository
                    .findByCharacterIdAndGoodTypeId(ownerCharacterId, refId)
                    .orElseThrow(() -> ApiException.badRequest(
                            "Character does not own any goods of type: " + refId));

            // Decrement will throw if quantity is insufficient
            goodsService.decrement(owned.getId(), quantity);

            refIdToStore      = refId;
            quantityToStore   = quantity;

        } else if (KIND_CELLAR_ITEM.equals(kind)) {
            long cellarItemId = parseCellarItemId(refId);
            CellarItem item = cellarItemRepository
                    .findByIdAndCharacterId(cellarItemId, ownerCharacterId)
                    .orElseThrow(() -> ApiException.badRequest(
                            "CellarItem " + refId + " not found or not owned by character " + ownerCharacterId));

            if (item.isEscrowed()) {
                throw ApiException.badRequest(
                        "CellarItem " + refId + " is already escrowed and cannot be shipped");
            }

            // Mark escrowed so it can't be double-handled
            item.setEscrowed(true);
            cellarItemRepository.save(item);

            refIdToStore    = refId;
            quantityToStore = 1.0;

        } else {
            throw ApiException.badRequest("Unknown shipment kind: " + kind
                    + ". Must be GOODS or CELLAR_ITEM");
        }

        Shipment shipment = new Shipment(
                ownerCharacterId,
                recipientCharacterId,
                kind,
                refIdToStore,
                quantityToStore,
                fromRegion,
                toRegion,
                currentDay,
                arriveDay);

        return shipmentRepository.save(shipment);
    }

    // ── listShipments ─────────────────────────────────────────────────────────

    /**
     * Returns all shipments for the given owning character (any status).
     *
     * @param ownerCharacterId the character whose shipments to return
     * @return list of shipments (may be empty)
     */
    @Transactional(readOnly = true)
    public List<Shipment> listShipments(Long ownerCharacterId) {
        return shipmentRepository.findByOwnerCharacterId(ownerCharacterId);
    }

    // ── collect ───────────────────────────────────────────────────────────────

    /**
     * Collects an arrived shipment and delivers it to the recipient.
     *
     * <p>Preconditions (400 if violated):
     * <ul>
     *   <li>Shipment must exist and be owned by {@code characterId}.</li>
     *   <li>{@code shipStatus} must be {@code "IN_TRANSIT"} (else 400 ALREADY_COLLECTED).</li>
     *   <li>{@code currentDay >= arriveDay} (else 400 NOT_ARRIVED).</li>
     * </ul>
     *
     * <p>On success:
     * <ul>
     *   <li>GOODS: calls {@link GoodsService#grant} on the effective recipient.</li>
     *   <li>CELLAR_ITEM: reassigns {@code characterId} on the item to recipient,
     *       clears {@code escrowed}.</li>
     * </ul>
     *
     * @param ownerCharacterId the collecting character (must own the shipment)
     * @param shipmentId       the shipment to collect
     * @return the updated (COLLECTED) {@link Shipment}
     */
    public Shipment collect(Long ownerCharacterId, Long shipmentId) {
        Shipment shipment = shipmentRepository.findById(shipmentId)
                .orElseThrow(() -> ApiException.badRequest(
                        "Shipment not found: " + shipmentId));

        if (!shipment.getOwnerCharacterId().equals(ownerCharacterId)) {
            throw ApiException.notFound("Shipment " + shipmentId
                    + " not found or not owned by character " + ownerCharacterId);
        }

        if ("COLLECTED".equals(shipment.getShipStatus())) {
            throw ApiException.badRequest("Shipment " + shipmentId + " has already been collected");
        }
        if ("CANCELLED".equals(shipment.getShipStatus())) {
            throw ApiException.badRequest("Shipment " + shipmentId + " has been cancelled");
        }

        long currentDay = clockService.currentAbsoluteDay();
        if (currentDay < shipment.getArriveDay()) {
            throw new ApiException("NOT_ARRIVED",
                    "Shipment is not yet arrived. Arrives on day " + shipment.getArriveDay()
                            + "; current day is " + currentDay,
                    org.springframework.http.HttpStatus.BAD_REQUEST);
        }

        // Effective recipient: fallback to owner if not specified
        long recipientId = shipment.getRecipientCharacterId() != null
                ? shipment.getRecipientCharacterId()
                : shipment.getOwnerCharacterId();

        if (KIND_GOODS.equals(shipment.getKind())) {
            goodsService.grant(recipientId, shipment.getRefId(), shipment.getQuantity());

        } else if (KIND_CELLAR_ITEM.equals(shipment.getKind())) {
            long cellarItemId = parseCellarItemId(shipment.getRefId());
            CellarItem item = cellarItemRepository.findById(cellarItemId)
                    .orElseThrow(() -> ApiException.badRequest(
                            "CellarItem " + shipment.getRefId() + " no longer exists"));

            item.setCharacterId(recipientId);
            item.setEscrowed(false);
            cellarItemRepository.save(item);
        }

        shipment.setShipStatus("COLLECTED");
        return shipmentRepository.save(shipment);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private static Region parseRegion(String regionName) {
        // Validate against the WorldCatalog so unknown strings return 400, not 500
        return WorldCatalog.REGIONS.stream()
                .filter(r -> r.region().name().equals(regionName))
                .map(com.game.world.RegionInfo::region)
                .findFirst()
                .orElseThrow(() -> ApiException.badRequest(
                        "Unknown region: " + regionName
                                + ". Valid values: KAKHETI, KARTLI, IMERETI, "
                                + "RACHA_LECHKHUMI, SAMEGRELO, GURIA_ADJARA, MESKHETI"));
    }

    private static long parseCellarItemId(String refId) {
        try {
            return Long.parseLong(refId);
        } catch (NumberFormatException e) {
            throw ApiException.badRequest(
                    "refId must be a numeric CellarItem id for CELLAR_ITEM shipments, got: " + refId);
        }
    }
}
