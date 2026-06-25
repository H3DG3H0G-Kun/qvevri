package com.game.ranking;

/**
 * Immutable record representing one entry on a leaderboard.
 *
 * <p>Used as the live read-model for all three boards (WEALTH, VINTNER, GUILD).
 *
 * @param rankPos     1-based position on the board (1 = best)
 * @param subjectId   character id (WEALTH/VINTNER) or guild id (GUILD)
 * @param subjectName character name or guild name
 * @param score       the raw score used for ordering
 *                    (walletGel for WEALTH; max quality for VINTNER;
 *                     member count for GUILD with treasury as tie-break)
 */
public record RankEntry(int rankPos, long subjectId, String subjectName, double score) {}
