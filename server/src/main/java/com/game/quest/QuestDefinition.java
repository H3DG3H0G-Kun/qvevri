package com.game.quest;

/**
 * Immutable definition of a single quest.
 *
 * <p>The {@code objectiveType} string acts as an enum discriminator for the
 * client (PLANT_VINE / SELL_BOTTLES / CRAFT_VESSEL / HARVEST / VISIT). In v1
 * the objective is auto-satisfied on complete; deeper tracking is deferred.
 *
 * <p>All fields are set at construction; getters only.
 */
public final class QuestDefinition {

    private final String id;
    private final String title;
    private final String description;
    private final String giverNpc;
    private final String objectiveType;
    private final int    objectiveCount;
    private final double rewardGel;
    /** Nullable — null means no goods reward. */
    private final String rewardGoodTypeId;
    private final double rewardGoodQty;

    public QuestDefinition(
            String id,
            String title,
            String description,
            String giverNpc,
            String objectiveType,
            int    objectiveCount,
            double rewardGel,
            String rewardGoodTypeId,
            double rewardGoodQty) {
        this.id               = id;
        this.title            = title;
        this.description      = description;
        this.giverNpc         = giverNpc;
        this.objectiveType    = objectiveType;
        this.objectiveCount   = objectiveCount;
        this.rewardGel        = rewardGel;
        this.rewardGoodTypeId = rewardGoodTypeId;
        this.rewardGoodQty    = rewardGoodQty;
    }

    public String getId()               { return id; }
    public String getTitle()            { return title; }
    public String getDescription()      { return description; }
    public String getGiverNpc()         { return giverNpc; }
    public String getObjectiveType()    { return objectiveType; }
    public int    getObjectiveCount()   { return objectiveCount; }
    public double getRewardGel()        { return rewardGel; }
    public String getRewardGoodTypeId() { return rewardGoodTypeId; }
    public double getRewardGoodQty()    { return rewardGoodQty; }
}
