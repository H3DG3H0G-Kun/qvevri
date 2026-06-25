package com.game.chat;

import com.game.character.Character;
import com.game.exception.ApiException;
import com.game.guild.GuildMember;
import com.game.guild.GuildMemberRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Business logic for lane CHAT.
 *
 * <p>Channel authorization model:
 * <ul>
 *   <li><b>GLOBAL</b> — any authenticated character may read and post.</li>
 *   <li><b>REGION:{REGION}</b> — the character's {@code homeRegion} must match
 *       the region in the channel key; otherwise 400 BAD_REQUEST.</li>
 *   <li><b>GUILD:{guildId}</b> — the character must be a member of the given
 *       guild (via {@link GuildMemberRepository}); otherwise 403 FORBIDDEN.</li>
 *   <li><b>DM:{a}:{b}</b> (ids sorted ascending) — the character's id must
 *       equal either {@code a} or {@code b}; otherwise 403 FORBIDDEN.</li>
 * </ul>
 *
 * <p>DM channel ids are always canonical: the two participant ids are sorted in
 * ascending order and joined with a colon. Use {@link #dmChannel(long, long)} to
 * build the key from any order.
 */
@Service
@Transactional
public class ChatService {

    /** Maximum body length in characters. */
    public static final int MAX_BODY_LENGTH = 500;

    /** Default number of messages returned when no sinceId is provided. */
    private static final int DEFAULT_FETCH_LIMIT = 50;

    private final ChatMessageRepository chatMessageRepository;
    private final GuildMemberRepository guildMemberRepository;

    public ChatService(ChatMessageRepository chatMessageRepository,
                       GuildMemberRepository guildMemberRepository) {
        this.chatMessageRepository = chatMessageRepository;
        this.guildMemberRepository = guildMemberRepository;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Sends a message to the given channel.
     *
     * <p>Validates that:
     * <ol>
     *   <li>The body is non-blank and at most 500 characters.</li>
     *   <li>The character is authorised to post to the channel.</li>
     * </ol>
     *
     * @param character  the verified sender (ownership already checked by the controller)
     * @param channel    target channel key
     * @param body       message text
     * @return the persisted {@link ChatMessage}
     * @throws ApiException 400 if body is blank or oversized, or REGION mismatch
     * @throws ApiException 403 if the character is not a member of a GUILD/DM channel
     */
    public ChatMessage send(Character character, String channel, String body) {
        validateBody(body);
        authorizePost(character, channel);

        ChatMessage msg = new ChatMessage(
                channel,
                character.getId(),
                character.getName(),
                body,
                Instant.now().toEpochMilli());
        return chatMessageRepository.save(msg);
    }

    /**
     * Returns messages for the channel.
     *
     * <p>If {@code sinceId} is provided, returns all messages with
     * {@code id > sinceId} in ascending order (polling path).
     * Otherwise returns the newest {@value #DEFAULT_FETCH_LIMIT} messages
     * in descending {@code createdAt} order.
     *
     * <p>For non-GLOBAL channels the character must be authorised to read:
     * the same membership rules as {@link #authorizePost} apply (via
     * {@link #authorizeRead}).
     *
     * @param character  the verified reader (may be null only for GLOBAL)
     * @param channel    target channel key
     * @param sinceId    exclusive lower-bound on message id, or null
     * @return messages in the appropriate order
     */
    @Transactional(readOnly = true)
    public List<ChatMessage> getMessages(Character character, String channel, Long sinceId) {
        authorizeRead(character, channel);

        if (sinceId != null) {
            return chatMessageRepository
                    .findByChannelAndIdGreaterThanOrderByIdAsc(channel, sinceId);
        }
        return chatMessageRepository
                .findByChannelOrderByCreatedAtDesc(channel,
                        PageRequest.of(0, DEFAULT_FETCH_LIMIT));
    }

    /**
     * Builds the canonical DM channel key from two character ids.
     * The ids are sorted ascending so the key is symmetric.
     *
     * @param a first character id
     * @param b second character id
     * @return canonical DM channel string, e.g. {@code "DM:3:9"}
     */
    public static String dmChannel(long a, long b) {
        long lo = Math.min(a, b);
        long hi = Math.max(a, b);
        return "DM:" + lo + ":" + hi;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void validateBody(String body) {
        if (body == null || body.isBlank()) {
            throw ApiException.badRequest("Message body must not be blank");
        }
        if (body.length() > MAX_BODY_LENGTH) {
            throw ApiException.badRequest(
                    "Message body must be at most " + MAX_BODY_LENGTH + " characters");
        }
    }

    /**
     * Authorises a send to the given channel.
     * GLOBAL: always allowed.
     * REGION:{R}: character's homeRegion must match R.
     * GUILD:{id}: character must be a member of that guild.
     * DM:{a}:{b}: character must be a or b.
     */
    private void authorizePost(Character character, String channel) {
        if ("GLOBAL".equals(channel)) {
            return; // always allowed
        }
        if (channel.startsWith("REGION:")) {
            String regionName = channel.substring("REGION:".length());
            if (!character.getHomeRegion().name().equalsIgnoreCase(regionName)) {
                throw ApiException.badRequest(
                        "Character home region is " + character.getHomeRegion()
                        + "; cannot post to channel " + channel);
            }
            return;
        }
        if (channel.startsWith("GUILD:")) {
            long guildId = parseGuildId(channel);
            checkGuildMembership(character.getId(), guildId);
            return;
        }
        if (channel.startsWith("DM:")) {
            checkDmParticipant(character.getId(), channel);
            return;
        }
        throw ApiException.badRequest("Unknown channel format: " + channel);
    }

    /**
     * Authorises a read from the given channel.
     * GLOBAL: character must be non-null (authed) but any character is allowed.
     * REGION/GUILD/DM: same membership rules as post.
     */
    private void authorizeRead(Character character, String channel) {
        if ("GLOBAL".equals(channel)) {
            if (character == null) {
                throw ApiException.unauthorized("Authentication required to read GLOBAL channel");
            }
            return;
        }
        if (character == null) {
            throw ApiException.forbidden("characterId required to read channel " + channel);
        }
        if (channel.startsWith("REGION:")) {
            String regionName = channel.substring("REGION:".length());
            if (!character.getHomeRegion().name().equalsIgnoreCase(regionName)) {
                throw ApiException.forbidden(
                        "Character home region is " + character.getHomeRegion()
                        + "; cannot read channel " + channel);
            }
            return;
        }
        if (channel.startsWith("GUILD:")) {
            long guildId = parseGuildId(channel);
            checkGuildMembership(character.getId(), guildId);
            return;
        }
        if (channel.startsWith("DM:")) {
            checkDmParticipant(character.getId(), channel);
            return;
        }
        throw ApiException.badRequest("Unknown channel format: " + channel);
    }

    private long parseGuildId(String channel) {
        try {
            return Long.parseLong(channel.substring("GUILD:".length()));
        } catch (NumberFormatException e) {
            throw ApiException.badRequest("Malformed guild channel: " + channel);
        }
    }

    private void checkGuildMembership(Long characterId, long guildId) {
        Optional<GuildMember> membership = guildMemberRepository.findByCharacterId(characterId);
        if (membership.isEmpty() || !membership.get().getGuildId().equals(guildId)) {
            throw ApiException.forbidden(
                    "Character " + characterId + " is not a member of guild " + guildId);
        }
    }

    private void checkDmParticipant(Long characterId, String channel) {
        // channel format: DM:{a}:{b} where a < b
        String[] parts = channel.split(":");
        if (parts.length != 3) {
            throw ApiException.badRequest("Malformed DM channel: " + channel);
        }
        try {
            long a = Long.parseLong(parts[1]);
            long b = Long.parseLong(parts[2]);
            long cid = characterId; // unbox once for primitive comparison
            if (cid != a && cid != b) {
                throw ApiException.forbidden(
                        "Character " + characterId + " is not a participant in " + channel);
            }
        } catch (NumberFormatException e) {
            throw ApiException.badRequest("Malformed DM channel: " + channel);
        }
    }
}
