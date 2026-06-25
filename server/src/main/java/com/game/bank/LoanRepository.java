package com.game.bank;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link Loan}.
 *
 * <p>Access patterns:
 * <ul>
 *   <li>Find the single OPEN loan for a character (to guard against double-loan).</li>
 *   <li>Find all loans for a character (for the status view).</li>
 * </ul>
 */
public interface LoanRepository extends JpaRepository<Loan, Long> {

    /**
     * Finds the single loan in the given status for the character.
     *
     * <p>Used primarily to check for an existing OPEN loan before issuing a new one,
     * and to locate the OPEN loan during repayment.
     *
     * @param characterId the character whose loan to find
     * @param loanStatus  the status string, e.g. {@code "OPEN"} or {@code "REPAID"}
     * @return the matching loan, or Optional.empty() if none
     */
    Optional<Loan> findByCharacterIdAndLoanStatus(Long characterId, String loanStatus);

    /**
     * Returns all loans for the given character (any status), ordered by id.
     *
     * @param characterId the character whose loans to list
     * @return list of loans, may be empty
     */
    List<Loan> findByCharacterId(Long characterId);
}
