package com.game.wine;

/**
 * View DTO returned by {@code GET /api/wine/ferment/{cellarItemId}/status}
 * and by the start-fermentation and bottle endpoints.
 *
 * <p>Fields:
 * <ul>
 *   <li>{@code cellarItemId} — the item being tracked</li>
 *   <li>{@code fermentationState} — current state string (null, FERMENTING, READY, BOTTLED)</li>
 *   <li>{@code fermentStartedDay} — absolute day fermentation started (null if not started)</li>
 *   <li>{@code fermentReadyDay} — absolute day fermentation completes (null if not started)</li>
 *   <li>{@code agingFromDay} — absolute day aging started (null if not ready yet)</li>
 *   <li>{@code currentAbsoluteDay} — the world clock day at response time</li>
 *   <li>{@code daysUntilReady} — sim-days remaining until READY (0 if already ready/bottled)</li>
 *   <li>{@code currentQuality} — quality as of now (base + aging gain)</li>
 *   <li>{@code baseQuality} — quality before aging</li>
 *   <li>{@code style} — current style on the CellarItem</li>
 *   <li>{@code vesselGoodId} — the vessel's OwnedGood id (null if none)</li>
 * </ul>
 */
public record FermentStatusView(
        Long cellarItemId,
        String fermentationState,
        Long fermentStartedDay,
        Long fermentReadyDay,
        Long agingFromDay,
        long currentAbsoluteDay,
        long daysUntilReady,
        double currentQuality,
        Double baseQuality,
        String style,
        Long vesselGoodId
) {}
