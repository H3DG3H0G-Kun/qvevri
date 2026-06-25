package com.game.labor;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link HiredStaff}.
 *
 * <p>Primary access patterns:
 * <ul>
 *   <li>All staff for a character (regardless of labor_status).</li>
 *   <li>Filtered by labor_status — typically "ACTIVE" for wage/benefit calculations.</li>
 * </ul>
 */
public interface HiredStaffRepository extends JpaRepository<HiredStaff, Long> {

    /**
     * Returns all staff records for the given character in any status.
     *
     * @param characterId the character whose staff to find
     * @return list (may be empty)
     */
    List<HiredStaff> findByCharacterId(Long characterId);

    /**
     * Returns staff records for the given character filtered by {@code laborStatus}.
     *
     * <p>Common call: {@code findByCharacterIdAndLaborStatus(cid, "ACTIVE")}
     *
     * @param characterId the character whose staff to find
     * @param laborStatus "ACTIVE" or "QUIT"
     * @return filtered list (may be empty)
     */
    List<HiredStaff> findByCharacterIdAndLaborStatus(Long characterId, String laborStatus);
}
