package com.game.profession;

import jakarta.persistence.*;

/**
 * Tracks which characters have already received their career starter kit,
 * ensuring idempotent one-time grant.
 *
 * <p>Table: {@code profession_kit_claims}.
 */
@Entity
@Table(name = "profession_kit_claims")
public class ProfessionKitClaim {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** FK → mmo_character.id (not a JPA association; kept as a plain FK column). */
    @Column(name = "character_id", nullable = false, unique = true)
    private Long characterId;

    /** The career for which the kit was granted (for auditing). */
    @Column(name = "career_type", nullable = false)
    private String careerType;

    /** Epoch-ms timestamp of the grant. */
    @Column(name = "granted_at", nullable = false)
    private long grantedAt;

    protected ProfessionKitClaim() {}

    public ProfessionKitClaim(Long characterId, String careerType) {
        this.characterId = characterId;
        this.careerType  = careerType;
        this.grantedAt   = System.currentTimeMillis();
    }

    public Long getId()           { return id; }
    public Long getCharacterId()  { return characterId; }
    public String getCareerType() { return careerType; }
    public long getGrantedAt()    { return grantedAt; }
}
