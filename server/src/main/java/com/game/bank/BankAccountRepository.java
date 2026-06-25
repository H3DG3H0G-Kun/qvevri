package com.game.bank;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link BankAccount}.
 *
 * <p>Primary access pattern: look up a character's single savings account.
 */
public interface BankAccountRepository extends JpaRepository<BankAccount, Long> {

    /**
     * Finds the savings account for the given character, or empty if the account
     * has not yet been lazy-created.
     *
     * @param characterId the character whose account to find
     * @return the account, or Optional.empty()
     */
    Optional<BankAccount> findByCharacterId(Long characterId);
}
