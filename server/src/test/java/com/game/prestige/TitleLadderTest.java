package com.game.prestige;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link TitleLadder} — pure logic, no Spring context needed.
 *
 * <p>Verifies:
 * <ul>
 *   <li>Ladder is ordered ascending by threshold.</li>
 *   <li>{@code titleFor} returns the correct entry at boundary and between values.</li>
 *   <li>{@code nextTitle} returns the next entry or null at the top.</li>
 * </ul>
 */
class TitleLadderTest {

    // ── all() ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("all_returnsOrderedAscending")
    void all_returnsOrderedAscending() {
        List<TitleLadder.Entry> entries = TitleLadder.all();
        assertThat(entries).isNotEmpty();

        long prev = -1L;
        for (TitleLadder.Entry e : entries) {
            assertThat(e.threshold())
                    .as("Thresholds must be strictly ascending; failed at " + e.title())
                    .isGreaterThan(prev);
            prev = e.threshold();
        }
    }

    @Test
    @DisplayName("all_containsAllExpectedTitles")
    void all_containsAllExpectedTitles() {
        List<String> titles = TitleLadder.all().stream()
                .map(TitleLadder.Entry::title)
                .toList();
        assertThat(titles).containsExactly("GLEKHI", "MEVENAKHE", "MEURNE", "AZNAURI", "TAVADI");
    }

    @Test
    @DisplayName("all_firstTitleThresholdIsZero")
    void all_firstTitleThresholdIsZero() {
        TitleLadder.Entry first = TitleLadder.all().get(0);
        assertThat(first.title()).isEqualTo("GLEKHI");
        assertThat(first.threshold()).isEqualTo(0L);
    }

    // ── titleFor() ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("titleFor_prestige0_isGlekhi")
    void titleFor_prestige0_isGlekhi() {
        assertThat(TitleLadder.titleFor(0L).title()).isEqualTo("GLEKHI");
    }

    @Test
    @DisplayName("titleFor_prestige49_isGlekhi")
    void titleFor_prestige49_isGlekhi() {
        assertThat(TitleLadder.titleFor(49L).title()).isEqualTo("GLEKHI");
    }

    @Test
    @DisplayName("titleFor_prestige50_isMevenakhe")
    void titleFor_prestige50_isMevenakhe() {
        assertThat(TitleLadder.titleFor(50L).title()).isEqualTo("MEVENAKHE");
    }

    @Test
    @DisplayName("titleFor_prestige199_isMevenakhe")
    void titleFor_prestige199_isMevenakhe() {
        assertThat(TitleLadder.titleFor(199L).title()).isEqualTo("MEVENAKHE");
    }

    @Test
    @DisplayName("titleFor_prestige200_isMeurne")
    void titleFor_prestige200_isMeurne() {
        assertThat(TitleLadder.titleFor(200L).title()).isEqualTo("MEURNE");
    }

    @Test
    @DisplayName("titleFor_prestige600_isAznauri")
    void titleFor_prestige600_isAznauri() {
        assertThat(TitleLadder.titleFor(600L).title()).isEqualTo("AZNAURI");
    }

    @Test
    @DisplayName("titleFor_prestige1500_isTavadi")
    void titleFor_prestige1500_isTavadi() {
        assertThat(TitleLadder.titleFor(1500L).title()).isEqualTo("TAVADI");
    }

    @Test
    @DisplayName("titleFor_prestige9999_isTavadi")
    void titleFor_prestige9999_isTavadi() {
        assertThat(TitleLadder.titleFor(9999L).title()).isEqualTo("TAVADI");
    }

    // ── nextTitle() ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("nextTitle_atGlekhi_isMevenakhe")
    void nextTitle_atGlekhi_isMevenakhe() {
        TitleLadder.Entry next = TitleLadder.nextTitle(0L);
        assertThat(next).isNotNull();
        assertThat(next.title()).isEqualTo("MEVENAKHE");
        assertThat(next.threshold()).isEqualTo(50L);
    }

    @Test
    @DisplayName("nextTitle_at60prestige_isMeurne")
    void nextTitle_at60prestige_isMeurne() {
        TitleLadder.Entry next = TitleLadder.nextTitle(60L);
        assertThat(next).isNotNull();
        assertThat(next.title()).isEqualTo("MEURNE");
        assertThat(next.threshold()).isEqualTo(200L);
    }

    @Test
    @DisplayName("nextTitle_atTavadi_isNull")
    void nextTitle_atTavadi_isNull() {
        assertThat(TitleLadder.nextTitle(1500L)).isNull();
        assertThat(TitleLadder.nextTitle(9999L)).isNull();
    }

    @Test
    @DisplayName("nextTitle_at600prestige_isTavadi")
    void nextTitle_at600prestige_isTavadi() {
        TitleLadder.Entry next = TitleLadder.nextTitle(600L);
        assertThat(next).isNotNull();
        assertThat(next.title()).isEqualTo("TAVADI");
        assertThat(next.threshold()).isEqualTo(1500L);
    }
}
