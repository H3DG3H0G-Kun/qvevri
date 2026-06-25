package com.game.prestige;

import java.util.List;

/**
 * Static ordered list of noble titles in the QVEVRI social arc.
 *
 * <p>The ladder mirrors the CONTENT-BIBLE progression arc (GLEKHI → ... → TAVADI),
 * with Georgian-flavoured title names and ascending prestige thresholds.
 *
 * <p>Titles and thresholds:
 * <pre>
 *   GLEKHI    (peasant)     →       0 prestige
 *   MEVENAKHE (vine-keeper) →      50 prestige
 *   MEURNE    (steward)     →     200 prestige
 *   AZNAURI   (noble)       →     600 prestige
 *   TAVADI    (prince/lord) →   1 500 prestige
 * </pre>
 *
 * <p>This class is stateless and thread-safe; it is not a Spring bean.
 */
public final class TitleLadder {

    /** An immutable record pairing a title name with its prestige threshold. */
    public record Entry(String title, long threshold) {}

    /** Ordered list of entries, ascending by threshold. */
    private static final List<Entry> ENTRIES = List.of(
            new Entry("GLEKHI",    0L),
            new Entry("MEVENAKHE", 50L),
            new Entry("MEURNE",    200L),
            new Entry("AZNAURI",   600L),
            new Entry("TAVADI",    1500L)
    );

    private TitleLadder() { /* static utility */ }

    /**
     * Returns the full title ladder in ascending threshold order.
     *
     * @return an unmodifiable ordered list of {@link Entry} records
     */
    public static List<Entry> all() {
        return ENTRIES;
    }

    /**
     * Returns the highest title whose threshold is &lt;= the given prestige.
     * Always returns a valid title (minimum: GLEKHI at threshold 0).
     *
     * @param prestige current prestige points (must be &gt;= 0)
     * @return the {@link Entry} for the current title
     */
    public static Entry titleFor(long prestige) {
        Entry current = ENTRIES.get(0);
        for (Entry entry : ENTRIES) {
            if (prestige >= entry.threshold()) {
                current = entry;
            }
        }
        return current;
    }

    /**
     * Returns the next title above the current prestige level,
     * or {@code null} if the character has already reached the top title (TAVADI).
     *
     * @param prestige current prestige points
     * @return the next {@link Entry}, or {@code null} at the top
     */
    public static Entry nextTitle(long prestige) {
        Entry current = titleFor(prestige);
        int idx = ENTRIES.indexOf(current);
        if (idx < 0 || idx >= ENTRIES.size() - 1) {
            return null;
        }
        return ENTRIES.get(idx + 1);
    }
}
