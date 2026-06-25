package com.game.estate;

/**
 * Read-only snapshot of a vineyard's current management plan.
 * Returned by GET /api/vineyards/{vineyardId}/management.
 *
 * <p>Field semantics and value ranges follow MANAGE-SPEC §2 exactly.
 *
 * @param vineyardId       persistent vineyard id
 * @param budLoad          buds retained at pruning (1..40)
 * @param ownRoots         vine on own roots (phylloxera-vulnerable)
 * @param canopyOpenness01 canopy management level (0..1)
 * @param leafPulled       leaf removal around bunches
 * @param copperSpray01    copper spray intensity (0..1)
 * @param sulfurSpray01    sulfur spray intensity (0..1)
 * @param netting          bird/hail netting present
 * @param guardDog         guard dog present
 * @param falcons          falcons/hawks present
 * @param cats             cats present
 * @param ducks            ducks present
 * @param coverCrop01      cover crop intensity (0..1)
 */
public record ManagementPlanDto(
        Long    vineyardId,
        int     budLoad,
        boolean ownRoots,
        double  canopyOpenness01,
        boolean leafPulled,
        double  copperSpray01,
        double  sulfurSpray01,
        boolean netting,
        boolean guardDog,
        boolean falcons,
        boolean cats,
        boolean ducks,
        double  coverCrop01
) {
    /** Convenience factory — reads levers straight from the entity. */
    public static ManagementPlanDto from(Vineyard v) {
        return new ManagementPlanDto(
                v.getId(),
                v.getBudLoad(),
                v.isOwnRoots(),
                v.getCanopyOpenness01(),
                v.isLeafPulled(),
                v.getCopperSpray01(),
                v.getSulfurSpray01(),
                v.isNetting(),
                v.isGuardDog(),
                v.isFalcons(),
                v.isCats(),
                v.isDucks(),
                v.getCoverCrop01()
        );
    }
}
