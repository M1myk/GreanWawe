# GreenWave

A full-stack web MVP for exploring how traffic signals affect urban journeys. GreenWave is being developed as a transport-themed recruitment project for Xelerit.

## Project idea

The planned application compares two virtual vehicles travelling across a small road network in Gdańsk:

- **A*** chooses a baseline route using road travel costs.
- **GreenWave** uses available information about nearby simulated signals to adjust speed and reconsider its route. It estimates arrival at each signal along a candidate path and carries any waiting time forward to the next junction.

Both runs use the same vehicle physics, simulation clock and pedestrian-event timeline. Results compare journey time, distance, stops, stopped time and speed variation. GreenWave is not assumed to win every comparison.

All signal phases, cycles and pedestrian requests are simulated by the backend. No live traffic-signal or ITS data is used. Map geometry and background tiles are separate from simulated signal behaviour.

## Current status

The full-screen map runs A* and GreenWave together. Both cars share the same simulation time and signal conditions and apply the same maneuver speed limits. SQLite stores the final comparison result and saved trip configurations. The single-run API remains available for separate experiments.

## Planned MVP

- Select start and destination nodes on a small map.
- Create, read, update and delete saved trip configurations through the UI and REST API.
- Simulate acceleration, braking and fixed-cycle or pedestrian-triggered signals.
- Start, pause, resume and reset a comparison; change playback speed.
- Display both vehicles and routes, then compare their results.
- Save configurations and completed results locally using SQLite.
- Reproduce runs using the same configuration, seed and data/engine versions.

Live GPS navigation, real signal control, traffic queues, multiple interacting vehicles and machine-learning predictions are outside the MVP.

## Architecture and proposed technologies

| Layer | Responsibility | Technology |
| --- | --- | --- |
| Backend | REST, simulation, routing, validation, metrics | Java 21, Spring Boot 4.1.2, Maven |
| Frontend | Map, forms, controls, animation and results | React 19.3.1, TypeScript 7.0.2, Vite 8.3.7; Tailwind CSS and Leaflet planned |
| Database | Saved configurations, scenarios and results | SQLite through Xerial JDBC; no external database server |
| Map | Road geometry and background | Prepared OpenStreetMap extract and local SVG road view |

The backend is in `backend/`, the frontend in `frontend/`. Direct dependency versions are pinned in their manifests, with the frontend lockfile included. SQLite integration now uses Xerial JDBC 3.54.1.0, Hibernate community dialects 7.4.13.Final and Flyway 12.4.0 (Hibernate/Flyway managed by Spring Boot). The full-screen view overlays the stored road graph bounds and endpoint pins on OpenStreetMap tiles. The local road drawing provides a fallback if tiles fail. Set VITE_MAP_TILE_URL to override the tile URL template. Tailwind remains deferred.

## How I am building the project

I use **Spec-Driven Development (SDD)**: requirements and acceptance criteria come before implementation. The approved specification in `.spec/` is the source of truth. Draft proposals are explicitly distinguished from approved decisions and implemented features.

For each feature I follow this cycle:

1. Describe its data model, API contract, business invariants and Given–When–Then criteria.
2. Use AI to review ambiguities, edge cases and concurrency concerns.
3. Review a bounded implementation plan with affected files, dependencies and checks.
4. Authorize implementation of that plan.
5. Review the changes and run appropriate automated tests.
6. Keep the specification, code and tests aligned, including after manual changes.

I remain responsible for scope, design decisions, understanding the solution and reviewing generated changes. Work is planned in small increments; Git branches and diff reviews will be used once the repository is initialized.

## Development stages

| Stage | Planned outcome | Status |
| --- | --- | --- |
| 1. Requirements and specification | Layer boundaries, API, invariants and feature criteria | Draft prepared; review ongoing |
| 2. Project setup | Separate Java/React projects, pinned dependencies, CORS and local setup | Implemented and verified |
| 3. Saved trips | End-to-end CRUD with SQLite and a simple UI | Implemented and verified |
| 4. Road network | Small Gdańsk extract, validated graph and map display | Implemented |
| 5. Baseline simulation | Clock, signals, vehicle physics and A* | Implemented |
| 6. GreenWave | Limited-observation routing and speed adaptation | Implemented in the API |
| 7. Integration and verification | Controls, animation, results and reproducibility checks | Implemented |
| 8. Submission | Verified setup guide, GIF/MP4 demo and public GitHub repository | Planned |

The [implementation plan](.spec/implementation_plan.md) gives more detail. The approved 30 m yellow-light rule and open numerical defaults are recorded in the [decision register](.spec/decisions.md).

## How AI is used

I use **ChatGPT and Codex (OpenAI)** as engineering assistants and technical mentors. So far, AI has helped organize requirements, draft the architecture and specification, identify gaps and conflicting rules, define acceptance criteria, and prepare project documentation and `.gitignore`.

