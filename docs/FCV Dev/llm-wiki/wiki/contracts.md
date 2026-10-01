# Contratos REST

## HECHO

El PRD exige REST/JSON entre `citas-web` y `citas-api` y validación cross-repo en funcionalidades clave.

## DECISIÓN — 2026-09-17 · HU-004/005/006/007

El contrato inicial cubre solamente autenticación bajo `/api/v1/auth`:

| Operación | Entrada | Éxito |
|---|---|---|
| `POST /register` | JSON `firstName`, `lastName`, `documentType`, `documentNumber`, `email`, `phone`, `password` | `201`, JSON con `id`, datos públicos y rol `USER`, sin contraseña |
| `POST /login` | JSON `email`, `password` | `200`, JSON `accessToken`, `tokenType=Bearer`, `expiresIn`; cookie `refresh_token` |
| `POST /refresh` | Cookie `refresh_token` | `200`, nuevo access en JSON y nueva cookie refresh; la anterior se revoca |
| `POST /logout` | Cookie `refresh_token` | `204`, revocación de la sesión y cookie borrada |

Email se normaliza con trim y minúsculas. Documento es único por `(documentType, documentNumber)` normalizados. La contraseña de registro es obligatoria y se limita a 72 bytes UTF-8 por el límite de BCrypt; sus espacios no se alteran. Solo se permite autoregistro `USER`. Errores: `400` validación, `409` duplicidad, `401` credencial/refresh inválido, `403` rol insuficiente, en formato Problem Details; login no revela qué credencial falló.

Access JWT y refresh JWT usan secretos distintos, tipo explícito y duraciones configurables (valores iniciales: 15 minutos y 7 días). El access lleva `sub` y roles. El refresh lleva `sub` y `jti`; su identificador se guarda solo como hash en una sesión persistida. Un refresh válido rota ambos tokens atómicamente. Logout revoca solo la sesión indicada; un access emitido conserva validez hasta su expiración.

Para sitios distintos, la cookie es `HttpOnly; Secure; SameSite=None`, con `Path=/api/v1/auth`. CORS permite credenciales únicamente al `FRONTEND_ORIGIN` configurado. Login, refresh y logout requieren `Origin` permitido cuando se envía y `X-Requested-With: XMLHttpRequest`; el perfil HTTP local usa cookie `SameSite=Lax` sin `Secure`. El cliente futuro deberá enviar credenciales y ese encabezado, guardar access únicamente según su diseño aprobado y eliminar su estado local al salir.

### Impacto cross-repo antes del cambio REST

- `citas-api`: nuevo `pom.xml`, código de dominio/aplicación/adaptadores, migración Flyway, configuración, pruebas y este contrato.
- `citas-web`: sin cambios en este incremento; HU-033 integrará las cuatro rutas, cookie y errores. Al ser endpoints nuevos, no hay cliente previo que migrar.
- Compatibilidad: `/api/v1` fija la versión de este contrato; cambios posteriores requieren revisión de ambas partes. Migración: esquema inicial de identidad por Flyway. Pruebas: REST, seguridad, persistencia y `mvn test` en backend; prueba cross-repo cuando exista el cliente.

## PREGUNTA ABIERTA

Las rutas, filtros, paginación y formatos de fecha/hora de las demás HU siguen sin contrato aprobado.

## DECISIÓN — 2026-09-22 · Contrato S3 de agendamiento

Todos los recursos S3 usan `/api/v1`, JWT access en `Authorization: Bearer` y JSON. Las fechas se representan como `YYYY-MM-DD` y las horas como `HH:mm` en la zona `America/Bogota`.

| Recurso | Operación | Rol |
|---|---|---|
| Catálogos | `GET /catalogs/{locations|appointment-statuses|roles|regimes}` | autenticado |
| Planes activos para registro | `GET /catalogs/plans` | público |
| Especialidades disponibles | `GET /specialties` | autenticado |
| Especialidades ADMIN | `GET|POST|PATCH /admin/specialties[/{id}]` | ADMIN |
| Profesionales | `POST /admin/professionals`; `PUT /admin/professionals/{id}/specialties|locations`; `PATCH /admin/professionals/{id}/active` | ADMIN |
| Bloques propios | `GET|POST /professional/availability-blocks`; `PATCH|DELETE /professional/availability-blocks/{id}` | PROFESSIONAL |
| Disponibilidad | `GET /availability?locationId=&specialtyId=&date=&professionalId?` | USER |
| Reserva | `POST /appointments` | USER |
| Solicitudes especializadas | `GET /admin/appointments/pending-specialized`; `POST /admin/appointments/{id}/decision` | ADMIN |

