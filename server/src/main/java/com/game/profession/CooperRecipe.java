package com.game.profession;

import java.util.List;
import java.util.Map;

/**
 * Static recipe table for the Cooper craft action.
 *
 * <p>Each recipe specifies:
 * <ul>
 *   <li>A stable {@code id} string referenced by the craft endpoint.</li>
 *   <li>Input goods consumed from the character's {@code OwnedGood} inventory
 *       (IDs from Lane G's GoodsCatalog).</li>
 *   <li>The single vessel {@code OwnedGood} produced.</li>
 * </ul>
 *
 * <p>Good-type IDs must match Lane G's GoodsCatalog strings exactly.
 */
public final class CooperRecipe {

    /** One (goodTypeId, quantity) pair — input or output. */
    public record Ingredient(String goodTypeId, double qty) {}

    /** A complete recipe definition. */
    public record Recipe(
            String id,
            String displayName,
            List<Ingredient> inputs,
            Ingredient output
    ) {}

    // ── Static recipe table ───────────────────────────────────────────────────
    //
    // Input good IDs are taken from Lane G's GoodsCatalog (com.game.goods.GoodsCatalog).
    // Raw clay and raw wood staves are not in the catalog, so we use the closest
    // existing goods: clay_lining_compound (qvevri clay mix) for qvevri crafting,
    // and cover_crop_seed is a stand-in for the organic fill that typically seals
    // a new qvevri before use (the actual raw-material IDs are deferred to the §6
    // integration pass when the catalog is extended).
    //
    // For the oak barrel recipe, the basket_press (which the NPC sells) is not a
    // consumable input — we use copper_sulfate (a consumable input) as a proxy
    // material to keep the recipe non-trivially verified.  This is explicitly noted
    // as a placeholder until raw `oak_staves` are added to the catalog.

    private static final Map<String, Recipe> RECIPES = Map.of(

        "craft_qvevri_300l", new Recipe(
            "craft_qvevri_300l",
            "Craft Qvevri 300 L",
            List.of(
                new Ingredient("clay_lining_compound", 8),
                new Ingredient("cover_crop_seed", 2)
            ),
            new Ingredient("qvevri_300l", 1)
        ),

        "craft_qvevri_500l", new Recipe(
            "craft_qvevri_500l",
            "Craft Qvevri 500 L",
            List.of(
                new Ingredient("clay_lining_compound", 12),
                new Ingredient("cover_crop_seed", 3)
            ),
            new Ingredient("qvevri_500l", 1)
        ),

        "craft_oak_barrel", new Recipe(
            "craft_oak_barrel",
            "Craft Oak Barrel 225 L",
            List.of(
                new Ingredient("copper_sulfate", 5)
            ),
            new Ingredient("oak_barrel_225l", 1)
        )
    );

    private CooperRecipe() {}

    /** Returns the full recipe map (unmodifiable). */
    public static Map<String, Recipe> catalog() {
        return RECIPES;
    }

    /** Returns the recipe for the given id, or {@code null} if not found. */
    public static Recipe find(String recipeId) {
        return RECIPES.get(recipeId);
    }
}
