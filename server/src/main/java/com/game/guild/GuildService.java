package com.game.guild;

import com.game.character.CharacterService;
import com.game.exception.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Business logic for the LANE GUILDS feature (wine-house cooperatives).
 *
 * <p>All monetary mutations delegate to {@link CharacterService#adjustWallet}
 * which enforces the invariant that a character's wallet never goes below zero.
 * Treasury mutations are performed directly on the {@link Guild} entity within
 * the same transaction so the two balance changes are always atomic.
 *
 * <p>Role constants: {@code "FOUNDER"} and {@code "MEMBER"}.
 */
@Service
public class GuildService {

    static final String ROLE_FOUNDER = "FOUNDER";
    static final String ROLE_MEMBER  = "MEMBER";

    private final GuildRepository       guildRepository;
    private final GuildMemberRepository memberRepository;
    private final CharacterService      characterService;

    public GuildService(GuildRepository guildRepository,
                        GuildMemberRepository memberRepository,
                        CharacterService characterService) {
        this.guildRepository  = guildRepository;
        this.memberRepository = memberRepository;
        this.characterService = characterService;
    }

    // ── Create ────────────────────────────────────────────────────────────────

    /**
     * Creates a new guild with the given name and records the creating character
     * as its FOUNDER.
     *
     * @param characterId the character who is founding the guild (must be owned by caller)
     * @param name        the desired guild name (must be unique)
     * @return the newly persisted {@link Guild}
     * @throws ApiException 400 if the name is already taken
     * @throws ApiException 400 if the character is already a member of any guild
     */
    @Transactional
    public Guild createGuild(Long characterId, String name) {
        // Guard: name uniqueness
        if (guildRepository.findByName(name).isPresent()) {
            throw ApiException.badRequest("Guild name already taken: " + name);
        }
        // Guard: character not already in a guild
        if (memberRepository.findByCharacterId(characterId).isPresent()) {
            throw ApiException.badRequest(
                    "Character " + characterId + " is already a member of a guild");
        }

        long now = System.currentTimeMillis();
        Guild guild = guildRepository.save(new Guild(name, characterId, now));

        GuildMember founder = new GuildMember(guild.getId(), characterId, ROLE_FOUNDER, now);
        memberRepository.save(founder);

        return guild;
    }

    // ── Join ──────────────────────────────────────────────────────────────────

    /**
     * Adds the character as a MEMBER of the given guild.
     *
     * @param guildId     the guild to join
     * @param characterId the joining character
     * @return the new {@link GuildMember} record
     * @throws ApiException 404 if the guild does not exist
     * @throws ApiException 400 if the character is already a member of any guild
     */
    @Transactional
    public GuildMember joinGuild(Long guildId, Long characterId) {
        Guild guild = guildRepository.findById(guildId)
                .orElseThrow(() -> ApiException.notFound("Guild not found: " + guildId));

        if (memberRepository.findByCharacterId(characterId).isPresent()) {
            throw ApiException.badRequest(
                    "Character " + characterId + " is already a member of a guild");
        }

        long now = System.currentTimeMillis();
        GuildMember member = new GuildMember(guild.getId(), characterId, ROLE_MEMBER, now);
        return memberRepository.save(member);
    }

    // ── Leave ─────────────────────────────────────────────────────────────────

    /**
     * Removes the character's membership from their current guild.
     *
     * <p>A FOUNDER may only leave if they are the last remaining member.
     * If other members still exist, the founder must transfer leadership or
     * disband the guild first (v1 rule: 400 in that case).
     *
     * @param guildId     the guild to leave
     * @param characterId the character leaving
     * @throws ApiException 400 if the character is not a member of this guild
     * @throws ApiException 400 if the character is the FOUNDER and other members remain
     */
    @Transactional
    public void leaveGuild(Long guildId, Long characterId) {
        GuildMember membership = memberRepository.findByCharacterId(characterId)
                .filter(m -> m.getGuildId().equals(guildId))
                .orElseThrow(() -> ApiException.badRequest(
                        "Character " + characterId + " is not a member of guild " + guildId));

        if (ROLE_FOUNDER.equals(membership.getGuildRole())) {
            List<GuildMember> allMembers = memberRepository.findByGuildId(guildId);
            if (allMembers.size() > 1) {
                throw ApiException.badRequest(
                        "Founder cannot leave while other members remain; "
                        + "transfer leadership or disband the guild first");
            }
        }

        memberRepository.delete(membership);
    }

    // ── Get guild view ────────────────────────────────────────────────────────

    /**
     * Returns the guild plus its full member list.
     *
     * @param guildId the guild to fetch
     * @return a {@link GuildView} combining the guild and its members
     * @throws ApiException 404 if the guild does not exist
     */
    @Transactional(readOnly = true)
    public GuildView getGuild(Long guildId) {
        Guild guild = guildRepository.findById(guildId)
                .orElseThrow(() -> ApiException.notFound("Guild not found: " + guildId));
        List<GuildMember> members = memberRepository.findByGuildId(guildId);
        return new GuildView(guild, members);
    }

    // ── Deposit ───────────────────────────────────────────────────────────────

    /**
     * Moves {@code amountGel} from the character's wallet into the guild treasury.
     *
     * <p>The character must be a member of the guild. The wallet debit is
     * delegated to {@link CharacterService#adjustWallet} which enforces the
     * non-negative wallet invariant.
     *
     * @param guildId     the destination guild
     * @param characterId the depositing character
     * @param amountGel   positive GEL amount to deposit
     * @return the updated {@link Guild} (with new treasury balance)
     * @throws ApiException 400 if amountGel &lt;= 0
     * @throws ApiException 400/403 if the character is not a member of this guild
     * @throws ApiException 402 if the character's wallet is insufficient
     */
    @Transactional
    public Guild deposit(Long guildId, Long characterId, double amountGel) {
        if (amountGel <= 0.0) {
            throw ApiException.badRequest("Deposit amount must be positive");
        }

        Guild guild = guildRepository.findById(guildId)
                .orElseThrow(() -> ApiException.notFound("Guild not found: " + guildId));

        // Verify membership
        memberRepository.findByCharacterId(characterId)
                .filter(m -> m.getGuildId().equals(guildId))
                .orElseThrow(() -> ApiException.forbidden(
                        "Character " + characterId + " is not a member of guild " + guildId));

        // Debit wallet (throws 402 if insufficient)
        characterService.adjustWallet(characterId, -amountGel);

        // Credit treasury
        guild.setTreasuryGel(guild.getTreasuryGel() + amountGel);
        return guildRepository.save(guild);
    }

    // ── Withdraw ──────────────────────────────────────────────────────────────

    /**
     * Moves {@code amountGel} from the guild treasury into the character's wallet.
     * Only the guild FOUNDER may withdraw.
     *
     * @param guildId     the source guild
     * @param characterId the withdrawing character (must be FOUNDER)
     * @param amountGel   positive GEL amount to withdraw
     * @return the updated {@link Guild} (with new treasury balance)
     * @throws ApiException 400 if amountGel &lt;= 0
     * @throws ApiException 400 if the character is not the FOUNDER
     * @throws ApiException 400 if the treasury has insufficient funds
     */
    @Transactional
    public Guild withdraw(Long guildId, Long characterId, double amountGel) {
        if (amountGel <= 0.0) {
            throw ApiException.badRequest("Withdrawal amount must be positive");
        }

        Guild guild = guildRepository.findById(guildId)
                .orElseThrow(() -> ApiException.notFound("Guild not found: " + guildId));

        // Verify FOUNDER role
        GuildMember membership = memberRepository.findByCharacterId(characterId)
                .filter(m -> m.getGuildId().equals(guildId))
                .orElseThrow(() -> ApiException.badRequest(
                        "Character " + characterId + " is not a member of guild " + guildId));

        if (!ROLE_FOUNDER.equals(membership.getGuildRole())) {
            throw ApiException.badRequest(
                    "Only the guild FOUNDER may withdraw from the treasury");
        }

        // Guard treasury balance
        if (guild.getTreasuryGel() < amountGel) {
            throw ApiException.badRequest(
                    "Insufficient treasury: " + guild.getTreasuryGel()
                    + " GEL available, need " + amountGel + " GEL");
        }

        // Debit treasury
        guild.setTreasuryGel(guild.getTreasuryGel() - amountGel);
        guildRepository.save(guild);

        // Credit wallet
        characterService.adjustWallet(characterId, amountGel);

        return guild;
    }
}
