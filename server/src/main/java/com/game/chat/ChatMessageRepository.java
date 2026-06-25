package com.game.chat;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Spring Data repository for {@link ChatMessage} entities.
 *
 * <p>Two query patterns are exposed:
 * <ol>
 *   <li>Newest-N fetch — returns the most recent messages in a channel, ordered
 *       by {@code createdAt} descending (newest first). The controller reverses
 *       the list before sending to the client so older messages come first in the
 *       response array, which is the natural display order.</li>
 *   <li>Since-ID polling — returns all messages whose id is strictly greater than
 *       {@code sinceId}, ordered ascending. This powers efficient long-poll / REST-
 *       poll clients without re-reading messages already seen.</li>
 * </ol>
 */
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    /**
     * Returns the most recent messages in the channel, newest first.
     * Pair with a {@code Pageable.ofSize(N)} to cap the result set.
     *
     * @param channel  the channel key
     * @param pageable page/limit descriptor
     * @return messages ordered by {@code createdAt} descending
     */
    List<ChatMessage> findByChannelOrderByCreatedAtDesc(String channel, Pageable pageable);

    /**
     * Polling variant: returns all messages in the channel whose id is strictly
     * greater than {@code sinceId}, ordered ascending (oldest-of-new first).
     *
     * @param channel channel key
     * @param sinceId exclusive lower bound on message id
     * @return messages with {@code id > sinceId}, ascending
     */
    List<ChatMessage> findByChannelAndIdGreaterThanOrderByIdAsc(String channel, Long sinceId);
}
