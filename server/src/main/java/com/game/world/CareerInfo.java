package com.game.world;

/**
 * Descriptive data for a career, returned by GET /api/world/careers.
 */
public record CareerInfo(
        CareerType type,
        String displayName,
        String description
) {}
