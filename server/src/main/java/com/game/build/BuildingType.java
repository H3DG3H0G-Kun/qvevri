package com.game.build;

import java.util.List;

/**
 * Immutable descriptor for one kind of estate building.
 *
 * <p>Instances are held in {@link BuildingCatalog}; never persisted directly —
 * the live {@link Building} entity carries {@code buildingTypeId} as a string FK.
 *
 * <p>Cost summary (must be affordable within the 100 GEL starting wallet):
 * <ul>
 *   <li>COTTAGE   — 30 GEL cash + 1× cover_crop_seed (14 GEL shop price) = 44 GEL total shop cost</li>
 *   <li>MARANI    — 80 GEL cash + 2× clay_lining_compound (35 GEL each)</li>
 *   <li>PRESS_HOUSE — 60 GEL cash + 1× basket_press (320 GEL) — player must already own the press</li>
 *   <li>CELLAR    — 70 GEL cash + 1× oak_barrel_225l (520 GEL) — player must already own the barrel</li>
 * </ul>
 *
 * <p>The affordability note: COTTAGE is the "starter" building — costGel=30 and the one
 * input (cover_crop_seed qty=1 at 14 GEL) is purchasable for 44 GEL total, well under the
 * 100 GEL starting wallet. Tests use COTTAGE for the happy-path scenario.
 */
public final class BuildingType {

    private final String                  id;
    private final String                  displayName;
    private final double                  costGel;
    private final List<BuildingTypeInput> inputs;
    private final String                  bonusType;
    private final double                  bonusValue;

    public BuildingType(String id,
                        String displayName,
                        double costGel,
                        List<BuildingTypeInput> inputs,
                        String bonusType,
                        double bonusValue) {
        this.id          = id;
        this.displayName = displayName;
        this.costGel     = costGel;
        this.inputs      = List.copyOf(inputs);
        this.bonusType   = bonusType;
        this.bonusValue  = bonusValue;
    }

    public String                  getId()          { return id; }
    public String                  getDisplayName() { return displayName; }
    public double                  getCostGel()     { return costGel; }
    public List<BuildingTypeInput> getInputs()      { return inputs; }
    public String                  getBonusType()   { return bonusType; }
    public double                  getBonusValue()  { return bonusValue; }
}
