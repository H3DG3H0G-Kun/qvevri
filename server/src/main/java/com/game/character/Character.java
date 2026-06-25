package com.game.character;

import com.game.world.CareerType;
import com.game.world.Rank;
import com.game.world.Region;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Persistent MMO character. Fields are the contract per MMO-CORE-SPEC Section 2.
 *
 * <p>Each character belongs to one Account (via accountId FK), has a career,
 * a home region, a progression rank and a wallet in Georgian Lari (GEL).
 * New characters start at rank GLEKHI with 100.0 GEL.
 */
@Entity
@Table(name = "mmo_character")
public class Character {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long accountId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CareerType careerType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Region homeRegion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Rank rank = Rank.GLEKHI;

    /** Wallet balance in GEL. New characters start with 100.0 GEL. */
    @Column(nullable = false)
    private double walletGel = 100.0;

    @Column(nullable = false)
    private long createdAt;

    /** Required by JPA. */
    protected Character() {}

    public Character(Long accountId, String name, CareerType careerType,
                     Region homeRegion, long createdAt) {
        this.accountId = accountId;
        this.name = name;
        this.careerType = careerType;
        this.homeRegion = homeRegion;
        this.rank = Rank.GLEKHI;
        this.walletGel = 100.0;
        this.createdAt = createdAt;
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    public Long getId()            { return id; }
    public Long getAccountId()     { return accountId; }
    public String getName()        { return name; }
    public CareerType getCareerType() { return careerType; }
    public Region getHomeRegion()  { return homeRegion; }
    public Rank getRank()          { return rank; }
    public double getWalletGel()   { return walletGel; }
    public long getCreatedAt()     { return createdAt; }

    // ── Setters used by CharacterService / MarketService ──────────────────────

    public void setWalletGel(double walletGel) { this.walletGel = walletGel; }
}
