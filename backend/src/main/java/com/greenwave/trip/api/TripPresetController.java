package com.greenwave.trip.api;

import com.greenwave.trip.api.dto.*;
import com.greenwave.trip.service.*;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/trip-presets")
public class TripPresetController {
    private final TripPresetService service;
    private final TripPresetMapper mapper;
    public TripPresetController(TripPresetService service, TripPresetMapper mapper) {
        this.service = service; this.mapper = mapper;
    }
    @PostMapping
    public ResponseEntity<TripPresetResponse> create(@Valid @RequestBody CreateTripPresetRequest request) {
        var preset = service.create(request.name(), mapper.toDomain(request.config()));
        return ResponseEntity.created(URI.create("/api/v1/trip-presets/" + preset.id())).body(mapper.toResponse(preset));
    }
    @GetMapping
    public TripPresetPage list(@RequestParam(defaultValue="20") int limit, @RequestParam(defaultValue="0") int offset) {
        var window = service.list(limit, offset);
        return new TripPresetPage(window.items().stream().map(mapper::toResponse).toList(), window.total(), limit, offset);
    }
    @GetMapping("/{id}")
    public TripPresetResponse get(@PathVariable String id) { return mapper.toResponse(service.get(id)); }
    @PutMapping("/{id}")
    public TripPresetResponse update(@PathVariable String id, @Valid @RequestBody UpdateTripPresetRequest request) {
        return mapper.toResponse(service.update(id, request.name(), mapper.toDomain(request.config()), request.version()));
    }
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) { service.delete(id); return ResponseEntity.noContent().build(); }
}
