package com.game.profession;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link WineGrade}.
 */
@Repository
public interface WineGradeRepository extends JpaRepository<WineGrade, Long> {

    /** Returns all grades issued for a given cellar item (normally 0 or 1). */
    List<WineGrade> findByCellarItemId(Long cellarItemId);
}
