# Glosario de endpoints — Backend Spring Boot

> Base URL local: `http://localhost:8080`. Todas las respuestas usan el envelope
> `ApiResponse<T>` (`{ success, data, error }`). El JWT viaja en cookie httpOnly
> `access_token` (Spring Security).

## Auth — `/api/auth`

| Método | Path | Auth | Descripción |
|--------|------|------|-------------|
| POST | `/api/auth/login` | público | Login por `email`+`password`, o por `role` (modo demo). Setea cookie `access_token`. |
| POST | `/api/auth/logout` | público | Expira la cookie `access_token`. |
| GET | `/api/auth/me` | autenticado | Devuelve el usuario de la sesión actual. |
| POST | `/api/auth/register` | público | Crea cuenta nueva (médico o abogado). `201 Created`. |

## Perfil — `/api/profile`

| Método | Path | Auth | Descripción |
|--------|------|------|-------------|
| GET | `/api/profile?userId=` | autenticado | Perfil propio, o de `userId` (admin). |
| PATCH | `/api/profile` | autenticado | Actualiza perfil propio. |
| PUT | `/api/profile` | autenticado | Alias de `PATCH` (mismo comportamiento). |
| POST | `/api/profile/avatar` | autenticado | Sube avatar (multipart `file`) a Cloudinary, devuelve `avatarUrl`. |

## Consultas legales — `/api/legal-cases`

| Método | Path | Auth | Descripción |
|--------|------|------|-------------|
| GET | `/api/legal-cases?status&priority&doctorId&search&page&pageSize` | autenticado | Lista paginada (filtrada por rol). |
| POST | `/api/legal-cases` | autenticado | Crea una consulta. `201 Created`. |
| GET | `/api/legal-cases/{id}` | autenticado | Detalle de una consulta. |
| PUT | `/api/legal-cases/{id}` | autenticado | Actualiza una consulta. |

## Documentos — `/api/documents`

| Método | Path | Auth | Descripción |
|--------|------|------|-------------|
| GET | `/api/documents?status&type&authorId&search&page&pageSize` | autenticado | Lista paginada. |
| POST | `/api/documents` | autenticado | Crea documento. `201 Created`. |
| GET | `/api/documents/{id}` | autenticado | Detalle de un documento. |
| PUT | `/api/documents/{id}` | autenticado | Actualiza un documento. |

## Matching — `/api/matching`

| Método | Path | Auth | Descripción |
|--------|------|------|-------------|
| GET | `/api/matching/doctors` | autenticado | Directorio de médicos. |
| GET | `/api/matching/lawyers` | autenticado | Sin `doctorId`: directorio de abogados. Con `doctorId`: recomendaciones ML para ese médico. |
| POST | `/api/matching/lawyers` | autenticado | Genera y persiste recomendaciones (con factores XAI), opcionalmente para un `caseId`. `201 Created`. |
| GET | `/api/matching/contact-requests?lawyerId&doctorId&status` | autenticado | Lista solicitudes de contacto. |
| POST | `/api/matching/contact-requests` | autenticado | Crea solicitud de contacto médico→abogado (`fromDoctorId`, `toLawyerId`, `message`, `caseId`). `201 Created`. |
| PATCH | `/api/matching/contact-requests` | autenticado | Responde una solicitud (`requestId`, `status`, `responseMessage`). |
| GET | `/api/matching/relevant-cases?lawyerId` | autenticado | Casos relevantes para un abogado (matching médico↔abogado). |

> ⚠️ Las rutas que dependen de `RecommendationService` / `RelevantCasesService`
> consumen el `ml-service` (vía `MlProxyService`) para el scoring TF-IDF/coseno —
> ver sección ML.

## ML (proxy a `ml-service`) — `/api/ml`

| Método | Path | Auth | Descripción |
|--------|------|------|-------------|
| POST | `/api/ml/risk` | autenticado | Evaluación de riesgo (Random Forest). Si `riskLevel` es `alto`/`critico`, dispara alerta a n8n (fire-and-forget). |
| GET | `/api/ml/health` | público | Estado del `ml-service` (proxy a `GET /health` en FastAPI). |

`ml-service` (FastAPI, `http://localhost:8000`) expone internamente:
- `GET /health`
- `GET /api/v1/model/info`
- `POST /api/v1/risk-assessment`
- `POST /api/v1/recommendations`

## Usuarios — `/api/users` (solo ADMIN)

| Método | Path | Auth | Descripción |
|--------|------|------|-------------|
| GET | `/api/users?role=` | ADMIN | Lista usuarios, opcionalmente filtrados por rol. |
| POST | `/api/users` | ADMIN | Crea usuario (`name`, `email`, `role`, `password`). `201 Created`. |
| GET | `/api/users/{id}` | ADMIN | Detalle de un usuario. |
| PUT | `/api/users/{id}` | ADMIN | Actualiza `name`/`role`. |
| PATCH | `/api/users/{id}` | ADMIN | Activa/desactiva el usuario (toggle). |

## Auditoría — `/api/audit` (solo ADMIN)

| Método | Path | Auth | Descripción |
|--------|------|------|-------------|
| GET | `/api/audit?action&userId&resource&search&page&pageSize` | ADMIN | Bitácora de auditoría paginada. |

> Nota: este endpoint es admin-only. El dashboard de médico llamaba a este
> endpoint y producía `403 Forbidden`; el bug fue corregido eliminando esa
> llamada (ver [ESTADO_VERIFICACION.md](ESTADO_VERIFICACION.md)).

## Endpoints auditados automáticamente (`@Auditable`)

Estos endpoints generan una entrada en `/api/audit` al ejecutarse:
`login`, `logout`, `legal-cases` (create/update), `documents` (create/update),
`profile` (update/avatar), `matching/contact-requests` (create/update),
`users` (create/update).
