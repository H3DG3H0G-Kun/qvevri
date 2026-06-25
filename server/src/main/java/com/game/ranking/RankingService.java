package com.game.ranking;

import com.game.character.Character;
import com.game.character.CharacterRepository;
import com.game.guild.Guild;
import com.game.guild.GuildMemberRepository;
import com.game.guild.GuildRepository;
import com.game.market.CellarItem;
import com.game.market.CellarItemRepository;
import com.game.world.clock.WorldClockService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Computes live leaderboards by reading (READ-ONLY) existing repositories.
 *
 * <p>Three boards:
 * <ul>
 *   <li><b>WEALTH</b> — top characters by {@code Character.walletGel} (descending).</li>
 *   <li><b>VINTNER</b> — top characters by their best {@code CellarItem.quality}
 *       (group by characterId, take max quality, sort descending).</li>
 *   <li><b>GUILD</b> — top guilds by member count (descending), tie-broken by
 *       {@code Guild.treasuryGel} (descending). Requires the guild package; if it
 *       is absent the board is empty (see note below).</li>
 * </ul>
 *
 * <p>Assumption: the guild package ({@link GuildRepository}, {@link GuildMemberRepository})
 * is present in this codebase (V14 confirmed). The GUILD board is therefore active.
 * If those beans were absent we would return an empty list; the constructor injection
 * makes the dependency explicit.
 */
@Service
@Transactional(readOnly = true)
public class RankingService {

    /** Maximum entries returned per board. */
    private static final int TOP_N = 20;

    private final CharacterRepository     characterRepository;
    private final CellarItemRepository    cellarItemRepository;
    private final GuildRepository         guildRepository;
    private final GuildMemberRepository   guildMemberRepository;
    private final RankingSnapshotRepository snapshotRepository;
    private final WorldClockService       worldClockService;

    public RankingService(CharacterRepository characterRepository,
                          CellarItemRepository cellarItemRepository,
                          GuildRepository guildRepository,
                          GuildMemberRepository guildMemberRepository,
                          RankingSnapshotRepository snapshotRepository,
                          WorldClockService worldClockService) {
        this.characterRepository  = characterRepository;
        this.cellarItemRepository = cellarItemRepository;
        this.guildRepository      = guildRepository;
        this.guildMemberRepository = guildMemberRepository;
        this.snapshotRepository   = snapshotRepository;
        this.worldClockService    = worldClockService;
    }

    // ── WEALTH board ──────────────────────────────────────────────────────────

    /**
     * Computes the live WEALTH board: top {@value #TOP_N} characters sorted by
     * {@code walletGel} descending.
     *
     * @return ordered list of rank entries (position 1 = highest wallet)
     */
    public List<RankEntry> wealthBoard() {
        List<Character> all = characterRepository.findAll();
        all.sort(Comparator.comparingDouble(Character::getWalletGel).reversed());

        List<RankEntry> result = new ArrayList<>();
        int pos = 1;
        for (Character c : all) {
            if (pos > TOP_N) break;
            result.add(new RankEntry(pos, c.getId(), c.getName(), c.getWalletGel()));
            pos++;
        }
        return result;
    }

    // ── VINTNER board ─────────────────────────────────────────────────────────

    /**
     * Computes the live VINTNER board: top {@value #TOP_N} characters by their
     * single best {@code CellarItem.quality}.
     *
     * <p>Algorithm: load all CellarItems → group by {@code characterId} → take
     * {@code max(quality)} per character → sort descending → take top N.
     *
     * @return ordered list of rank entries (position 1 = highest quality bottle)
     */
    public List<RankEntry> vintnerBoard() {
        List<CellarItem> allItems = cellarItemRepository.findAll();

        // Group by characterId, keeping only the max quality per character
        Map<Long, Double> bestByChar = new HashMap<>();
        for (CellarItem item : allItems) {
            bestByChar.merge(item.getCharacterId(), item.getQuality(), Math::max);
        }

        // Resolve character names (only for those who appear on the board)
        // Sort by quality descending, take top N
        List<Map.Entry<Long, Double>> sorted = bestByChar.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(TOP_N)
                .collect(Collectors.toList());

        // Bulk load characters for name resolution
        Set<Long> charIds = sorted.stream()
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
        Map<Long, String> nameMap = characterRepository.findAllById(charIds).stream()
                .collect(Collectors.toMap(Character::getId, Character::getName));

        List<RankEntry> result = new ArrayList<>();
        int pos = 1;
        for (Map.Entry<Long, Double> entry : sorted) {
            String name = nameMap.getOrDefault(entry.getKey(), "Unknown");
            result.add(new RankEntry(pos, entry.getKey(), name, entry.getValue()));
            pos++;
        }
        return result;
    }

    // ── GUILD board ───────────────────────────────────────────────────────────

