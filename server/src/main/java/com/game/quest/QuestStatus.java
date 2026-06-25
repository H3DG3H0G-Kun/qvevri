package com.game.quest;

/**
 * Stable string constants for {@link PlayerQuest#getQuestStatus()}.
 *
 * <p>Using string constants (rather than a JPA @Enumerated enum) keeps the
 * schema flexible and avoids ordinal-vs-string JPA pitfalls.
 */
public final class QuestStatus {

    /** Quest has been offered to the player but not yet accepted. */
    public static final String OFFERED   = "OFFERED";

    /** Quest is accepted and in progress. */
    public static final String ACTIVE    = "ACTIVE";

    /** Quest is completed; reward has been granted. */
    public static final String COMPLETED = "COMPLETED";

    private QuestStatus() {}
}
