package com.greenwave.scenario.persistence;

import com.greenwave.scenario.domain.Scenario;
import com.greenwave.scenario.domain.ScenarioNode;

import java.util.List;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

@Repository
public class ScenarioRepository {
    private final EntityManager entityManager;
    public ScenarioRepository(EntityManager entityManager) { this.entityManager = entityManager; }

    @SuppressWarnings("unchecked")
    public List<Scenario> findAll() {
        List<Object[]> rows = entityManager.createNativeQuery("SELECT s.id, s.version, s.name, s.network_id, n.version FROM scenario s JOIN road_network n ON n.id=s.network_id ORDER BY s.id").getResultList();
        return rows.stream().map(r -> new Scenario((String)r[0], (String)r[1], (String)r[2], (String)r[3], (String)r[4])).toList();
    }
    @SuppressWarnings("unchecked")
    public List<ScenarioNode> nodes(String scenarioId) {
        List<Object[]> rows = entityManager.createNativeQuery("""
                SELECT n.node_id, n.lat, n.lon FROM road_node n JOIN scenario s ON s.network_id=n.network_id
                WHERE s.id=:scenario ORDER BY n.node_id
                """).setParameter("scenario", scenarioId).getResultList();
        return rows.stream().map(r -> new ScenarioNode((String)r[0], ((Number)r[1]).doubleValue(), ((Number)r[2]).doubleValue())).toList();
    }
    public boolean reachable(String scenarioId, String start, String destination) {
        var result = (Number)entityManager.createNativeQuery("""
                WITH RECURSIVE reachable(node_id) AS (
                    SELECT n.node_id FROM road_node n JOIN scenario s ON s.network_id=n.network_id
                    WHERE s.id=:scenario AND n.node_id=:start
                    UNION
                    SELECT e.to_node_id FROM road_edge e JOIN scenario s ON s.network_id=e.network_id
                    JOIN reachable r ON e.from_node_id=r.node_id WHERE s.id=:scenario
                ) SELECT count(*) FROM reachable WHERE node_id=:destination
                """).setParameter("scenario", scenarioId).setParameter("start", start).setParameter("destination", destination).getSingleResult();
        return result.intValue() > 0;
    }
}
