# GreenWave — MVP specification v0.2

Date: 2026-11-10. Status: stage 2 implemented; functional simulation specifications remain drafts for review. Documentation and code comments are written in English.

## Goal

Compare a baseline A* route with GreenWave navigation on a small Gdańsk road network. GreenWave considers observed signals, speed adaptation and replanning. Present actual simulation outcomes even when GreenWave performs worse.

## Document index

| Document | Scope |
| --- | --- |
| [Architecture](00_architecture_guidelines.md) | Layers and proposed technologies |
| [Domain models](backend/01_domain_models.md) | Backend entities and responsibilities |
| [Simulation](backend/02_simulation_rules.md) | Clock, signals, physics and randomness |
| [Navigation](backend/03_navigation.md) | A* and proposed GreenWave heuristic |
| [Frontend UI](frontend/01_ui_ux.md) | Layout, interaction and accessibility |
| [Frontend integration](frontend/02_state_and_integration.md) | State, polling, animation and errors |
| [Database](database/01_schema.md) | Logical schema and persistence |
| [REST API](shared/01_api_contracts.md) | Shared contract |
| [Invariants](shared/02_business_invariants.md) | Cross-layer rules |
| [Acceptance index](features/01_acceptance_criteria.md) | Cross-layer scenarios |
| [Implementation plan](implementation_plan.md) | Delivery stages |
| [Decisions](decisions.md) | Confirmed choices, proposals and open questions |
| [SDD workflow](sdd_workflow.md) | Review, planning, implementation and verification |
| [Xelerit requirements](xelerit-requirements.md) | Submission requirements and checklist |

## Planned MVP

A prepared, versioned road network; start/destination selection; one independent vehicle per algorithm; simulated fixed-cycle and pedestrian-triggered signals; acceleration, braking and speed limits; start/pause/resume/reset and playback speed; comparison metrics; saved configurations and results; full saved-trip CRUD in UI and REST; reproducibility using seed and versions.

Out of scope: real signal control, live ITS data, GPS navigation, ML, queues, interacting vehicles, accounts and a mobile app.

## Feature specifications

Each document includes a data model, invariants, API references, scope and Given–When–Then criteria:

- [Trip presets](features/trip_presets.md)
- [Road network](features/road_network.md)
- [Traffic lights](features/traffic_lights.md)
- [Vehicle physics](features/vehicle_physics.md)
- [A* navigation](features/astar_navigation.md)
- [GreenWave navigation](features/greenwave_navigation.md)
- [Simulation comparison](features/simulation_comparison.md)

Shared definitions are linked rather than maintained as competing contracts. Java and React are selected; detailed contracts, numerical defaults and the heuristic remain proposals listed in the decision register. Approval of the SDD process does not approve every technical proposal.

All phases, cycles and pedestrian requests are simulated by Java. Map-source metadata describes only background and road geometry, not live signal behaviour. Private conversation archives are ignored by Git and are not dependencies of this specification. [AGENTS.md](../AGENTS.md) and the factual AI report remain public project documents.

Update the specification first when an accepted requirement changes, followed by implementation and relevant tests.

Stage 2 specification and verification criteria: [Project setup](features/project_setup.md). Health/CORS and the frontend connection view are implemented; CRUD, database and simulation remain planned.
