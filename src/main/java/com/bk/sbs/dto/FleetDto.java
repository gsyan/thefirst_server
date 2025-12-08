package com.bk.sbs.dto;

import com.bk.sbs.enums.EFormationType;
import java.time.LocalDateTime;
import java.util.List;

public class FleetDto {
    private Long id;
    private Long characterId;
    private String fleetName;
    private String description;
    private boolean isActive;
    private EFormationType formation;
    private LocalDateTime created;
    private LocalDateTime modified;
    private List<ShipDto> ships;

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getCharacterId() {
        return characterId;
    }

    public void setCharacterId(Long characterId) {
        this.characterId = characterId;
    }

    public String getFleetName() {
        return fleetName;
    }

    public void setFleetName(String fleetName) {
        this.fleetName = fleetName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
    }

    public LocalDateTime getCreated() {
        return created;
    }

    public void setCreated(LocalDateTime created) {
        this.created = created;
    }

    public LocalDateTime getModified() {
        return modified;
    }

    public void setModified(LocalDateTime modified) {
        this.modified = modified;
    }

    public List<ShipDto> getShips() {
        return ships;
    }

    public void setShips(List<ShipDto> ships) {
        this.ships = ships;
    }

    public EFormationType getFormation() {
        return formation;
    }

    public void setFormation(EFormationType formation) {
        this.formation = formation;
    }
}
