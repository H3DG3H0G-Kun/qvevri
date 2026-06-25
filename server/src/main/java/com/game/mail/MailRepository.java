package com.game.mail;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link Mail}.
 */
@Repository
public interface MailRepository extends JpaRepository<Mail, Long> {

    /**
     * All mail for a recipient character (inbox).
     */
    List<Mail> findByRecipientCharacterId(Long recipientCharacterId);

    /**
     * All unclaimed mail for a recipient — useful for dashboards.
     */
    List<Mail> findByRecipientCharacterIdAndIsClaimedFalse(Long recipientCharacterId);
}
