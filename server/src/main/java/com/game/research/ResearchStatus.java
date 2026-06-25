package com.game.research;

/**
 * String constants for {@link PlayerResearch#getResearchStatus()}.
 *
 * <p>Using string constants (not an enum) keeps the column value transparent
 * for debugging and avoids JPA ordinal/name mapping issues.
 */
public final class ResearchStatus {

    public static final String RESEARCHING = "RESEARCHING";
    public static final String COMPLETE    = "COMPLETE";

    private ResearchStatus() {}
}
