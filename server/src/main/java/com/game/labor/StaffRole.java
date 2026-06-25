package com.game.labor;

/**
 * Immutable value object describing one NPC staff role in the static catalog.
 *
 * <p>Column naming in this record avoids H2 reserved words.
 * {@code benefitVal} avoids {@code value}; {@code hireCostGel} and
 * {@code dailyWageGel} are explicit.
 *
 * <p>Instances are created only by {@link StaffCatalog}; use
 * {@link StaffCatalog#all()} / {@link StaffCatalog#find(String)} to access them.
 *
 * @param id            stable string id (e.g. "vineyard_hand") — never rename in production
 * @param title         human-readable display name
 * @param hireCostGel   one-time upfront hire cost in GEL (debited from wallet on hire)
 * @param dailyWageGel  GEL accrued per sim-day while ACTIVE (paid via payroll)
 * @param benefitType   category string (e.g. "YIELD", "QUALITY", "CRAFT", "SALES")
 * @param benefitVal    numeric bonus magnitude for the benefit (summed in /benefits endpoint)
 */
public record StaffRole(
        String id,
        String title,
        double hireCostGel,
        double dailyWageGel,
        String benefitType,
        double benefitVal
) {}
