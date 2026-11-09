# GreenWave

A full-stack web MVP for exploring how traffic signals affect urban journeys. GreenWave is being developed as a transport-themed recruitment project for Xelerit.

## Project idea

The planned application compares two virtual vehicles travelling across a small road network in Gdańsk:

- **A*** chooses a baseline route using road travel costs.
- **GreenWave** uses available information about nearby simulated signals to adjust speed and reconsider its route.

Both runs use the same vehicle physics, simulation clock and pedestrian-event timeline. Results compare journey time, distance, stops, stopped time and speed variation. GreenWave is not assumed to win every comparison.

All signal phases, cycles and pedestrian requests are simulated by the backend. No live traffic-signal or ITS data is used. Map geometry and background tiles are separate from simulated signal behaviour.

## Current status

The project is in the specification and review stage. Layer specifications, feature acceptance criteria, an implementation roadmap and AI-agent instructions have been prepared. Backend and frontend implementation, application tests and a demonstration recording have not started. Technical defaults and the detailed GreenWave heuristic remain proposals until reviewed.

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
| Backend | REST, simulation, routing, validation, metrics | Java; Spring Boot and Maven proposed |
| Frontend | Map, forms, controls, animation and results | React; TypeScript, Vite, Tailwind CSS and Leaflet proposed |
| Database | Saved configurations, scenarios and results | SQLite proposed; no external database server |
| Map | Road geometry and background | Prepared OpenStreetMap extract proposed |

Java and React are selected. Exact dependency versions and SQLite integration will be verified before implementation. Backend code will live in `backend/`, frontend code in `frontend/`. These implementation folders do not exist yet.

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
| 2. Project setup | Separate Java/React projects, pinned dependencies, CORS and local setup | Planned |
| 3. Saved trips | End-to-end CRUD with SQLite and a simple UI | Planned |
| 4. Road network | Small Gdańsk extract, validated graph and map display | Planned |
| 5. Baseline simulation | Clock, signals, vehicle physics and A* | Planned |
| 6. GreenWave | Limited-observation routing and speed adaptation | Planned |
| 7. Integration and verification | Controls, animation, results and reproducibility checks | Planned |
| 8. Submission | Verified setup guide, GIF/MP4 demo and public GitHub repository | Planned |

The [implementation plan](.spec/implementation_plan.md) gives more detail. Open decisions, including yellow-light behaviour and numerical defaults, are recorded in the [decision register](.spec/decisions.md).

## How AI is used

I use **ChatGPT and Codex (OpenAI)** as engineering assistants and technical mentors. So far, AI has helped organize requirements, draft the architecture and specification, identify gaps and conflicting rules, define acceptance criteria, and prepare project documentation and `.gitignore`.

During implementation, I plan to use AI for bounded coding tasks, test preparation, debugging and review. These are planned activities, not claims of completed work. The product itself does not currently include an AI feature; its distinctive functionality is the GreenWave simulation.

Agent behaviour and constraints are documented in [AGENTS.md](AGENTS.md). Actual activities, findings and verification are recorded in the [SDD report](docs/sdd_report.md). Private conversation exports and conversation links are excluded from public project materials.

## Documentation

- [Specification index](.spec/README.md)
- [Architecture](.spec/00_architecture_guidelines.md)
- [SDD workflow](.spec/sdd_workflow.md)
- [REST contract](.spec/shared/01_api_contracts.md)
- [Recruitment requirements and submission checklist](.spec/xelerit-requirements.md)

Project documentation and code comments are written in English.

## Local setup and tests

There is no runnable application yet. Verified, step-by-step instructions for installing dependencies, configuring the environment, starting the API and frontend, and running tests will be added alongside implementation. No unverified startup commands or test results are presented here.

## Demonstration

A short GIF or MP4 showing saved-trip CRUD and a simulation comparison will be added to the repository before submission. No demonstration recording is available yet.
