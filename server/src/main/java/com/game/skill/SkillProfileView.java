package com.game.skill;

import java.util.List;

/**
 * Read-only projection of a character's skill profile returned by the API.
 *
 * <p>Fields:
 * <ul>
 *   <li>{@code totalPoints}     — total skill points granted to this character.</li>
 *   <li>{@code spentPoints}     — skill points already spent on learned talents.</li>
 *   <li>{@code availablePoints} — points remaining to spend (totalPoints − spentPoints).</li>
 *   <li>{@code learned}         — list of all talents learned by this character.</li>
 * </ul>
 *
 * <p>This record is constructed by {@link SkillService} and returned directly by
 * {@link SkillController} — it is not persisted.
 */
public record SkillProfileView(
        int                totalPoints,
        int                spentPoints,
        int                availablePoints,
        List<LearnedSkill> learned
) {
    /**
     * Convenience factory: builds a view from a {@link SkillProfile} and its
     * current set of {@link LearnedSkill} rows.
     *
     * @param profile the character's skill profile
     * @param learned the list of learned talent rows
     * @return a populated SkillProfileView
     */
    public static SkillProfileView of(SkillProfile profile, List<LearnedSkill> learned) {
        return new SkillProfileView(
                profile.getTotalPoints(),
                profile.getSpentPoints(),
                profile.getTotalPoints() - profile.getSpentPoints(),
                learned);
    }
}
