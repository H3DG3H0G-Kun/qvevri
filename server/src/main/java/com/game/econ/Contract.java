package com.game.econ;

import java.util.List;

/**
 * Skeleton record for bilateral economic contracts.
 *
 * <p>Only {@link ContractType#SPOT} contracts are exercised in Phase 2.
 * {@link ContractType#SEASONAL_SUPPLY} and {@link ContractType#WAGE} are
 * scaffolded here for Phase 3+ without further behaviour.
 *
 * <h2>Field descriptions</h2>
 * <ul>
 *   <li>{@code id} — deterministic identifier assigned by the issuing service</li>
 *   <li>{@code type} — contract category; only SPOT exercised now</li>
 *   <li>{@code partyIds} — ordered list of all parties (index 0 = initiator)</li>
 *   <li>{@code terms} — human-readable or machine-parseable term string (e.g.
 *       JSON snippet); kept opaque at this layer</li>
 *   <li>{@code status} — current lifecycle status</li>
 * </ul>
 *
 * <p>GDD Part 7/8 — Phase 2 economy, econ-core lane.
 *
 * @param id       unique contract identifier
 * @param type     the contract category
 * @param partyIds all parties; index 0 is the initiating party
 * @param terms    opaque terms string (JSON or plain text)
 * @param status   current lifecycle status
 */
public record Contract(
        String         id,
        ContractType   type,
        List<String>   partyIds,
        String         terms,
        ContractStatus status
) {

    /**
     * Return a copy of this contract with the status changed to
     * {@link ContractStatus#EXERCISED}.
     *
     * <p>Only valid for {@link ContractType#SPOT} contracts in Phase 2.
     * Calling this on other types records the status change but has no further
     * side-effect (the matching behaviour lives in the caller / Bazari).
     *
     * @return a new Contract record with status = EXERCISED
     * @throws IllegalStateException if the contract is not currently ACTIVE
     */
    public Contract exercise() {
        if (status != ContractStatus.ACTIVE) {
            throw new IllegalStateException(
                    "Cannot exercise contract '" + id + "' in status " + status);
        }
        return new Contract(id, type, partyIds, terms, ContractStatus.EXERCISED);
    }

    /**
     * Return a copy of this contract with the status changed to
     * {@link ContractStatus#ACTIVE}.
     *
     * @return a new Contract record with status = ACTIVE
     * @throws IllegalStateException if the contract is not currently PENDING
     */
    public Contract activate() {
        if (status != ContractStatus.PENDING) {
            throw new IllegalStateException(
                    "Cannot activate contract '" + id + "' in status " + status);
        }
        return new Contract(id, type, partyIds, terms, ContractStatus.ACTIVE);
    }

    /**
     * Return a copy of this contract with the status changed to
     * {@link ContractStatus#CANCELLED}.
     *
     * @return a new Contract record with status = CANCELLED
     */
    public Contract cancel() {
        return new Contract(id, type, partyIds, terms, ContractStatus.CANCELLED);
    }
}
