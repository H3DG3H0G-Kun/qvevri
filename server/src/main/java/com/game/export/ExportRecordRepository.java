package com.game.export;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link ExportRecord}.
 */
@Repository
public interface ExportRecordRepository extends JpaRepository<ExportRecord, Long> {

    /**
     * Returns all export records for the given seller character, ordered by most recent first.
     *
     * @param sellerCharacterId the character whose export history to fetch
     * @return list of export records (may be empty)
     */
    List<ExportRecord> findBySellerCharacterIdOrderByCreatedAtDesc(Long sellerCharacterId);
}
