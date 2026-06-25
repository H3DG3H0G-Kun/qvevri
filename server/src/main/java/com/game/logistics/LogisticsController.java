package com.game.logistics;

import com.game.character.Character;
import com.game.market.TokenHelper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for LANE LOGISTICS — ship goods/wine between regions.
 *
 * <p>All endpoints require {@code Authorization: Bearer <token>} and verify
 * that {@code characterId} belongs to the authenticated account.
 * Authentication is inline via {@link TokenHelper} (no Spring Security rules
 * needed beyond the existing {@code /api/logistics/**} permitAll in SecurityConfig).
 *
 * <h2>Endpoints</h2>
 * <ul>
 *   <li>POST /api/logistics/ship            — create a shipment</li>
 *   <li>GET  /api/logistics/{characterId}   — list a character's shipments</li>
 *   <li>POST /api/logistics/collect         — collect an arrived shipment</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/logistics")
public class LogisticsController {

    private final TokenHelper       tokenHelper;
    private final LogisticsService  logisticsService;

    public LogisticsController(TokenHelper tokenHelper,
                               LogisticsService logisticsService) {
        this.tokenHelper      = tokenHelper;
        this.logisticsService = logisticsService;
    }

    // ── POST /api/logistics/ship ──────────────────────────────────────────────

    /**
     * Creates a new shipment.
     *
     * <p>Request body:
     * <pre>{@code
     * {
     *   "characterId":          <long>,
     *   "kind":                 "GOODS" | "CELLAR_ITEM",
     *   "refId":                <string>,   // goodTypeId or cellarItemId
     *   "quantity":             <double>,   // > 0; ignored for CELLAR_ITEM
     *   "fromRegion":           <string>,   // optional; defaults to character's homeRegion
     *   "toRegion":             <string>,   // Region enum name
     *   "recipientCharacterId": <long>      // optional; null = self
     * }
     * }</pre>
     *
     * <p>If {@code fromRegion} is omitted, defaults to the sender character's
     * {@link Character#getHomeRegion()} value.
     *
     * @return 200 with the persisted {@link Shipment}
     */
    @PostMapping("/ship")
    public ResponseEntity<Shipment> ship(
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {

        Long characterId = getLong(body, "characterId");
        Character sender = tokenHelper.requireOwnedCharacter(request, characterId);

        String kind      = getString(body, "kind");
        String refId     = getString(body, "refId");
        double quantity  = getDouble(body, "quantity");
        String toRegion  = getString(body, "toRegion");

        // fromRegion: use provided value or fall back to the character's home region
        String fromRegion;
        if (body.containsKey("fromRegion") && body.get("fromRegion") != null) {
            fromRegion = body.get("fromRegion").toString();
        } else {
            fromRegion = sender.getHomeRegion().name();
        }

        Long recipientCharacterId = null;
        if (body.containsKey("recipientCharacterId") && body.get("recipientCharacterId") != null) {
            recipientCharacterId = ((Number) body.get("recipientCharacterId")).longValue();
        }

        Shipment shipment = logisticsService.ship(
                characterId, kind, refId, quantity,
                fromRegion, toRegion, recipientCharacterId);

        return ResponseEntity.ok(shipment);
    }

    // ── GET /api/logistics/{characterId} ─────────────────────────────────────

    /**
     * Returns all shipments for the given character (any status).
     *
     * @param characterId the owning character's id (path variable)
     * @return 200 with list of {@link Shipment}
     */
    @GetMapping("/{characterId}")
    public ResponseEntity<List<Shipment>> listShipments(
            @PathVariable Long characterId,
            HttpServletRequest request) {

        tokenHelper.requireOwnedCharacter(request, characterId);
        return ResponseEntity.ok(logisticsService.listShipments(characterId));
    }

    // ── POST /api/logistics/collect ───────────────────────────────────────────

    /**
     * Collects an arrived shipment and delivers it to the recipient.
     *
     * <p>Request body: {@code { "characterId": <long>, "shipmentId": <long> }}
     *
     * <p>Returns 400 with code {@code NOT_ARRIVED} if
     * {@code currentDay < arriveDay}.
     *
     * @return 200 with the COLLECTED {@link Shipment}
     */
    @PostMapping("/collect")
    public ResponseEntity<Shipment> collect(
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {

        Long characterId = getLong(body, "characterId");
        tokenHelper.requireOwnedCharacter(request, characterId);

        Long shipmentId = getLong(body, "shipmentId");
        Shipment shipment = logisticsService.collect(characterId, shipmentId);
        return ResponseEntity.ok(shipment);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private static Long getLong(Map<String, Object> body, String key) {
        Object val = body.get(key);
        if (val == null) {
            throw com.game.exception.ApiException.badRequest("Missing required field: " + key);
        }
        return ((Number) val).longValue();
    }

    private static String getString(Map<String, Object> body, String key) {
        Object val = body.get(key);
        if (val == null) {
            throw com.game.exception.ApiException.badRequest("Missing required field: " + key);
        }
        return val.toString();
    }

    private static double getDouble(Map<String, Object> body, String key) {
        Object val = body.get(key);
        if (val == null) {
            return 1.0; // sensible default; service validates > 0
        }
        return ((Number) val).doubleValue();
    }
}
