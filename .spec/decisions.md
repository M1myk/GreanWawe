# Decision register

## Confirmed requirements

- Java and React; GreenWave transport application and a small Gdańsk map.
- SDD with specification before code and separate backend/frontend/database responsibilities.
- Xelerit requires CRUD, a lightweight local database, REST, CORS, usable UI and a distinctive feature.
- Product AI is optional; actual engineering assistance must be documented.
- Signal phases, cycles and pedestrian requests are entirely simulated. No real signal data is used.
- All project documentation and code comments are in English; user-facing discussion may remain Polish.
- Private conversation exports and historical notes are ignored; public documents contain no private conversation links.

## Functional proposals v0.2

SQLite; Java 21/Spring Boot; React/TypeScript/Vite/Tailwind/Leaflet; saved-trip CRUD; a 0.1 s tick; 250 ms polling; 500 m observation range; decisions every 1 s; a 1800 s duration limit. Proposed vehicle defaults: 13.89 m/s maximum speed, 2 m/s² acceleration, 3 m/s² braking. Simplified signals, one vehicle per algorithm and the heuristic in backend/03_navigation.md.

These details are drafts, not previously approved user decisions. Approval of the process does not automatically approve numerical defaults or implementation.

## Open decisions before the relevant stage

- Exact small Gdańsk extract and how road geometry will be prepared.
- Physics parameters, phase programs and physically feasible stopping rules.
- Meaning of the historical name “Native”; no separate mode is defined from an incomplete reference.
- Compatible framework versions, SQLite dialect, migration and UI test tools.
- Machine-readable OpenAPI contract before endpoint implementation.
- Git was present when stage 2 started; implementation uses branch chore/project-setup. No commit or publication was performed.

## Review findings

Feature-level models, contracts and full Given–When–Then scenarios were missing and have been added under features/. Draft versus approved status is now explicit.

**Unresolved conflict:** the proposed rule forbids entering from the beginning of YELLOW. A vehicle can be too close to stop within the braking limit when that phase begins. Merely checking YELLOW duration does not resolve this. Before implementing that behaviour, agree on a YELLOW entry rule or an explicit simplified model. Position clamping must not conceal discontinuous physics. This remains open, not an approved business-rule change.

## Stage 2 dependency and configuration decision

The user authorized the previously described setup stage. Implemented Java 21 with Spring Boot 4.1.2, Spring Web MVC/test starters and Maven; React/react-dom 19.3.1, TypeScript 7.0.2, Vite 8.3.7, React Vite plugin 6.3.0, React types 19.3.0 and Node types 26.6.6. Registry versions were checked and actual builds passed. Node runtime is 24.x; type definitions are compile-time only. SQLite, ORM, map and Tailwind dependencies are deferred until needed.

Default ports: API 8080, frontend 5173. CORS accepts explicit configured origins, no credentials or wildcard. Only GET/OPTIONS are currently exposed; CRUD will expand and test methods in stage 3. Health endpoint is /api/v1/health and is not a persistence-readiness guarantee.

Compatibility references checked during setup: [Spring Boot requirements](https://docs.spring.io/spring-boot/system-requirements.html) and [Vite requirements](https://vite.dev/guide/). Integration was verified by actual Maven/npm builds rather than version assumptions alone.

## Stage 3 authorization

The user requested the next stage with complete domain/DTO/entity/controller/service/repository structure. A synthetic four-node scenario was explicitly accepted, with Gdańsk deferred to stage 4. Adopt the preset numerical validation bounds for saved configurations only; simulation behaviour and YELLOW rules remain unapproved. Flyway and Hibernate use Boot-managed versions with the community SQLite dialect; pin Xerial JDBC at 3.54.1.0. No external DB server is installed. The concrete plan is in features/trip_presets.md.
