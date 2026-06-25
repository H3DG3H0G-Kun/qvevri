package com.game.festival;

/**
 * Immutable definition of a single world festival event.
 *
 * <p>All fields are set at construction; getters only. The {@code id} is a
 * stable string (never rename once data is in production; add a migration
 * instead). The [{@code startDayOfYear}, {@code endDayOfYear}] window is
 * non-wrapping (endDayOfYear >= startDayOfYear) in v1.
 *
 * <p>Georgian wine-culture flavour — ids match the canonical Georgian terms.
 */
public final class FestivalDefinition {

    private final String id;
    private final String name;
    private final String description;

    /** Inclusive start day-of-year (0..364). */
    private final int startDayOfYear;

    /** Inclusive end day-of-year (0..364, >= startDayOfYear). */
    private final int endDayOfYear;

    /** Category string for the client (e.g. "HARVEST_BONUS", "QUALITY_BOOST"). */
    private final String bonusType;

    /** Magnitude of the bonus effect (interpretation depends on bonusType). */
    private final double bonusValue;

    /** GEL reward credited to the character wallet on first participation. */
    private final double rewardGel;

    public FestivalDefinition(
            String id,
            String name,
            String description,
            int startDayOfYear,
            int endDayOfYear,
            String bonusType,
            double bonusValue,
            double rewardGel) {
        this.id             = id;
        this.name           = name;
        this.description    = description;
        this.startDayOfYear = startDayOfYear;
        this.endDayOfYear   = endDayOfYear;
        this.bonusType      = bonusType;
        this.bonusValue     = bonusValue;
        this.rewardGel      = rewardGel;
    }

    public String getId()             { return id; }
    public String getName()           { return name; }
    public String getDescription()    { return description; }
    public int    getStartDayOfYear() { return startDayOfYear; }
    public int    getEndDayOfYear()   { return endDayOfYear; }
    public String getBonusType()      { return bonusType; }
    public double getBonusValue()     { return bonusValue; }
    public double getRewardGel()      { return rewardGel; }
}
