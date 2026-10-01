# Registro de decisiones

## DECISIÓN — 2026-09-17

La raíz no se convierte en un tercer repositorio. La única LLM Wiki se versiona dentro de `citas-api/docs/FCV Dev/llm-wiki/`.

## DECISIÓN — 2026-09-17 · Identidad backend

El usuario aprobó el incremento mínimo de HU-001/002/004, el seed parcial de HU-003 y el backend completo de HU-005/006/007. Email se compara sin distinguir mayúsculas y documento por tipo+número. El refresh va en cookie HttpOnly para sitios distintos y rota en cada uso. Logout revoca el refresh de esa sesión. Las obligaciones de interfaz se trasladan a HU-033.

## PREGUNTA ABIERTA

No se han aprobado todavía estados exhaustivos de citas, contratos de las demás HU, zona horaria ni estrategia de reserva concurrente.

## DECISIÓN — 2026-09-22 · Catálogo de subagentes

Los ocho subagentes especializados se mantienen como archivos Markdown versionados en `docs/FCV Dev/subagents/`. El orquestador selecciona el perfil más específico, separa implementación de verificación y conserva la responsabilidad de coordinar cambios cross-repo y actualizar la Wiki.

## HECHO — 2026-09-22 · Frontend

React es el framework detectado en `citas-web`; deja de ser una pregunta abierta. La aprobación visual y la verificación del incremento auth continúan pendientes de evidencia.

## DECISION - 2026-09-25 - S4 scope and lifecycle rules

The user approved HU-025/026/027/028/029/030/032/033 for S4, with HU-033 limited to these flows. HU-030 applies when an appointment is `APPROVED` and its scheduled end has passed in `America/Bogota`. For HU-032, USER reads own appointment histories, PROFESSIONAL reads histories assigned to their identity, and ADMIN reads any history. The approved S4 REST contract is in `contracts.md`.