In stage 2, AI also implemented the Java/React skeletons, configured CORS, prepared and ran three HTTP integration tests, built the frontend and checked browser connection, failure and recovery states. In stage 3, AI also prepared the layered preset implementation, migrations, OpenAPI contract, React CRUD UI and integration tests. Testing exposed an initial null-version getter bug; AI fixed it and reran the suite. In stage 5, AI implemented the baseline simulation, map controls and regression tests. Tests exposed an issue with the last braking increment; AI corrected the stopping calculation. In stage 6, AI implemented the signal-aware planner, the shared maneuver speed limits and the related tests. In stage 7, AI connected both vehicle runs to a shared clock and built the comparison controls, map view, SQLite storage and regression tests. The product itself does not currently include an AI feature; its distinctive functionality is the GreenWave simulation.

Agent behaviour and constraints are documented in [AGENTS.md](AGENTS.md). Actual activities, findings and verification are recorded in the [SDD report](docs/sdd_report.md). Private conversation exports and conversation links are excluded from public project materials.

## Documentation

- [Specification index](.spec/README.md)
- [Architecture](.spec/00_architecture_guidelines.md)
- [SDD workflow](.spec/sdd_workflow.md)
- [REST contract](.spec/shared/01_api_contracts.md)
- [Recruitment requirements and submission checklist](.spec/xelerit-requirements.md)

Project documentation and code comments are written in English.

## Local setup and tests

### Prerequisites

Use JDK 21, Maven 3.9.x and Node.js 24.x with npm. Verification used Java 21.0.11, Maven 3.9.16, Node 24.14.1 and npm 11.11.0 on macOS. Downloading dependencies requires internet access. No database server, SQLite command-line installation or API key is required. Maven downloads the bundled JDBC driver.

### 1. Build and start the backend

From the repository root:

```sh
cd backend
mvn verify
java -jar target/greenwave-backend-0.1.0-SNAPSHOT.jar
```

Stop a running JAR before rebuilding it, then restart with the new artifact. Leave this terminal running. The API listens on the loopback interface at `http://localhost:8080`. Open `http://localhost:8080/api/v1/health` to see:

```json
{"status":"UP","application":"greenwave"}
```

This endpoint checks application availability, not database readiness.

### 2. Install and start the frontend

In a second terminal, from the repository root:

```sh
cd frontend
cp .env.example .env.local
npm ci
npm run dev
```

Open `http://localhost:5173` to see the full-screen Gdańsk road map. Search for an imported street in **Skąd** or **Dokąd**, or focus a field and click near a road on the map. Blue A marks the origin and red B marks the destination.

Once a route is available, press **Rozpocznij teraz** to start both cars. The blue line is A* and the dashed green line is GreenWave. Driving View follows the selected car. The top banner shows its current street and upcoming junction or light, while the bottom panel shows controls and the estimated time difference for the fastest or highlighted alternative relative to the selected GreenWave route. Switch the followed car with the A* and GreenWave buttons, or use **×** to return to the overview. Use **Pauza**, **Wznów**, **Reset** and **Tempo** to control both runs together. Playback supports up to 10× and the default trip limit is 3,000 simulated seconds. Yellow lasts 3 seconds for both signal types. The table compares travel time, distance, stops, stopped time, speed variation and estimated gasoline use. GreenWave can show a target speed to reach the current green window. Its simulation-only boost is at most 20 km/h above the ordinary cap on compatible approach segments. The default planner observes 1,500 m of directed roads and compares up to ten route variants. With predicted waiting, it accelerates normally before coasting toward the light. Both cars use the same [2.0 L fuel estimate](docs/fuel-model.md). Finished results remain available from GET /api/v1/comparisons/{id}/result after restart. Reset replaces the saved result. Stop each server with Ctrl+C.

Use **Wypróbuj przykład** for Kołobrzeska → Pomorska or **Strzyża → Drzewieckiego · szczyt** for the rush-hour comparison. The endpoint fields also accept street and house number from the bundled Gdańsk address index. Driving View rotates the map to keep the followed car's direction up. The alternatives panel shows the switching threshold for a faster option and briefly explains why a previously faster option disappeared. The completed comparison summarizes signed GreenWave differences in time, estimated fuel and stops.

Choose **Bez natężenia**, **Zwykły ruch** or **Godziny szczytu** before starting. The orange and red map layers show simulated traffic queues. In these queues the cars alternate between crawling, braking, stopping and restarting. Idling and repeated acceleration affect the shared fuel estimate. Both cars obey the same restrictions. GreenWave estimates observed stop-and-go delay and requires a meaningful improvement before rerouting during the drive. A* keeps its initial route. Violet dashes show the GreenWave alternatives. Open **Warianty GreenWave** to highlight one and inspect its estimated remaining time, signal wait and congestion delay. Selecting another mode prepares a new comparison. Saved presets retain the selected mode.

To try GreenWave through the local API, create a single run and use the session ID from the response to start it:

