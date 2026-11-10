package com.greenwave.scenario.api;

import com.greenwave.scenario.api.dto.*;
import com.greenwave.scenario.domain.Scenario;
import com.greenwave.scenario.service.ScenarioService;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/scenarios")
public class ScenarioController {
    private final ScenarioService service;
    public ScenarioController(ScenarioService service) { this.service = service; }
    @GetMapping
    public ScenarioList list() { return new ScenarioList(service.list().stream().map(this::response).toList()); }
    @GetMapping("/{id}")
    public ScenarioDetailsResponse get(@PathVariable String id) {
        var details = service.get(id);
        var scenario = details.scenario();
        return new ScenarioDetailsResponse(scenario.id(), scenario.version(), scenario.name(), scenario.networkId(),
                scenario.networkVersion(), details.nodes().stream()
                    .map(node -> new ScenarioDetailsResponse.NodeResponse(node.id(), node.lat(), node.lon())).toList(), List.of());
    }
    private ScenarioResponse response(Scenario scenario) {
        return new ScenarioResponse(scenario.id(), scenario.version(), scenario.name(), scenario.networkId(), scenario.networkVersion());
    }
    public record ScenarioList(List<ScenarioResponse> items) { }
}
