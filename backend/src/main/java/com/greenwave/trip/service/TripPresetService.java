package com.greenwave.trip.service;

import com.greenwave.trip.domain.*;
import com.greenwave.trip.persistence.*;
import com.greenwave.scenario.persistence.ScenarioRepository;
import com.greenwave.shared.api.ApiException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class TripPresetService {
    private static final DateTimeFormatter TIMESTAMP = new DateTimeFormatterBuilder().appendInstant(9).toFormatter();
    private final TripPresetRepository repository;
    private final ScenarioRepository scenarios;
    private final TripPresetMapper mapper;
    public TripPresetService(TripPresetRepository repository, ScenarioRepository scenarios, TripPresetMapper mapper) {
        this.repository = repository; this.scenarios = scenarios; this.mapper = mapper;
    }
    public TripPreset create(String name, TripConfig config) {
        validate(config);
        var entity = new TripPresetEntity(UUID.randomUUID().toString(), normalizedName(name), config.scenarioId(),
                mapper.serialize(config), TIMESTAMP.format(Instant.now()));
        return mapper.toDomain(repository.saveAndFlush(entity));
    }
    public TripPreset update(String id, String name, TripConfig config, long version) {
        var entity = entity(id);
        if (entity.getVersion()+1 != version) throw new ApiException(409, "VERSION_CONFLICT", "This trip was changed. Reload it before saving.");
        validate(config);
        entity.update(normalizedName(name), config.scenarioId(), mapper.serialize(config), TIMESTAMP.format(Instant.now()));
        repository.flush();
        return mapper.toDomain(entity);
    }
    public void delete(String id) { repository.delete(entity(id)); repository.flush(); }
    @Transactional(readOnly=true)
    public TripPreset get(String id) { return mapper.toDomain(entity(id)); }
    @Transactional(readOnly=true)
    public Window list(int limit, int offset) {
        if (limit < 1 || limit > 100) throw ApiException.invalid("limit", "Must be between 1 and 100");
        if (offset < 0) throw ApiException.invalid("offset", "Must be nonnegative");
        return new Window(repository.findWindow(limit, offset).stream().map(mapper::toDomain).toList(), repository.count());
    }
    private TripPresetEntity entity(String id) {
        return repository.findById(id).orElseThrow(() -> new ApiException(404, "NOT_FOUND", "Saved trip not found"));
    }
    private String normalizedName(String name) {
        var trimmed = name.strip();
        if (trimmed.isEmpty() || trimmed.length() > 80) throw ApiException.invalid("name", "Use 1 to 80 characters");
        return trimmed;
    }
    private void validate(TripConfig config) {
        double interval = config.navigation().decisionIntervalSeconds();
        if (Math.abs(interval*10 - Math.rint(interval*10)) > 0.00000001) {
            throw ApiException.invalid("config.navigation.decisionIntervalSeconds", "Must be a multiple of 0.1 seconds");
        }
        var nodes = scenarios.nodes(config.scenarioId());
        if (nodes.isEmpty()) throw ApiException.invalid("config.scenarioId", "Unknown scenario");
        if (config.startNodeId().equals(config.destinationNodeId())) throw ApiException.invalid("config.destinationNodeId", "Choose a different destination");
        if (nodes.stream().noneMatch(n -> n.id().equals(config.startNodeId()))) throw ApiException.invalid("config.startNodeId", "Unknown start node");
        if (nodes.stream().noneMatch(n -> n.id().equals(config.destinationNodeId()))) throw ApiException.invalid("config.destinationNodeId", "Unknown destination node");
        if (!scenarios.reachable(config.scenarioId(), config.startNodeId(), config.destinationNodeId())) {
            throw ApiException.invalid("config.destinationNodeId", "No directed route connects these nodes");
        }
    }
    public record Window(List<TripPreset> items, long total) { }
}