```sh
curl -sS http://localhost:8080/api/v1/simulations \
  -H 'Content-Type: application/json' \
  -d '{"networkId":"synthetic-v1","startNodeId":"n1","destinationNodeId":"n3","randomSeed":42,"algorithm":"GREENWAVE"}'

curl -sS http://localhost:8080/api/v1/simulations/SESSION_ID/commands \
  -H 'Content-Type: application/json' -d '{"commandId":"start","type":"START"}'

curl -sS http://localhost:8080/api/v1/simulations/SESSION_ID
```

Open **Zapisane** to manage named trip presets. The saved-trip form opens the Gdańsk extract. Use **Pick start** and **Pick destination**, then click near road nodes or select their IDs from the lists. Enter a name and save. For a minimal example, switch to the synthetic scenario and choose n1 → n3 or n1 → n2. Use Edit to update it, Delete to remove it, or **Porównaj** to load the saved configuration onto the main map. Node n4 is isolated and demonstrates validation. The saved seed and numerical parameters are applied when you start the comparison.

### 3. Run checks and build

From `backend/`, run `mvn verify` for HTTP integration tests and the executable JAR. From `frontend/`, run `npm run typecheck` or `npm run build` (which also runs typecheck). The frontend build is written to `frontend/dist/`.

Automated coverage includes HTTP integration tests covering health/CORS, complete CRUD, nested validation and malformed JSON, directed reachability, pagination, concurrent/stale updates, migrations, foreign keys reopening a persisted SQLite file, network geometry and unknown network IDs. Browser checks cover connection states, creation, editing, validation and retrieval after a backend restart. Deletion is verified through HTTP tests and the empty list was checked in the browser. Baseline coverage includes the REST lifecycle and pause, A* path costs, braking and downstream speed limits, the yellow-light threshold, and seeded replay.

### Configuration

| Variable | Default | Usage |
| --- | --- | --- |
| SERVER_PORT | 8080 | Export before starting Java |
| GREENWAVE_CORS_ALLOWED_ORIGINS | http://localhost:5173 | Comma-separated explicit browser origins; export before starting Java |
| GREENWAVE_DB_URL | jdbc:sqlite:greenwave.db?foreign_keys=on&busy_timeout=5000 | Export before starting Java; parent directories must exist |
| VITE_API_BASE_URL | http://localhost:8080/api/v1 | Set in frontend/.env.local; restart Vite after changes |

Spring Boot does not automatically read a `.env` file in this setup: export backend variables in the shell. Vite loads frontend `.env.local`. Never store secrets in `VITE_` variables; these become public browser code.

Vite uses strict port 5173 and fails if it is occupied. Stop the other application or update the port deliberately together with CORS. `localhost` and `127.0.0.1` are different browser origins; use the documented frontend URL or explicitly allow the chosen origin. The optional Vite preview uses port 4173, which must be explicitly allowed before API calls from preview will work.

The default SQLite file is created in the backend process working directory, normally `backend/greenwave.db`. Flyway applies numbered SQL migrations automatically; Hibernate never auto-updates the schema. The file survives restart and is ignored by Git. Use a temporary database URL for disposable experiments rather than deleting real data. CORS allows GET/POST/PUT/DELETE/OPTIONS only for configured origins, without credentials.

## Project structure

```text
.spec/       Requirements, architecture, contracts and acceptance criteria
backend/     Java domain, API/DTOs, services, JPA persistence, migrations and tests
frontend/    React application, TypeScript/Vite configuration and lockfile
docs/        Factual SDD and AI-use report
AGENTS.md    Engineering and mentoring instructions
```

## Backend structure

```text
com.greenwave/
  config/                    HTTP and CORS configuration
  health/                    Application availability endpoint
  trip/
    domain/                  Immutable TripPreset and TripConfig objects
    api/                     REST controller
      dto/                   Create/update requests and typed responses
    service/                 Transactional business rules and explicit mapper
    persistence/             TripPresetEntity and Spring Data repository
  scenario/
    domain/                  Scenario and node objects
    api/dto/                 Read-only scenario responses
    service/                 Scenario lookup
    persistence/             Scenario and directed-reachability queries
  shared/api/                Structured errors and centralized exception handling
resources/db/migration/      Versioned SQLite schema and synthetic fixture
```

Entities are not exposed over HTTP. Public preset versions start at 1; stale edits return 409 and retain the form so the user can reload deliberately. Each comparison owns a copy of its configuration.

Implemented endpoints are described in [OpenAPI](.spec/shared/openapi.yaml): health, scenario reads, road-network reads, full trip-preset CRUD, single-run sessions and paired comparison sessions.

See [road data provenance](docs/road-network-data.md) for the extract bounds, licence and graph limitations.

## Demonstration

A short GIF or MP4 showing saved-trip CRUD and a simulation comparison will be added to the repository before submission. No demonstration recording is available yet.
