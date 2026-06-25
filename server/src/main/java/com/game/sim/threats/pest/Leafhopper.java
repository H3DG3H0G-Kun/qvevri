package com.game.sim.threats.pest;

import com.game.core.data.Fault;
import com.game.core.data.PhenoStage;
import com.game.sim.threats.api.ThreatCategory;
import com.game.sim.threats.api.ThreatContext;
import com.game.sim.threats.api.ThreatEffect;
import com.game.sim.threats.api.ThreatMemory;
import com.game.sim.threats.api.ThreatSource;

/**
 * Grape Leafhopper (<em>Empoasca vitis</em> / <em>Zygina rhamni</em>).
 *
 * <p>Leafhoppers pierce leaf cells and suck sap, causing stippling, leaf curl
 * and early senescence. They thrive in warm, dry conditions. Two generations
 * per season: first from shoot-growth, second (more damaging) from mid-summer.
 *
 * <p>Suppressors:
 * <ul>
 *   <li>{@code coverCrop01} — cover crop hosts beneficials (parasitic wasps,
 *       spiders) that suppress leafhopper populations; linear reduction.</li>
 *   <li>{@code ducks} — ducks eat adult leafhoppers and nymphs.</li>
 * </ul>
 *
 * <p>Pressure builds with warm dry weather; high humidity suppresses.
 * Active from {@link PhenoStage#SHOOT_GROWTH} onward.
 *
 * <p>Memory: {@code level} = population pressure 0..1, {@code ticksActive}
 * = consecutive active days this season.
 */
public final class Leafhopper implements ThreatSource {

    // ---- constants -------------------------------------------------------

    /** First active phenostage. */
    private static final PhenoStage ONSET_STAGE = PhenoStage.SHOOT_GROWTH;

    /** Warm day threshold for pressure build. */
    private static final double WARM_TEMP_C = 22.0;

    /** Humidity that suppresses leafhopper. Above this, growth slows. */
    private static final double HUMIDITY_SUPPRESS_THRESHOLD = 0.70;

    /** Base daily level growth rate in warm dry conditions. */
    private static final double LEVEL_GROWTH_RATE = 0.012;

    /** Daily level decay in cool/humid conditions. */
    private static final double LEVEL_DECAY_RATE = 0.008;

    /** Cover crop suppression per unit of coverCrop01. */
    private static final double COVER_CROP_SUPPRESSION = 0.6;

    /** Duck suppression factor. */
    private static final double DUCK_SUPPRESSION = 0.5;

    /** Health drain per unit level per day. */
    private static final double HEALTH_DRAIN_PER_LEVEL = 0.002;

    /** Quality penalty per unit level per day (stippling reduces photosynthate). */
    private static final double QUALITY_PENALTY_PER_LEVEL = 0.0001;

    /** Threshold above which visible tells appear. */
    private static final double TELL_THRESHOLD = 0.35;

    // ---- ThreatSource impl -----------------------------------------------

    @Override
    public String id() {
        return "pest.leafhopper";
    }

    @Override
    public ThreatCategory category() {
        return ThreatCategory.PEST;
    }

    @Override
    public ThreatEffect evaluate(ThreatContext ctx) {
        // Not active before shoot growth
        if (stageOrdinal(ctx.vine().stage()) < stageOrdinal(ONSET_STAGE)) {
            return ThreatEffect.none(ThreatMemory.none());
        }

        ThreatMemory mem = ctx.memory();
        double level = mem.level();
        int ticksActive = mem.ticksActive();

        double meanTemp = ctx.today().meanTempC();
        double humidity = ctx.today().humidity01();

        // Cover crop and ducks provide suppression
        double suppressionFactor = 1.0
                - COVER_CROP_SUPPRESSION * ctx.coverCrop01()
                - (ctx.ducks() ? DUCK_SUPPRESSION : 0.0);
        suppressionFactor = Math.max(0.0, suppressionFactor);

        boolean warmAndDry = meanTemp >= WARM_TEMP_C && humidity < HUMIDITY_SUPPRESS_THRESHOLD;

        if (warmAndDry) {
            level = Math.min(1.0, level + LEVEL_GROWTH_RATE * suppressionFactor);
            ticksActive++;
        } else {
            level = Math.max(0.0, level - LEVEL_DECAY_RATE);
            ticksActive = level > 0 ? ticksActive : 0;
        }

        if (level < 0.01) {
            ThreatMemory next = new ThreatMemory(0.0, 0.0, 0, mem.yearsActive(), false);
            return ThreatEffect.none(next);
        }

        double healthDrain = HEALTH_DRAIN_PER_LEVEL * level;
        double qualityPenalty = QUALITY_PENALTY_PER_LEVEL * level;
        String tell = (level >= TELL_THRESHOLD && ticksActive % 7 == 1)
                ? "leafhopper stippling on leaves — bleached patches, early senescence"
                : "";

        ThreatMemory next = new ThreatMemory(level, 0.0, ticksActive, mem.yearsActive(), level > 0.1);

        return new ThreatEffect(
                -healthDrain,
                1.0, // no direct yield hit (indirect via health)
                qualityPenalty,
                Fault.NONE,
                false,
                tell,
                next);
    }

    // ---- helpers ---------------------------------------------------------

    private static int stageOrdinal(PhenoStage stage) {
        return stage.ordinal();
    }
}
