package com.greenwave.scenario.api.dto;

import java.util.List;

public record ScenarioDetailsResponse(String id, String version, String name, String networkId, String networkVersion,
                                      List<NodeResponse> nodes, List<Object> signals) {
    public record NodeResponse(String id, double lat, double lon) { }
}
