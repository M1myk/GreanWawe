package com.greenwave.trip.persistence;

import jakarta.persistence.*;

@Entity
@Table(name="trip_preset")
public class TripPresetEntity {
    @Id private String id;
    @Column(nullable=false) private String name;
    @Column(name="scenario_id", nullable=false) private String scenarioId;
    @Column(name="config_json", nullable=false) private String configJson;
    @Version private Long version;
    @Column(name="created_at", nullable=false) private String createdAt;
    @Column(name="updated_at", nullable=false) private String updatedAt;

    protected TripPresetEntity() { }

    public TripPresetEntity(String id, String name, String scenarioId, String configJson, String timestamp) {
        this.id = id;
        this.createdAt = timestamp;
        update(name, scenarioId, configJson, timestamp);
    }

    public void update(String name, String scenarioId, String configJson, String timestamp) {
        this.name = name;
        this.scenarioId = scenarioId;
        this.configJson = configJson;
        this.updatedAt = timestamp;
    }
    public String getId() { return id; }
    public String getName() { return name; }
    public String getScenarioId() { return scenarioId; }
    public String getConfigJson() { return configJson; }
    public Long getVersion() { return version; }
    public String getCreatedAt() { return createdAt; }
    public String getUpdatedAt() { return updatedAt; }
}
