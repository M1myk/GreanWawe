# GreenWave

A full-stack web MVP for exploring how traffic signals affect urban journeys. GreenWave is being developed as a transport-themed recruitment project for Xelerit.

## Project idea

The planned application compares two virtual vehicles travelling across a small road network in Gdańsk:

- **A*** chooses a baseline route using road travel costs.
- **GreenWave** uses available information about nearby simulated signals to adjust speed and reconsider its route.

Both runs use the same vehicle physics, simulation clock and pedestrian-event timeline. Results compare journey time, distance, stops, stopped time and speed variation. GreenWave is not assumed to win every comparison.

All signal phases, cycles and pedestrian requests are simulated by the backend. No live traffic-signal or ITS data is used. Map geometry and background tiles are separate from simulated signal behaviour.

## Current status

Stage 2 (project setup) is implemented: separate Spring Boot and React applications, a health endpoint, configurable CORS, a browser connection check and HTTP integration tests. Specification review for later features continues. Saved-trip CRUD, SQLite persistence, maps and the simulation are not implemented yet. Numerical simulation defaults and the detailed GreenWave heuristic remain proposals until reviewed.

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
| Database | Saved configurations, scenarios and results | SQLite proposed; no external database server |
| Map | Road geometry and background | Prepared OpenStreetMap extract proposed |

The backend is in `backend/`, the frontend in `frontend/`. Direct dependency versions are pinned in their manifests, with the frontend lockfile included. SQLite integration and map/styling dependencies will be introduced and verified in their respective stages.

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
| 3. Saved trips | End-to-end CRUD with SQLite and a simple UI | Planned |
| 4. Road network | Small Gdańsk extract, validated graph and map display | Planned |
| 5. Baseline simulation | Clock, signals, vehicle physics and A* | Planned |
| 6. GreenWave | Limited-observation routing and speed adaptation | Planned |
| 7. Integration and verification | Controls, animation, results and reproducibility checks | Planned |
| 8. Submission | Verified setup guide, GIF/MP4 demo and public GitHub repository | Planned |

The [implementation plan](.spec/implementation_plan.md) gives more detail. Open decisions, including yellow-light behaviour and numerical defaults, are recorded in the [decision register](.spec/decisions.md).

## How AI is used

I use **ChatGPT and Codex (OpenAI)** as engineering assistants and technical mentors. So far, AI has helped organize requirements, draft the architecture and specification, identify gaps and conflicting rules, define acceptance criteria, and prepare project documentation and `.gitignore`.

In stage 2, AI also implemented the Java/React skeletons, configured CORS, prepared and ran three HTTP integration tests, built the frontend and checked browser connection, failure and recovery states. Future bounded coding, test preparation, debugging and review remain planned for subsequent features. The product itself does not currently include an AI feature; its distinctive functionality is the GreenWave simulation.

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

Use JDK 21, Maven 3.9.x and Node.js 24.x with npm. Verification used Java 21.0.11, Maven 3.9.16, Node 24.14.1 and npm 11.11.0 on macOS. Downloading dependencies requires internet access. No database server or API key is needed for stage 2.

### 1. Build and start the backend

From the repository root:

```sh
cd backend
mvn verify
java -jar target/greenwave-backend-0.1.0-SNAPSHOT.jar
```

Leave this terminal running. The API listens on the loopback interface at `http://localhost:8080`. Open `http://localhost:8080/api/v1/health` to see:

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

Open `http://localhost:5173`. The page should display **Connected to the GreenWave API.** The frontend calls the API directly across origins rather than using a development proxy, so this checks CORS too. Use **Retry connection** after restarting the API. Stop each server with Ctrl+C.

### 3. Run checks and build

From `backend/`, run `mvn verify` for HTTP integration tests and the executable JAR. From `frontend/`, run `npm run typecheck` or `npm run build` (which also runs typecheck). The frontend build is written to `frontend/dist/`.

Current automated coverage: health response, allowed custom-origin preflight and rejection of an unapproved origin. Browser checks covered connected, unavailable and recovered states. There are no simulation tests yet.

### Configuration

| Variable | Default | Usage |
| --- | --- | --- |
| SERVER_PORT | 8080 | Export before starting Java |
| GREENWAVE_CORS_ALLOWED_ORIGINS | http://localhost:5173 | Comma-separated explicit browser origins; export before starting Java |
| VITE_API_BASE_URL | http://localhost:8080/api/v1 | Set in frontend/.env.local; restart Vite after changes |

Spring Boot does not automatically read a `.env` file in this setup: export backend variables in the shell. Vite loads frontend `.env.local`. Never store secrets in `VITE_` variables; these become public browser code.

Vite uses strict port 5173 and fails if it is occupied. Stop the other application or update the port deliberately together with CORS. `localhost` and `127.0.0.1` are different browser origins; use the documented frontend URL or explicitly allow the chosen origin. The optional Vite preview uses port 4173, which must be explicitly allowed before API calls from preview will work.

## Project structure

```text
.spec/       Requirements, architecture, contracts and acceptance criteria
backend/     Java application, HTTP configuration and integration tests
frontend/    React application, TypeScript/Vite configuration and lockfile
docs/        Factual SDD and AI-use report
AGENTS.md    Engineering and mentoring instructions
```

## Demonstration

A short GIF or MP4 showing saved-trip CRUD and a simulation comparison will be added to the repository before submission. No demonstration recording is available yet.
