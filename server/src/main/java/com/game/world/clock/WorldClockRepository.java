package com.game.world.clock;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for the single {@link WorldClock} row (id = 1).
 */
public interface WorldClockRepository extends JpaRepository<WorldClock, Long> {
}
