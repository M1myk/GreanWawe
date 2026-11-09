# Architecture guidelines

## Layers

React frontend → REST API → Java application services → simulation/navigation domain. Repositories connect services to SQLite. The domain does not depend on HTTP, React or ORM entities.

| Layer | Responsibility | Boundary |
| --- | --- | --- |
| Backend | Graph, signals, physics, decisions and metrics | Authoritative simulation state |
| Frontend | Forms, map, interpolation and results | No independent routing or physics |
| Database | Scenarios, versions, configurations and results | Does not drive the clock |

Planned implementation folders: `backend/` and `frontend/`. Backend modules: network, simulation, navigation and comparison; introduce domain/application/api/persistence subdivisions only where useful. Documentation lives in `.spec/` and `docs/`.

## Proposed stack

Java 21, Spring Boot/Spring Web and Maven; React, strict TypeScript, Vite, Tailwind CSS and Leaflet; SQLite with Spring Data JPA/Hibernate. Before implementation, pin compatible dependency versions and verify the SQLite dialect and migration integration. Framework versions are not yet selected.

JUnit 5 for domain and integration tests; Mockito only where a boundary needs replacement. Frontend test tools will be selected in the implementation plan, favouring interaction tests and one complete comparison journey.

## Engineering rules

Keep API DTOs separate from persistence entities and internal engine models. Give planners restricted observations, not the engine or random generator. Dependencies require explicit inclusion and acceptance in the specification. Keep environment configuration outside source code and document it. Write documentation and code comments in English.

The MVP uses one backend instance and one local operator. Distributed sessions are out of scope. Log comparison IDs, status changes and failures; avoid logging every full snapshot.

## Map and signal data

A prepared OpenStreetMap extract supplies a directed graph with geometry, directions and speed limits. Map tiles and routing data are separate. Do not delegate routing to an external API. Record source, acquisition date, licence, attribution and transformations with the dataset; verify tile-provider terms before configuration.

All signals, phase programs and pedestrian events are defined by the simulation scenario. No live controllers, ITS feeds or measured signal timings are integrated. OpenStreetMap data does not supply runtime signal phases.