    /**
     * Computes the live GUILD board: top {@value #TOP_N} guilds by member count
     * (descending), tie-broken by {@code Guild.treasuryGel} (descending).
     *
     * <p>The score field carries member count as a double for uniformity; treasury
     * is used only as a sort tie-breaker and is not exposed in the score field
     * separately. If the guild package is absent (no beans), this returns an
     * empty list — but the guild package IS present (V14 confirmed).
     *
     * @return ordered list of rank entries (position 1 = most members)
     */
    public List<RankEntry> guildBoard() {
        List<Guild> allGuilds = guildRepository.findAll();
        if (allGuilds.isEmpty()) {
            return Collections.emptyList();
        }

        // Compute member count per guild
        Map<Long, Long> memberCounts = new HashMap<>();
        for (Guild g : allGuilds) {
            long count = guildMemberRepository.findByGuildId(g.getId()).size();
            memberCounts.put(g.getId(), count);
        }

        // Sort: primary = member count desc; tie-break = treasuryGel desc
        allGuilds.sort(Comparator
                .<Guild, Long>comparing(g -> memberCounts.getOrDefault(g.getId(), 0L),
                        Comparator.reverseOrder())
                .thenComparingDouble(g -> -g.getTreasuryGel()));

        List<RankEntry> result = new ArrayList<>();
        int pos = 1;
        for (Guild g : allGuilds) {
            if (pos > TOP_N) break;
            double score = memberCounts.getOrDefault(g.getId(), 0L).doubleValue();
            result.add(new RankEntry(pos, g.getId(), g.getName(), score));
            pos++;
        }
        return result;
    }

    // ── Me-rank ───────────────────────────────────────────────────────────────

    /**
     * Returns the rank entry for the given subject on the given board,
     * or {@code Optional.empty()} if not on the board (e.g. no cellar items).
     *
     * @param board       "wealth" or "vintner" (guild not supported for /me)
     * @param characterId the character to look up
     * @return the entry if present, empty otherwise
     */
    public Optional<RankEntry> meRank(String board, long characterId) {
        // Rank against the FULL ordered list, not the truncated top-N board — a
        // character's true position must be reported even if they're past TOP_N.
        return switch (board.toLowerCase()) {
            case "wealth"  -> fullWealthRank(characterId);
            case "vintner" -> fullVintnerRank(characterId);
            default        -> Optional.empty();
        };
    }

    /** True 1-based position of a character on the full wealth ranking, if present. */
    private Optional<RankEntry> fullWealthRank(long characterId) {
        List<Character> all = characterRepository.findAll();
        all.sort(Comparator.comparingDouble(Character::getWalletGel).reversed());
        int pos = 1;
        for (Character c : all) {
            if (c.getId() == characterId) {
                return Optional.of(new RankEntry(pos, c.getId(), c.getName(), c.getWalletGel()));
            }
            pos++;
        }
        return Optional.empty();
    }

    /** True 1-based position on the full vintner ranking; empty if the character owns no bottles. */
    private Optional<RankEntry> fullVintnerRank(long characterId) {
        List<CellarItem> allItems = cellarItemRepository.findAll();
        Map<Long, Double> bestByChar = new HashMap<>();
        for (CellarItem item : allItems) {
            bestByChar.merge(item.getCharacterId(), item.getQuality(), Math::max);
        }
        List<Map.Entry<Long, Double>> sorted = bestByChar.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .collect(Collectors.toList());
        int pos = 1;
        for (Map.Entry<Long, Double> e : sorted) {
            if (e.getKey() == characterId) {
                String name = characterRepository.findById(characterId)
                        .map(Character::getName).orElse("Unknown");
                return Optional.of(new RankEntry(pos, characterId, name, e.getValue()));
            }
            pos++;
        }
        return Optional.empty();
    }

    // ── Snapshot ──────────────────────────────────────────────────────────────

    /**
     * Computes the current board and persists each entry as a {@link RankingSnapshot}.
     * Returns the persisted rows.
     *
     * @param board one of "wealth", "vintner", "guild" (case-insensitive)
     * @return the list of persisted snapshot rows, ordered by rankPos
     */
    @Transactional
    public List<RankingSnapshot> snapshot(String board) {
        List<RankEntry> entries = switch (board.toLowerCase()) {
            case "wealth"  -> wealthBoard();
            case "vintner" -> vintnerBoard();
            case "guild"   -> guildBoard();
            default        -> throw new IllegalArgumentException("Unknown board: " + board);
        };

        long simDay    = worldClockService.currentAbsoluteDay();
        long now       = System.currentTimeMillis();
        String boardKey = board.toUpperCase();

        List<RankingSnapshot> rows = entries.stream()
                .map(e -> new RankingSnapshot(
                        boardKey,
                        e.subjectId(),
                        e.subjectName(),
                        e.score(),
                        e.rankPos(),
                        simDay,
                        now))
                .collect(Collectors.toList());

        return snapshotRepository.saveAll(rows);
    }

    // ── Snapshot read ─────────────────────────────────────────────────────────

    /**
     * Returns all persisted snapshot rows for the given board, ordered by rankPos.
     *
     * @param board e.g. "WEALTH"
     * @return list ordered by rankPos asc
     */
    public List<RankingSnapshot> getSnapshots(String board) {
        return snapshotRepository.findByBoardOrderByRankPosAsc(board.toUpperCase());
    }
}
