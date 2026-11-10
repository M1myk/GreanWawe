package com.greenwave.scenario.service;

import com.greenwave.scenario.domain.*;
import com.greenwave.scenario.persistence.ScenarioRepository;
import com.greenwave.shared.api.ApiException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly=true)
public class ScenarioService {
    private final ScenarioRepository repository;
    public ScenarioService(ScenarioRepository repository) { this.repository = repository; }
    public List<Scenario> list() { return repository.findAll(); }
    public Details get(String id) {
        var scenario = list().stream().filter(s -> s.id().equals(id)).findFirst()
                .orElseThrow(() -> new ApiException(404, "NOT_FOUND", "Scenario not found"));
        return new Details(scenario, repository.nodes(id));
    }
    public record Details(Scenario scenario, List<ScenarioNode> nodes) { }
}
