package com.greenwave.trip.service;

import com.greenwave.trip.api.dto.*;
import com.greenwave.trip.domain.*;
import com.greenwave.trip.persistence.TripPresetEntity;
import java.time.Instant;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class TripPresetMapper {
    private final JsonMapper json;
    public TripPresetMapper(JsonMapper json) { this.json = json; }

    public TripConfig toDomain(TripConfigRequest dto) {
        return new TripConfig(dto.scenarioId(), dto.startNodeId(), dto.destinationNodeId(), dto.randomSeed(),
                new TripConfig.Vehicle(dto.vehicle().maxSpeedMps(), dto.vehicle().accelerationMps2(), dto.vehicle().brakingMps2()),
                new TripConfig.Navigation(dto.navigation().observationRangeM(), dto.navigation().decisionIntervalSeconds()), dto.maxDurationSeconds());
    }
    public String serialize(TripConfig config) { return json.writeValueAsString(config); }
    public TripPreset toDomain(TripPresetEntity entity) {
        return new TripPreset(entity.getId(), entity.getName(), json.readValue(entity.getConfigJson(), TripConfig.class),
                entity.getVersion()+1, Instant.parse(entity.getCreatedAt()), Instant.parse(entity.getUpdatedAt()));
    }
    public TripPresetResponse toResponse(TripPreset preset) {
        var config = preset.config();
        var dto = new TripConfigResponse(config.scenarioId(), config.startNodeId(), config.destinationNodeId(), config.randomSeed(),
                new TripConfigResponse.Vehicle(config.vehicle().maxSpeedMps(), config.vehicle().accelerationMps2(), config.vehicle().brakingMps2()),
                new TripConfigResponse.Navigation(config.navigation().observationRangeM(), config.navigation().decisionIntervalSeconds()), config.maxDurationSeconds());
        return new TripPresetResponse(preset.id(), preset.name(), dto, preset.version(), preset.createdAt(), preset.updatedAt());
    }
}
