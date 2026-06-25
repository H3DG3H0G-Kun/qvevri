package com.game.estate;

import jakarta.persistence.*;

/**
 * A single dated tending action recorded against a vineyard for a particular
 * world-clock year.
 *
 * <p>During deterministic replay (GOODS-ECON-SPEC §LANE-M), actions for
 * {@code (vineyardId, year)} are applied causally: an action on
 * {@code dayOfYear} overrides/boosts the relevant lever for all days
 * {@code >= dayOfYear} in that season. An empty action list produces output
 * identical to a plan-only replay.
 *
 * <p>Table: {@code vineyard_actions}
 */
@Entity
@Table(name = "vineyard_actions",
       indexes = @Index(name = "idx_vineyard_actions_vineyard_year",
                        columnList = "vineyard_id, season_year"))
public class VineyardAction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** FK → Vineyard.id (denormalised for query performance). */
    @Column(name = "vineyard_id", nullable = false)
    private Long vineyardId;

    /** World-clock year this action was applied in. */
    @Column(name = "season_year", nullable = false)
    private int year;

    /**
     * Day within the year on which the action takes effect (0..364).
     * The action is causal: it only affects days >= this value.
     */
    @Column(nullable = false)
    private int dayOfYear;

    /**
     * Type of tending action.
     * Supported values (see GOODS-ECON-SPEC LANE-M):
     * {@code EMERGENCY_COPPER_SPRAY}, {@code EMERGENCY_SULFUR_SPRAY},
     * {@code EMERGENCY_NETTING}.
     */
    @Column(nullable = false)
    private String actionType;

    /**
     * Numeric value associated with the action (semantics depend on
     * {@code actionType}). For spray actions: intensity 0..1. For netting: 1.0
     * = enable, 0.0 = disable.
     */
    @Column(name = "action_value", nullable = false)
    private double value;

    // ── Constructors ──────────────────────────────────────────────────────────

    protected VineyardAction() {}

    public VineyardAction(Long vineyardId, int year, int dayOfYear,
                          String actionType, double value) {
        this.vineyardId = vineyardId;
        this.year       = year;
        this.dayOfYear  = dayOfYear;
        this.actionType = actionType;
        this.value      = value;
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public Long   getId()         { return id; }
    public Long   getVineyardId() { return vineyardId; }
    public int    getYear()       { return year; }
    public int    getDayOfYear()  { return dayOfYear; }
    public String getActionType() { return actionType; }
    public double getValue()      { return value; }
}
