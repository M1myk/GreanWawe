package com.greenwave.simulation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/simulations")
public class SimulationController {
    private final SimulationService simulations;

    public SimulationController(SimulationService simulations) {
        this.simulations = simulations;
    }

    @PostMapping
    ResponseEntity<BaselineSimulation.Snapshot> create(@RequestBody @Valid Create request) {
        var snapshot = simulations.create(request);
        return ResponseEntity.created(URI.create("/api/v1/simulations/" + snapshot.id())).body(snapshot);
    }

    @GetMapping("/{id}")
    BaselineSimulation.Snapshot get(@PathVariable String id) {
        return simulations.get(id);
    }

    @PostMapping("/{id}/commands")
    BaselineSimulation.Snapshot command(@PathVariable String id, @RequestBody @Valid Command request) {
        return simulations.command(id, request);
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@PathVariable String id) {
        simulations.delete(id);
        return ResponseEntity.noContent().build();
    }

    record Create(@NotBlank String networkId, @NotBlank String startNodeId, @NotBlank String destinationNodeId,
                  @NotNull @Min(0) @Max(Integer.MAX_VALUE) Long randomSeed) {}
    enum CommandType { START, PAUSE, RESUME, RESET, SET_PLAYBACK_RATE }
    record Command(@NotBlank @Size(max = 64) String commandId, @NotNull CommandType type, Double playbackRate) {}
}
