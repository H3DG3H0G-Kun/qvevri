package com.game.world;

/**
 * Descriptive data for a region, returned by GET /api/world/regions.
 * latitude/longitude are WGS84 decimal degrees for the region's representative town.
 */
public record RegionInfo(
        Region region,
        String displayName,
        String climate,
        String signatureGrapes,
        String methodNote,
        double latitude,
        double longitude
) {}