`POST /auth/register` acepta `insurancePlanId` opcional; la afiliación es administrativa y no modifica las reglas de agenda. `POST /appointments` recibe `professionalId`, `locationId`, `specialtyId`, `date`, `startTime` y `reason` opcional. La API deriva la naturaleza general o especializada desde la especialidad: devuelve `APPROVED` para general y `REQUESTED` para especializada. Una decisión ADMIN recibe `APPROVE` o `REJECT`; el rechazo exige `reason`.

Errores de validación usan `400`; recursos o relaciones inexistentes usan `404`; rol u ownership usan `403`; slots ocupados, selección inválida o transición no permitida usan `409`. El frontend consume estas rutas directamente, sin BFF, y no guarda citas ni slots como fuente de verdad.

## DECISIÓN — 2026-09-22 · Corte web de autenticación

`citas-web` consume las cuatro operaciones de autenticación directamente con `VITE_API_URL` (valor local: `http://localhost:8080`). Envía `credentials: include` y `X-Requested-With: XMLHttpRequest` en login, refresh y logout. El access JWT permanece solo en memoria; el refresh se mantiene en cookie `HttpOnly` y se rota al restaurar la sesión. La interfaz no registra ni muestra tokens o contraseñas.

El CORS permite exclusivamente `FRONTEND_ORIGIN`, métodos `POST`, `GET`, `OPTIONS`, encabezados `Content-Type`, `Authorization`, `X-Requested-With` y credenciales. La base de referencia ya existente utiliza `BIGINT` para usuarios, `roles.code` y `refresh_tokens`; Flyway hace baseline en versión 0 y `V1` es compatible con ese esquema 3FN.

## DECISION - 2026-09-25 - S4 lifecycle REST contract

The user approved HU-025/026/027/028/029/030/032/033 for this cut and approved the routes proposed in `S4_BASELINE.md`. All endpoints use `/api/v1`, JSON, and access JWT in `Authorization: Bearer`. Appointment dates and times use `YYYY-MM-DD` and `HH:mm` in `America/Bogota`. Errors retain the existing Problem Details mapping: 400 invalid input, 401 invalid session, 403 role/ownership denial, 404 absent resource, 409 slot conflict or invalid transition.

| Operation | Route | Access |
|---|---|---|
| List own appointments; optional `status`, `from`, `to` filters | `GET /user/appointments` | USER |
| Read own appointment | `GET /user/appointments/{id}` | USER |
| Cancel eligible own appointment; optional `{reason}` body | `POST /user/appointments/{id}/cancellation` | USER |
| Request rescheduling; `{date, startTime}` body | `POST /user/appointments/{id}/rescheduling-requests` | USER |
| List pending reschedules; optional `locationId`, `professionalId`, `specialtyId`, `date` filters | `GET /admin/rescheduling-requests` | ADMIN |
| Decide with `{decision: APPROVE|REJECT, reason?}`; rejection requires reason | `POST /admin/rescheduling-requests/{id}/decision` | ADMIN |
| List own approved appointments; `from`, `to`, optional `locationId` | `GET /professional/appointments` | PROFESSIONAL |
| Close appointment as `COMPLETED` or `NO_SHOW` | `PATCH /professional/appointments/{id}/status` | PROFESSIONAL |
| Read status history | `GET /appointments/{id}/status-history` | USER owner, PROFESSIONAL assigned, ADMIN global |

Appointment responses contain `id`, location `{id,name}`, professional `{id,displayName}`, specialty `{id,name}`, `date`, `startTime`, `endTime`, `durationMinutes`, `status`, and `rejectionReason` when present. Professional agenda responses contain no patient personal data. Rescheduling responses contain request id, related appointment, requested slot, status, reason, and relevant timestamps. History responses contain new status, nullable actor id, source `SYSTEM|USER|ADMIN`, timestamp, and optional reason.

HU-030 eligibility: the appointment must be assigned to the authenticated professional, have status `APPROVED`, and its scheduled end must have passed in `America/Bogota`; the professional may select `COMPLETED` or `NO_SHOW`. HU-032 access: USER reads own appointment history; PROFESSIONAL reads history for appointments assigned to them; ADMIN reads any appointment history.

Compatibility: authentication and S3 routes remain unchanged; lifecycle adds routes under `/api/v1`. A migration after V2 preserves current appointments and history. A `PENDING` reschedule retains its new slots without freeing the original slots. `citas-web` consumes these routes directly. Migration, backend tests, client types/API/screens, and evidence in both repositories are required before closure.
