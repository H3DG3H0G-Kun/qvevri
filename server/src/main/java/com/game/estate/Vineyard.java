package com.game.estate;

import com.game.core.data.Region;
import com.game.core.data.Variety;

import jakarta.persistence.*;

/**
 * Persistent configuration of a player-owned vineyard plot.
 *
 * <p>Only the config is stored; evolving vine state is never persisted —
 * it is replayed deterministically from (seed, region, year) on demand
 * per the WORLD-CLOCK-SPEC §0 replay model.
 *
 * <p>Management levers (added per MANAGE-SPEC §2) are persisted here and
 * fed into {@code VineyardReplayService}. Defaults match the previously
 * hardcoded constants, so existing replay output is byte-identical.
 *
 * <p>Table: {@code mmo_vineyard}
 */
@Entity
@Table(name = "mmo_vineyard")
public class Vineyard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** FK → Character.id (owner). */
    @Column(nullable = false)
    private Long ownerCharacterId;

    /** Wine-growing region (drives weather model). */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Region region;

    /** Grape variety planted. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Variety variety;

    /**
     * Master RNG seed for this vineyard.
     * Deterministic from id/creation-time when not supplied by the client.
     */
    @Column(nullable = false)
    private long seed;

    /**
     * Number of buds retained at winter pruning.
     * Affects yield and vigour balance. Default = 12 (1..40).
     */
    @Column(nullable = false)
    private int budLoad = 12;

    /** Life-cycle status (GROWING or FALLOW). */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VineyardStatus status = VineyardStatus.GROWING;

    /**
     * The world-clock year in which the last successful harvest was taken.
     * 0 = never harvested.
     */
    @Column(nullable = false)
    private int lastHarvestedYear = 0;

    /** Epoch-ms creation timestamp. */
    @Column(nullable = false)
    private long createdAt;

    // ── Management levers (MANAGE-SPEC §2) ────────────────────────────────────
    // Defaults = previously hardcoded values in VineyardReplayService, so the
    // default-lever path remains byte-identical to the pre-manage behaviour.

    /** Vine on own roots → phylloxera-vulnerable. */
    @Column(nullable = false)
    private boolean ownRoots = true;

    /** Canopy openness 0..1 (higher = more airflow, lower fungal risk). */
    @Column(nullable = false)
    private double canopyOpenness01 = 0.40;

    /** Leaf removal around bunches (improves airflow). */
    @Column(nullable = false)
    private boolean leafPulled = false;

    /** Copper spray intensity 0..1 (suppresses downy mildew / black rot). */
    @Column(nullable = false)
    private double copperSpray01 = 0.0;

    /** Sulfur spray intensity 0..1 (suppresses powdery mildew). */
    @Column(nullable = false)
    private double sulfurSpray01 = 0.0;

    /** Bird/hail netting present (reduces bird damage at véraison). */
    @Column(nullable = false)
    private boolean netting = false;

    /** Guard dog present (reduces boar / deer damage). */
    @Column(nullable = false)
    private boolean guardDog = false;

    /** Falcons/hawks present (reduces starling pressure). */
    @Column(nullable = false)
    private boolean falcons = false;

    /** Cats present (reduces rodent pressure). */
    @Column(nullable = false)
    private boolean cats = false;

    /** Ducks present (reduces slug / insect pressure). */
    @Column(nullable = false)
    private boolean ducks = false;

    /** Cover crop intensity 0..1 (soil health, some pest suppression). */
    @Column(nullable = false)
    private double coverCrop01 = 0.0;

    /**
     * The world-clock year in which this vineyard was planted.
     * {@code null} = legacy / fully-established (age ≥ 3): treated as mature
     * so all existing rows and tests are unaffected.
     * When set, VineyardReplayService applies an establishment yield multiplier.
     */
    @Column(nullable = true)
    private Integer plantedYear;

    // ── Constructors ──────────────────────────────────────────────────────────

    protected Vineyard() {}

    /**
     * Primary constructor — management levers receive their spec defaults
     * automatically so existing call-sites need no change.
     * {@code plantedYear} is left {@code null} (= mature/established) so
     * existing rows and tests are byte-identical.
     */
    public Vineyard(Long ownerCharacterId, Region region, Variety variety,
                    long seed, int budLoad) {
        this.ownerCharacterId = ownerCharacterId;
        this.region           = region;
        this.variety          = variety;
        this.seed             = seed;
        this.budLoad          = budLoad;
        this.status           = VineyardStatus.GROWING;
        this.lastHarvestedYear = 0;
        this.createdAt        = System.currentTimeMillis();
        // lever fields remain at their field-initialiser defaults (spec defaults)
        // plantedYear stays null → mature/established; replay output unchanged
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public Long getId()                    { return id; }
    public Long getOwnerCharacterId()      { return ownerCharacterId; }
    public Region getRegion()              { return region; }
    public Variety getVariety()            { return variety; }
    public long getSeed()                  { return seed; }
    public int getBudLoad()                { return budLoad; }
    public VineyardStatus getStatus()      { return status; }
    public int getLastHarvestedYear()      { return lastHarvestedYear; }
    public long getCreatedAt()             { return createdAt; }

    /**
     * Returns the world-clock year this vineyard was planted, or {@code null}
     * if not set (meaning fully mature/established — existing behaviour).
     */
    public Integer getPlantedYear()        { return plantedYear; }

    // ── Lever getters ──────────────────────────────────────────────────────────

    public boolean isOwnRoots()            { return ownRoots; }
    public double getCanopyOpenness01()    { return canopyOpenness01; }
    public boolean isLeafPulled()          { return leafPulled; }
    public double getCopperSpray01()       { return copperSpray01; }
    public double getSulfurSpray01()       { return sulfurSpray01; }
    public boolean isNetting()             { return netting; }
    public boolean isGuardDog()            { return guardDog; }
    public boolean isFalcons()             { return falcons; }
    public boolean isCats()                { return cats; }
    public boolean isDucks()               { return ducks; }
    public double getCoverCrop01()         { return coverCrop01; }

    // ── Setters ───────────────────────────────────────────────────────────────

    public void setLastHarvestedYear(int year)   { this.lastHarvestedYear = year; }
    public void setStatus(VineyardStatus status) { this.status = status; }
    public void setPlantedYear(Integer plantedYear) { this.plantedYear = plantedYear; }

    // ── Lever setters ──────────────────────────────────────────────────────────

    public void setBudLoad(int budLoad)                   { this.budLoad = budLoad; }
    public void setOwnRoots(boolean ownRoots)             { this.ownRoots = ownRoots; }
    public void setCanopyOpenness01(double v)             { this.canopyOpenness01 = v; }
    public void setLeafPulled(boolean leafPulled)         { this.leafPulled = leafPulled; }
    public void setCopperSpray01(double v)                { this.copperSpray01 = v; }
    public void setSulfurSpray01(double v)                { this.sulfurSpray01 = v; }
    public void setNetting(boolean netting)               { this.netting = netting; }
    public void setGuardDog(boolean guardDog)             { this.guardDog = guardDog; }
    public void setFalcons(boolean falcons)               { this.falcons = falcons; }
    public void setCats(boolean cats)                     { this.cats = cats; }
    public void setDucks(boolean ducks)                   { this.ducks = ducks; }
    public void setCoverCrop01(double v)                  { this.coverCrop01 = v; }
}
