package com.game.character;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CharacterRepository extends JpaRepository<Character, Long> {

    /** Find all characters owned by the given account. */
    List<Character> findAllByAccountId(Long accountId);

    /** Find a character by id only if it belongs to the given account. */
    Optional<Character> findByIdAndAccountId(Long id, Long accountId);
}
