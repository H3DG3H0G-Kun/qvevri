package com.game.research;

/**
 * Immutable descriptor for a single node in the research tech tree.
 *
 * <p>Fields:
 * <ul>
 *   <li>{@code id}           — stable string key used in the tech tree and as FK in PlayerResearch.</li>
 *   <li>{@code title}        — human-readable display name.</li>
 *   <li>{@code description}  — flavour text shown to the player.</li>
 *   <li>{@code costGel}      — GEL debited from the character's wallet on start.</li>
 *   <li>{@code durationDays} — sim-days before research completes.</li>
 *   <li>{@code prereqId}     — id of another node that must be COMPLETE before this one can start,
 *                              or {@code null} for root nodes.</li>
 *   <li>{@code bonusType}    — discriminator string for the bonus this node unlocks.</li>
 *   <li>{@code bonusValue}   — magnitude of the bonus (interpretation depends on bonusType).</li>
 * </ul>
 */
public record ResearchNode(
        String id,
        String title,
        String description,
        double costGel,
        int    durationDays,
        String prereqId,      // nullable — null means no prerequisite
        String bonusType,
        double bonusValue
) {}
