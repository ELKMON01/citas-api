# Trazabilidad

## HECHO

Las sesiones S2-S6 requieren commits y evidencias específicas. El backend y frontend deben mantener historial trazable; las pruebas y la evidencia cross-repo son parte de la evaluación.

## HECHO — 2026-09-17

Las HU-001 a HU-036 existen en `docs/FCV Dev/scrum/`. HU-001/002/003 tienen avance parcial; HU-004 define por ahora solo el contrato de identidad. La implementación backend de HU-005/006/007 cuenta con `AuthIntegrationTest`, `IdentityTest` y `AuthRequestGuardTest`; la integración web se controla mediante HU-033.

## HECHO — 2026-09-22

El catálogo de ocho subagentes fue versionado en `docs/FCV Dev/subagents/` y enlazado desde el orquestador. `citas-web` contiene trabajo local React/Vite de autenticación y pruebas; no debe declararse completado hasta ejecutar build, typecheck, tests y verificación cross-repo.

## HECHO — 2026-09-22 · Integración de autenticación

El prototipo `citas-web/portal-de-citas.zip` se importó como React/Vite y se integró con HU-005/006/007. La comprobación usa MySQL persistente, CORS explícito, registro/login/refresh/logout reales y pruebas de frontend. HU-033 permanece en progreso porque las pantallas de perfil, agenda y roles posteriores siguen fuera del corte de autenticación.

## HECHO — 2026-09-25 · Incremento S4

El usuario aprobó HU-025/026/027/028/029/030/032/033, el criterio HU-030 de cita `APPROVED` cuya hora final ya pasó, el acceso a historial USER propietario / PROFESSIONAL asignado / ADMIN global y el contrato lifecycle de `contracts.md`. Ambos repositorios están en `develop`; se añadieron lifecycle API, migración V3, cliente web por REST directo y pantallas por rol. El frontend verificó 25/25 pruebas, lint y build. API compila y pasan cuatro pruebas focalizadas; los nuevos casos de integración y concurrencia no pudieron ejecutarse porque Testcontainers no alcanza Docker Engine desde el contenedor Maven. No se declara ningún DoD completado; matriz y límites en [evidencia S4](../../evidence/S4/README.md).
