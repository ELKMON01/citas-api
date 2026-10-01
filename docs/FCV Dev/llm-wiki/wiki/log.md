# Log de operaciones

| Fecha | Operación | Fuentes/páginas | Resultado |
|---|---|---|---|
| 2026-09-17 | INGEST inicial | SRC-PRD-001, SRC-TECH-001, SRC-DB-001, SRC-TRACE-001 | Estructura y páginas iniciales creadas tras aprobación del usuario |
| 2026-09-17 | DECISIÓN | HU-001 a HU-007; `contracts.md`, `decisions.md` | Contrato y corte backend de identidad aprobados; HU-033 conserva integración web |
| 2026-09-17 | LEARN/LINT | Flyway V1, pruebas MySQL 8.4; `data-integrity.md`, `traceability.md` | Corte 3FN y evidencia backend añadidos; RAW intacto, sin nuevos enlaces estructurales |
| 2026-09-22 | LEARN/DECISIÓN | `docs/FCV Dev/subagents/`, orquestador, `index.md`, `subagents.md`, arquitectura, decisiones, riesgos y trazabilidad | Ocho subagentes versionados; protocolo de delegación y nueva ubicación documental registrados; frontend React/Vite reconocido como trabajo pendiente de verificación |
| 2026-09-22 | LINT | Catálogo de subagentes y LLM Wiki | Enlaces Markdown relativos verificados; referencias operativas del orquestador actualizadas; se conserva como riesgo explícito la allowlist histórica de la Skill Scrum |
| 2026-09-22 | LEARN/DECISIÓN | HU-033, `contracts.md`, `traceability.md` | Corte React de autenticación integrado y validado contra API/MySQL; CORS explícito y compatibilidad Flyway con el esquema 3FN existente documentados; HU-033 queda en progreso. |
| 2026-09-22 | LINT | `data-integrity.md`, HU-005 a HU-007 | Evidencia actualizada a 9/9 pruebas Maven y modelo persistente `refresh_tokens`; integración web USER confirmada sin cerrar HU posteriores. |
| 2026-09-22 | DECISIÓN | HU-001, HU-002, HU-011, HU-014 a HU-024 y `contracts.md` | Cierre S2 confirmado para fundación y modelo 3FN; corte S3 aprobado con afiliación opcional, agenda real y contrato REST compartido. |
| 2026-09-25 | DECISION / PLAN | HU-025/026/027/028/029/030/032/033, `contracts.md`, `decisions.md`, `S4_BASELINE.md` | User approved S4 scope, HU-030 applicability, HU-032 audit permissions, and the lifecycle REST contract. Clean starting state preserved; `develop` created from existing `main` heads in both repos. |
| 2026-09-25 | IMPLEMENT / VERIFY | S4 backend/frontend, `traceability.md`, `docs/FCV Dev/evidence/S4/` | Lifecycle API, V3 migration and role-based web flows implemented on `develop`; frontend 25/25 tests, lint and build PASS. Backend integration and cross-repo runtime BLOCKED by missing Maven/Docker access. Independent backend review pending; no HU DoD closed and no commit created because API hook cannot pass its required full Maven suite. |
