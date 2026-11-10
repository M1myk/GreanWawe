# Project setup — stage 2

Status: implementation authorized by the user on 2026-11-10, following the stage-2 plan. This does not approve unrelated simulation proposals.

## Scope and files

Create backend/pom.xml, Java application entry point, health controller, CORS configuration, application properties and HTTP integration tests. Create frontend/package.json, lockfile, TypeScript/Vite configuration, HTML entry, React connection-status view, CSS and environment example. Update README, decisions and SDD report.

Selected direct dependencies: Spring Boot 4.1.2 with Java 21, Spring Web MVC and Spring Boot Web MVC test starter; React/react-dom 19.3.1, TypeScript 7.0.2, Vite 8.3.7, React Vite plugin 6.3.0, React type packages 19.3.0 and Node type definitions 26.6.6. Pin direct versions and commit the npm lockfile. Maven manages transitive versions through the Boot parent. Registry availability and actual builds must confirm compatibility.

No database, ORM, map, Tailwind or simulation dependency is needed yet; introduce those in their relevant stages. No business parameters are decided by this setup.

## Model and API

GET /api/v1/health returns HTTP 200 and JSON {"status":"UP","application":"greenwave"}. It reports application availability, not database readiness. The UI displays checking, connected or unavailable, with a Retry button. It must not imply a working simulation.

## Configuration and invariants

Backend defaults to localhost:8080; frontend to localhost:5173 with strictPort. Configure backend port through SERVER_PORT and allowed origins through GREENWAVE_CORS_ALLOWED_ORIGINS (comma-separated). Default allowed origin: http://localhost:5173. Do not use wildcard origins or credentials. Expose only methods needed now: GET and OPTIONS. Expand and test the list when CRUD is introduced. Frontend uses VITE_API_BASE_URL, default http://localhost:8080/api/v1. No dev proxy: the browser exercises actual CORS.

## Given–When–Then

- SET-01: Given a running backend, when GET /api/v1/health is requested, then return the declared JSON and 200.
- SET-02: Given the configured frontend origin, when a GET preflight is sent, then allow that origin and method.
- SET-03: Given an unapproved origin, when a CORS request is sent, then do not grant CORS permission.
- SET-04: Given a built frontend, when it requests the running API from the configured browser origin, then show Connected; when the API is unavailable, show an error and allow retry.

## Verification

Run Maven tests/package, npm typecheck/build, real HTTP checks and a browser connection check where available. Report exact results and limitations. Use Java 21, Maven 3.9.x and Node 24 for local verification; do not require system-wide package installation. Tests must exercise the real application HTTP configuration rather than only mirror configuration objects.
