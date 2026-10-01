# Índice de la LLM Wiki

Última actualización: 2026-09-25. El corte backend de identidad (HU-005/006/007) mantiene su contrato REST. El incremento S4 (HU-025/026/027/028/029/030/032/033) está aprobado e implementado en ambos repositorios sobre `develop`; el contrato lifecycle está en `contracts.md`. Las pruebas frontend están verdes; compilación backend y pruebas focalizadas pasan, mientras backend lifecycle e integración cross-repo siguen pendientes por el acceso de Testcontainers a Docker. La evidencia vigente está en [evidencia S4](../../evidence/S4/README.md).

## Lectura recomendada

1. [Resumen](overview.md)
2. [Reglas de dominio](domain-rules.md)
3. [Arquitectura](architecture.md)
4. [Integridad de datos](data-integrity.md)
5. [Contratos](contracts.md)
6. [Decisiones](decisions.md)
7. [Riesgos y preguntas](risks-open-questions.md)
8. [Preferencias](preferences.md)
9. [Trazabilidad](traceability.md)
10. [Subagentes y delegación](subagents.md)

## Gobierno

- [Log](log.md)
- [Convenciones](../schema/page-conventions.md)
- [Gobierno](../schema/governance.md)
- [Manifest RAW](../raw/manifest.md)

2026-09-25 update: identity and S3 contracts remain in force. Lifecycle S4 (HU-025/026/027/028/029/030/032/033) is approved and implemented; evidence is tracked in [S4 evidence](../../evidence/S4/README.md) and the workspace `S4_BASELINE.md`. Frontend checks, backend compile, and four backend focal tests pass; lifecycle persistence checks and cross-repo runtime verification remain blocked. HU DoD remains pending.
2026-09-30 update: additive identity completion is implemented in both repositories for password recovery/reset and USER profile/affiliation. Verification is complete: API suite, frontend tests/lint/build and local Docker runtime pass; the local web is on 5174 and API on 8081.
