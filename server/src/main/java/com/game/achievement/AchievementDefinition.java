package com.game.achievement;

/**
 * Immutable definition of a single milestone achievement in the QVEVRI game.
 *
 * <p>IDs are stable strings used as FK references in {@link PlayerAchievement};
 * never rename an id once data is in production (add a migration instead).
 *
 * <p>{@code rewardGoodTypeId} is nullable — null means no goods reward for this
 * achievement. When non-null, the value must match a valid id from
 * {@link com.game.goods.GoodsCatalog}.
 */
public final class AchievementDefinition {

    private final String id;
    private final String title;
    private final String description;
    private final double rewardGel;
    /** Nullable — null means no goods reward. */
    private final String rewardGoodTypeId;
    private final double rewardGoodQty;

    public AchievementDefinition(
            String id,
            String title,
            String description,
            double rewardGel,
            String rewardGoodTypeId,
            double rewardGoodQty) {
        this.id               = id;
        this.title            = title;
        this.description      = description;
        this.rewardGel        = rewardGel;
        this.rewardGoodTypeId = rewardGoodTypeId;
        this.rewardGoodQty    = rewardGoodQty;
    }

    public String getId()               { return id; }
    public String getTitle()            { return title; }
    public String getDescription()      { return description; }
    public double getRewardGel()        { return rewardGel; }
    public String getRewardGoodTypeId() { return rewardGoodTypeId; }
    public double getRewardGoodQty()    { return rewardGoodQty; }
}
