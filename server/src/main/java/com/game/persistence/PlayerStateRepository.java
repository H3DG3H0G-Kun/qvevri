package com.game.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PlayerStateRepository extends JpaRepository<PlayerStateEntity, PlayerStateId> {

    List<PlayerStateEntity> findBySessionId(String sessionId);
}
