package com.game.vineyard;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for POST /api/vineyard/simulate.
 *
 * <p>All fields are optional in the JSON payload; defaults match VINEYARD-API §1.
 * Jackson deserialises missing fields to the field-initialiser values here,
 * so omitting a field is identical to sending the default.
 */
public class VineyardRequest {

    /** Master RNG seed — drives full determinism. */
    private long seed = 42L;

    /** Wine-growing region name; must map to {@link com.game.core.data.Region}. */
    @NotNull
    private String region = "KAKHETI";

    /** Grape variety name; must map to {@link com.game.core.data.Variety}. */
    @NotNull
    private String variety = "SAPERAVI";

    /** Soil type name; must map to {@link com.game.core.data.SoilType}. */
    @NotNull
    private String soil = "HUMUS_CARBONATE";

    /** Number of buds left after pruning (1..40). */
    @Min(1) @Max(40)
    private int budLoad = 12;

    /** Day of year to harvest (1..364). */
    @Min(1) @Max(364)
    private int pickDay = 270;

    /** Whether to run the threat engine during the simulation. */
    private boolean threats = true;

    // ── No-arg constructor (required for Jackson deserialization) ────────────
    public VineyardRequest() {}

    // ── All-arg constructor (useful in tests) ────────────────────────────────
    public VineyardRequest(long seed, String variety, String soil,
                           int budLoad, int pickDay, boolean threats) {
        this.seed    = seed;
        this.variety = variety;
        this.soil    = soil;
        this.budLoad = budLoad;
        this.pickDay = pickDay;
        this.threats = threats;
    }

    // ── Getters ──────────────────────────────────────────────────────────────
    public long getSeed()     { return seed; }
    public String getRegion() { return region; }
    public String getVariety(){ return variety; }
    public String getSoil()   { return soil; }
    public int getBudLoad()   { return budLoad; }
    public int getPickDay()   { return pickDay; }
    public boolean isThreats(){ return threats; }

    // ── Setters (Jackson needs these for deserialization into POJO) ──────────
    public void setSeed(long seed)       { this.seed = seed; }
    public void setRegion(String r)      { this.region = r; }
    public void setVariety(String v)     { this.variety = v; }
    public void setSoil(String s)        { this.soil = s; }
    public void setBudLoad(int b)        { this.budLoad = b; }
    public void setPickDay(int p)        { this.pickDay = p; }
    public void setThreats(boolean t)    { this.threats = t; }
}
