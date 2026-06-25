package com.game.market;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Request body for POST /api/cellar/{characterId}/grow.
 *
 * <p>Fields mirror the VineyardRequest parameters that the client controls.
 * Defaults chosen to match the VineyardService defaults so that all fields
 * are optional — omitting a field uses the sim default.
 */
public class GrowRequest {

    /** Master RNG seed — drives full determinism. */
    private long seed = 42L;

    /** Number of buds left after pruning (1..40). */
    @Min(1) @Max(40)
    private int budLoad = 12;

    /** Day of year to harvest (1..364). */
    @Min(1) @Max(364)
    private int pickDay = 270;

    /** Whether to run the threat engine during the simulation. */
    private boolean threats = true;

    // ── Constructors ─────────────────────────────────────────────────────────

    public GrowRequest() {}

    public GrowRequest(long seed, int budLoad, int pickDay, boolean threats) {
        this.seed    = seed;
        this.budLoad = budLoad;
        this.pickDay = pickDay;
        this.threats = threats;
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    public long getSeed()     { return seed; }
    public int getBudLoad()   { return budLoad; }
    public int getPickDay()   { return pickDay; }
    public boolean isThreats(){ return threats; }

    // ── Setters ──────────────────────────────────────────────────────────────

    public void setSeed(long seed)       { this.seed = seed; }
    public void setBudLoad(int b)        { this.budLoad = b; }
    public void setPickDay(int p)        { this.pickDay = p; }
    public void setThreats(boolean t)    { this.threats = t; }
}
