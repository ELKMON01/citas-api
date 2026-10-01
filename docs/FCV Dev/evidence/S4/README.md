# Evidencia S4

Fecha: 2026-09-25. Ramas de trabajo: `develop` en `citas-api` y `citas-web`. El contrato aprobado, estado inicial y decisiones están en [S4_BASELINE.md](../../../../../S4_BASELINE.md).

## Comprobaciones reproducibles

| Área | Comando / fuente | Resultado | Límite |
|---|---|---|---|
| Frontend tests | `npm test` en `citas-web` | PASS, 4 archivos / 25 pruebas | No cubre persistencia API |
| Frontend tipos | `npm run lint` | PASS (`tsc --noEmit`) | — |
| Frontend build | `npm run build` | PASS, 1667 módulos | Diseño aprobado no disponible para comparación |
| API whitespace | `git diff --check` | PASS | No sustituye compilación |
| API compile | `docker compose run --rm --no-deps citas-api-dev mvn -q -DskipTests test` | PASS | Compila producción y pruebas, no ejecuta tests |
| API focalizadas | `-Dtest=IdentityTest,AuthRequestGuardTest test -q` en el contenedor Maven | PASS, 4 pruebas | No cubre persistencia lifecycle |
| API integración/migración | `SchedulingServiceIntegrationTest` | BLOCKED | No se pudo compilar ni levantar Testcontainers |
| Revisión independiente frontend | revisor de solo lectura | PASS cliente; runtime/diseño NO VERIFICABLE | No API activa ni referencia visual aprobada |
| Revisión independiente backend | verificador de solo lectura | PASS estático | Testcontainers y nuevos escenarios de integración pendientes |
| Hook pre-commit FAIL | secreto sintético temporal | PASS de rechazo en ambos repos | No se conservó el dato sintético |
| Hook pre-commit PASS | `git hook run pre-commit` en `citas-web` con cambios staged temporalmente | PASS | Índice restaurado tras la ejecución; API hook sigue bloqueado por Maven/Testcontainers |

## LOOP_01 — doble reserva / ocupación de slots

**Estado: BLOCKED para ejecución; implementación revisada.** La prueba existente `retainsConsecutiveSlotsAndReleasesThemAfterAdministrativeRejection` y el nuevo caso lifecycle cubren los escenarios previstos. El caso nuevo intenta reservar una franja retenida por reprogramación pendiente y conserva cita/retención. El verificador backend confirmó estáticamente que disponibilidad y reserva normal excluyen retenciones PENDING y que la solicitud bloquea sus slots candidatos. Testcontainers no pudo ejecutarse en este entorno, por lo que no se presenta resultado verde de ejecución ni se afirma evidencia histórica que no existe.

## LOOP_02 — ciclo de reprogramación

**Estado: BLOCKED para ejecución.** Builder implementó retención de slots, preservación de la cita original, aprobación/rechazo atómico, liberación de retenciones, serialización por lock y errores 404/409; se añadieron casos de integración y concurrencia. Compilación backend pasó y la revisión independiente aprobó estáticamente servicios, migración y autorización. Los nuevos escenarios de integración y migración V3 no se ejecutaron porque Testcontainers no accede al Docker Engine desde el contenedor Maven.

## LOOP_03 — reconciliación de contrato cliente/API

**Estado: PARCIAL.** El cliente usa rutas, parámetros y payloads del contrato aprobado; pruebas de API cliente y pantallas verifican filtros, errores 403/409, slots disponibles, permisos visuales y acciones por rol. Revisores comprobaron los contratos por inspección y frontend PASS. La integración real con servidor/API, respuestas de seguridad y fidelidad a diseño no se verificó.

## Cierre de HU y riesgos

Las HU S4 permanecen aprobadas/en progreso, con sus DoD sin marcar completos. Para cerrar hace falta restaurar acceso a Docker/Maven, ejecutar `mvn test` incluyendo Testcontainers, ejecutar el hook completo requerido, levantar ambos servicios y probar USER/PROFESSIONAL/ADMIN sobre MySQL real. No se creó commit porque el hook backend no pudo pasar.
