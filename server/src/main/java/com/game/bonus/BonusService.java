package com.game.bonus;

import com.game.build.BuildService;
import com.game.career.CareerProfile;
import com.game.career.CareerProfileCatalog;
import com.game.character.Character;
import com.game.character.CharacterRepository;
import com.game.labor.LaborService;
import com.game.research.PlayerResearch;
import com.game.research.ResearchCatalog;
import com.game.research.ResearchNode;
import com.game.research.ResearchService;
import com.game.research.ResearchStatus;
import com.game.skill.SkillService;
import com.game.world.CareerType;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Central aggregator for the INTEGRATION PASS: sums a character's effective bonus
 * of a given {@link BonusTypes type} across all contributing sources, so live
 * action endpoints can apply one number instead of querying every system.
 *
 * <h3>Sources and units</h3>
 * Every value summed here is in the <b>canonical unit</b> of its type:
 * <ul>
 *   <li>Multiplier types (SELL_MARGIN, BUY_DISCOUNT, YIELD, QUALITY,
 *       SHIPPING_DISCOUNT, …) are <b>fractional deltas</b> — 0.10 = +10%.</li>
 *   <li>AGING_CAP is in <b>absolute days</b>.</li>
 * </ul>
 * Contributing sources express bonuses in mixed native units, so each is
 * normalized as it is folded in:
 * <ul>
 *   <li><b>career</b> ({@link CareerProfileCatalog}) — already fractional.</li>
 *   <li><b>skills</b> ({@link SkillService#getBonuses}) — already canonical
 *       (fractional for multipliers; absolute days for AGING_CAP).</li>
 *   <li><b>buildings</b> ({@link BuildService#getBonuses}) — {@code WINE_QUALITY}
 *       (fractional) → QUALITY; {@code AGING_CAP} (days) → AGING_CAP. Other
 *       building effects (STORAGE, EXTRACTION) have no canonical multiplier and
 *       are intentionally not folded.</li>
 *   <li><b>research</b> ({@link ResearchService}) — COMPLETE nodes only:
 *       {@code YIELD_BONUS} (fractional) → YIELD; {@code SHIPPING_COST_REDUCTION}
 *       (fractional) → SHIPPING_DISCOUNT. {@code QUALITY_BONUS}/{@code AGING_QUALITY}
 *       are absolute quality <i>points</i>, not fractional multipliers, so they
 *       are deliberately excluded here.</li>
 *   <li><b>labor</b> ({@link LaborService#getBenefits}) — ACTIVE staff only:
 *       {@code YIELD} and {@code SALES} are whole-number percents, so they are
 *       divided by 100 to become fractional ({@code SALES}→SELL_MARGIN). The
 *       {@code QUALITY} staff benefit is in absolute points and is excluded.</li>
 * </ul>
 *
 * <h3>Invariant</h3>
 * A character with no career bonus, no learned skills, no buildings, no completed
 * research and no active staff totals 0.0 for every type — the basis for keeping
 * all existing tests green when a bonus is wired into a live calculation.
 */
@Service
public class BonusService {

    private final CharacterRepository characterRepository;
    private final SkillService        skillService;
    private final BuildService        buildService;
    private final ResearchService     researchService;
    private final LaborService        laborService;

    public BonusService(CharacterRepository characterRepository,
                        SkillService skillService,
                        BuildService buildService,
                        ResearchService researchService,
                        LaborService laborService) {
        this.characterRepository = characterRepository;
        this.skillService        = skillService;
        this.buildService        = buildService;
        this.researchService     = researchService;
        this.laborService        = laborService;
    }

    /** The character's total effective bonus for {@code bonusType} (all sources, canonical unit). */
    public double total(long characterId, String bonusType) {
        return careerContribution(characterId, bonusType)
             + skillContribution(characterId, bonusType)
             + buildingContribution(characterId, bonusType)
             + researchContribution(characterId, bonusType)
             + laborContribution(characterId, bonusType);
    }

    /** All canonical bonus types → the character's total for each (including 0.0). */
    public Map<String, Double> allBonuses(long characterId) {
        Map<String, Double> out = new LinkedHashMap<>();
        for (String type : BonusTypes.ALL) {
            out.put(type, total(characterId, type));
        }
        return out;
    }

    // ── sources ───────────────────────────────────────────────────────────────

    private double careerContribution(long characterId, String type) {
        CareerType career = characterRepository.findById(characterId)
                .map(Character::getCareerType)
                .orElse(null);
        if (career == null) {
            return 0.0;
        }
        CareerProfile p = CareerProfileCatalog.of(career);
        return switch (type) {
            case BonusTypes.SELL_MARGIN       -> p.sellMarginMult();
            case BonusTypes.BUY_DISCOUNT      -> p.buyDiscountMult();
            case BonusTypes.YIELD             -> p.yieldMult();
            case BonusTypes.QUALITY           -> p.qualityMult();
            case BonusTypes.SHIPPING_DISCOUNT -> p.shippingDiscountMult();
            case BonusTypes.CRAFT_DISCOUNT    -> p.craftDiscountMult();
            case BonusTypes.BROKER_COMMISSION -> p.brokerCommissionMult();
            case BonusTypes.CUTTING_MARGIN    -> p.cuttingMarginMult();
            case BonusTypes.GRADE_FEE_INCOME  -> p.gradeFeeIncomeMult();
            default                           -> 0.0;
        };
    }

    private double skillContribution(long characterId, String type) {
        return skillService.getBonuses(characterId).getOrDefault(type, 0.0);
    }

    /** Estate buildings: WINE_QUALITY→QUALITY (fractional), AGING_CAP→AGING_CAP (days). */
    private double buildingContribution(long characterId, String type) {
        Map<String, Double> b = buildService.getBonuses(characterId);
        return switch (type) {
            case BonusTypes.QUALITY   -> b.getOrDefault("WINE_QUALITY", 0.0);
            case BonusTypes.AGING_CAP -> b.getOrDefault("AGING_CAP", 0.0);
            default                   -> 0.0;
        };
    }

    /** Completed research nodes: YIELD_BONUS→YIELD, SHIPPING_COST_REDUCTION→SHIPPING_DISCOUNT. */
    private double researchContribution(long characterId, String type) {
        String nodeBonusType = switch (type) {
            case BonusTypes.YIELD             -> "YIELD_BONUS";
            case BonusTypes.SHIPPING_DISCOUNT -> "SHIPPING_COST_REDUCTION";
            default                           -> null;
        };
        if (nodeBonusType == null) {
            return 0.0;
        }
        double sum = 0.0;
        for (PlayerResearch pr : researchService.getForCharacter(characterId)) {
            if (!ResearchStatus.COMPLETE.equals(pr.getResearchStatus())) {
                continue;
            }
            ResearchNode node = ResearchCatalog.find(pr.getNodeId());
            if (node != null && nodeBonusType.equals(node.bonusType())) {
                sum += node.bonusValue();   // research nodes store fractional deltas
            }
        }
        return sum;
    }

    /** Active hired staff: YIELD%→YIELD, SALES%→SELL_MARGIN (whole-number percents → fractions). */
    private double laborContribution(long characterId, String type) {
        Map<String, Double> benefits = laborService.getBenefits(characterId);
        return switch (type) {
            case BonusTypes.YIELD       -> benefits.getOrDefault("YIELD", 0.0) / 100.0;
            case BonusTypes.SELL_MARGIN -> benefits.getOrDefault("SALES", 0.0) / 100.0;
            default                     -> 0.0;
        };
    }
}
