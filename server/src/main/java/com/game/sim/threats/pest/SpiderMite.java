package com.game.sim.threats.pest;

import com.game.core.data.Fault;
import com.game.core.data.PhenoStage;
import com.game.sim.threats.api.ThreatCategory;
import com.game.sim.threats.api.ThreatContext;
import com.game.sim.threats.api.ThreatEffect;
import com.game.sim.threats.api.ThreatMemory;
import com.game.sim.threats.api.ThreatSource;

/**
 * Grape Spider Mite — <em>Panonychus ulmi</em> (European red mite) and
 * <em>Tetranychus urticae</em> (two-spotted spider mite).
 *
 * <p>Spider mites explode in hot, dry, dusty conditions and are SUPPRESSED
 * heavily by predatory mites and beneficial insects present in a cover crop.
 * High humidity and rain reduce populations (wash off and fungal pathogens
 * attack mites). They damage leaf photosynthesis and — at high pressure — can
 * compromise berry ripening.
 *
 * <p>Suppressors:
 * <ul>
 *   <li>{@code coverCrop01} — primary suppressor: high beneficials (Phytoseiidae
 *       predatory mites) from cover crop; strong linear reduction.</li>
 *   <li>{@code ducks} — eat visible mite colonies.</li>
 * </ul>
 *
 * <p>Active from {@link PhenoStage#SHOOT_GROWTH} through {@link PhenoStage#RIPENING}.
 *
 * <p>Memory: {@code level} = population pressure 0..1.
 */
public final class SpiderMite implements ThreatSource {

    // ---- constants -------------------------------------------------------

    /** First active phenostage. */
    private static final PhenoStage ONSET_STAGE = PhenoStage.SHOOT_GROWTH;

    /** Last active phenostage. */
    private static final PhenoStage FINAL_STAGE = PhenoStage.RIPENING;

    /** Hot dry day threshold. */
    private static final double HOT_TEMP_C = 28.0;

    /** Low humidity threshold favouring mite explosion. */
    private static final double LOW_HUMIDITY_THRESHOLD = 0.45;

    /** Daily level growth under hot/dry conditions. */
    private static final double LEVEL_GROWTH_HOT_DRY = 0.025;

    /** Daily level growth under warm/moderate conditions. */
    private static final double LEVEL_GROWTH_WARM = 0.008;

    /** Rain or high humidity washes/kills mites. */
    private static final double RAIN_KNOCKDOWN_MM = 5.0;

    /** Level decay per rainy day. */
    private static final double LEVEL_DECAY_RAIN = 0.020;

    /** Modest decay on humid (but not rainy) days. */
    private static final double LEVEL_DECAY_HUMID = 0.005;

    /** Cover crop suppression coefficient (per unit). */
    private static final double COVER_CROP_SUPPRESSION = 0.75;

    /** Duck suppression factor. */
    private static final double DUCK_SUPPRESSION = 0.30;

    /** Health drain per unit level per day. */
    private static final double HEALTH_DRAIN_PER_LEVEL = 0.0025;

    /** Quality penalty per unit level per day (reduced photosynthesis = slower ripening). */
    private static final double QUALITY_PENALTY_PER_LEVEL = 0.00015;

    /** Tell threshold. */
    private static final double TELL_THRESHOLD = 0.30;

    // ---- ThreatSource impl -----------------------------------------------

    @Override
    public String id() {
        return "pest.spider_mite";
    }

    @Override
    public ThreatCategory category() {
        return ThreatCategory.PEST;
    }

    @Override
    public ThreatEffect evaluate(ThreatContext ctx) {
        int stageOrd = ctx.vine().stage().ordinal();
        if (stageOrd < ONSET_STAGE.ordinal() || stageOrd > FINAL_STAGE.ordinal()) {
            return ThreatEffect.none(ctx.memory());
        }

        ThreatMemory mem = ctx.memory();
        double level = mem.level();
        int ticksActive = mem.ticksActive();

        double meanTemp = ctx.today().meanTempC();
        double humidity = ctx.today().humidity01();
        double rain = ctx.today().rainMm();

        // Cover crop provides strongest suppression
        double suppressionFactor = 1.0
                - COVER_CROP_SUPPRESSION * ctx.coverCrop01()
                - (ctx.ducks() ? DUCK_SUPPRESSION : 0.0);
        suppressionFactor = Math.max(0.0, Math.min(1.0, suppressionFactor));

        // Population dynamics
        if (rain >= RAIN_KNOCKDOWN_MM) {
            level = Math.max(0.0, level - LEVEL_DECAY_RAIN);
        } else if (humidity > LOW_HUMIDITY_THRESHOLD + 0.30) {
            level = Math.max(0.0, level - LEVEL_DECAY_HUMID);
        } else if (meanTemp >= HOT_TEMP_C && humidity < LOW_HUMIDITY_THRESHOLD) {
            level = Math.min(1.0, level + LEVEL_GROWTH_HOT_DRY * suppressionFactor);
            ticksActive++;
        } else if (meanTemp >= 20.0) {
            level = Math.min(1.0, level + LEVEL_GROWTH_WARM * suppressionFactor);
            ticksActive++;
        } else {
            level = Math.max(0.0, level - LEVEL_DECAY_HUMID);
        }

        if (level < 0.01) {
            ThreatMemory next = new ThreatMemory(0.0, 0.0, 0, mem.yearsActive(), false);
            return ThreatEffect.none(next);
        }

        double healthDrain = HEALTH_DRAIN_PER_LEVEL * level;
        double qualityPenalty = QUALITY_PENALTY_PER_LEVEL * level;
        String tell = (level >= TELL_THRESHOLD && ticksActive % 7 == 1)
                ? "red spider mite colonies on leaves — bronze stippling, fine webbing"
                : "";

        ThreatMemory next = new ThreatMemory(level, 0.0, ticksActive, mem.yearsActive(), level > 0.2);

        return new ThreatEffect(
                -healthDrain,
                1.0,
                qualityPenalty,
                Fault.NONE,
                false,
                tell,
                next);
    }
}
