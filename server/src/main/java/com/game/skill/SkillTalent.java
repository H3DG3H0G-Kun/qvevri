package com.game.skill;

/**
 * Immutable descriptor for a single talent in the skill tree.
 *
 * <p>Fields:
 * <ul>
 *   <li>{@code id}          — stable string key (never rename once data is live).</li>
 *   <li>{@code title}       — human-readable display name.</li>
 *   <li>{@code description} — flavour text shown to the player.</li>
 *   <li>{@code cost}        — skill points required to learn this talent.</li>
 *   <li>{@code prereqId}    — id of another talent that must be learned first,
 *                             or {@code null} for root talents.</li>
 *   <li>{@code bonusType}   — discriminator string for the bonus this talent unlocks.</li>
 *   <li>{@code bonusValue}  — magnitude of the bonus (interpretation depends on bonusType).</li>
 * </ul>
 */
public record SkillTalent(
        String id,
        String title,
        String description,
        int    cost,
        String prereqId,     // nullable — null means no prerequisite
        String bonusType,
        double bonusValue
) {}
