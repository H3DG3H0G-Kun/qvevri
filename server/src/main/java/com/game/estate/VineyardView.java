package com.game.estate;

import com.game.core.data.PhenoStage;
import com.game.core.data.Region;
import com.game.core.data.Variety;

import java.util.List;

/**
 * Read-only snapshot of a vineyard's simulated state on a given world day.
 * Produced by {@link VineyardReplayService#viewAt}.
 *
 * <p>Fields follow WORLD-CLOCK-SPEC §3 exactly.
 *
 * @param vineyardId             persistent vineyard id
 * @param ownerCharacterId       owner character id
 * @param region                 growing region
 * @param variety                grape variety
 * @param year                   world-clock year of simulation
 * @param dayOfYear              day within the year (0..364)
 * @param stage                  current phenological stage
 * @param brix                   sugar content in °Bx
 * @param taGL                   titratable acidity in g/L
 * @param pH                     must pH
 * @param healthFraction         vine health 0..1
 * @param estimatedYieldKg       potential yield in kg
 * @param ripe                   true when stage == RIPENING and brix >= 22
 * @param alreadyHarvestedThisYear true if lastHarvestedYear == year
 * @param recentEvents           last ~8 phenology transitions and threat tells
 */
public record VineyardView(
        Long vineyardId,
        Long ownerCharacterId,
        Region region,
        Variety variety,
        int year,
        int dayOfYear,
        PhenoStage stage,
        double brix,
        double taGL,
        double pH,
        double healthFraction,
        double estimatedYieldKg,
        boolean ripe,
        boolean alreadyHarvestedThisYear,
        List<String> recentEvents
) {}
