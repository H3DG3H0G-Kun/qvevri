package com.game.land;

/**
 * Request body for POST /api/land/parcels — claim/buy a parcel.
 */
public class BuyParcelRequest {

    private Long   characterId;
    private String region;
    private String name;
    private Double sizeHectares;

    public BuyParcelRequest() {}

    public Long   getCharacterId()  { return characterId; }
    public String getRegion()       { return region; }
    public String getName()         { return name; }
    public Double getSizeHectares() { return sizeHectares; }

    public void setCharacterId(Long characterId)    { this.characterId = characterId; }
    public void setRegion(String region)            { this.region = region; }
    public void setName(String name)                { this.name = name; }
    public void setSizeHectares(Double sizeHectares){ this.sizeHectares = sizeHectares; }
}
