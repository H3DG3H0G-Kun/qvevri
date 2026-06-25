package com.game.core.data;

/**
 * Grape varieties supported by the sim.  Each variety maps to a
 * {@link VarietyProfile} via {@link VarietyProfiles#of(Variety)}.
 *
 * <p>Color is carried in the profile; use {@link #isWhite()} for a quick
 * compile-time-safe check.
 */
public enum Variety {
    // ── Red varieties ─────────────────────────────────────────────────────────
    /** Saperavi — late-ripening, thick-skinned, naturally high-acid Georgian red.
     *  Phase-0 baseline; all sim constants were originally calibrated to this variety. */
    SAPERAVI,
    /** Aleksandrouli — thin-skinned red from Racha; semi-sweet style friendly. */
    ALEKSANDROULI,
    /** Ojaleshi — aromatic red from Samegrelo; high residual-sugar potential. */
    OJALESHI,
    /** Chkhaveri — rosé-capable light red / rosé from Guria. */
    CHKHAVERI,

    // ── White varieties ───────────────────────────────────────────────────────
    /** Rkatsiteli — workhorse Georgian white; high acid, late ripening. */
    RKATSITELI,
    /** Mtsvane — aromatic white; citrus and floral; moderate acid. */
    MTSVANE,
    /** Kisi — aromatic white; stone-fruit and floral; moderate-high acid. */
    KISI,
    /** Tsolikouri — late, high-acid Imeretian white. */
    TSOLIKOURI,
    /** Tsitska — late, high-acid Imeretian white; naturally fine-bubble potential. */
    TSITSKA,
    /** Chinuri — light, high-acid white from Kartli. */
    CHINURI;

    /**
     * Returns {@code true} if this variety produces a white (or rosé-eligible)
     * wine style in the default fermentation path.
     *
     * <p>Chkhaveri is classified as white here because its default style in the
     * sim is a light-skin-contact rosé/white, not a classic red.
     */
    public boolean isWhite() {
        return switch (this) {
            case RKATSITELI, MTSVANE, KISI, TSOLIKOURI, TSITSKA, CHINURI, CHKHAVERI -> true;
            default -> false;
        };
    }
}
