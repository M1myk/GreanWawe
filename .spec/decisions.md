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
- Git initialization before branch-based development; the working folder has no Git history at this documentation stage.

## Review findings

Feature-level models, contracts and full Given–When–Then scenarios were missing and have been added under features/. Draft versus approved status is now explicit.

**Unresolved conflict:** the proposed rule forbids entering from the beginning of YELLOW. A vehicle can be too close to stop within the braking limit when that phase begins. Merely checking YELLOW duration does not resolve this. Before implementing that behaviour, agree on a YELLOW entry rule or an explicit simplified model. Position clamping must not conceal discontinuous physics. This remains open, not an approved business-rule change.
