package com.greenwave.simulation;

import com.greenwave.trip.api.dto.TripConfigRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/comparisons")
public class ComparisonController {
    private final ComparisonService comparisons;

    public ComparisonController(ComparisonService comparisons) { this.comparisons = comparisons; }

    @PostMapping
    ResponseEntity<ComparisonSnapshot> create(@Valid @RequestBody TripConfigRequest config) {
        var snapshot = comparisons.create(config);
        return ResponseEntity.created(URI.create("/api/v1/comparisons/" + snapshot.id())).body(snapshot);
    }

    @GetMapping("/{id}")
    ComparisonSnapshot get(@PathVariable String id) { return comparisons.get(id); }

    @PostMapping("/{id}/commands")
    ComparisonSnapshot command(@PathVariable String id, @Valid @RequestBody Command request) {
        return comparisons.command(id, request);
    }

    @GetMapping("/{id}/result")
    ComparisonSnapshot result(@PathVariable String id) { return comparisons.result(id); }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> release(@PathVariable String id) {
        comparisons.release(id);
        return ResponseEntity.noContent().build();
    }

    record Command(@NotBlank @Size(max = 64) String commandId, @NotNull @Min(0) Long expectedRevision,
                   @NotNull SimulationController.CommandType type, Double playbackRate) {}
}
