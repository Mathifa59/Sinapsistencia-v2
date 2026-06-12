# Prompt de Claude Code — Migración Sinapsistencia → Angular 21 + Spring Boot 3.5 + PostgreSQL

> Pega esto en Claude Code en la raíz de un repo nuevo. Trabaja **por fases con checkpoints**:
> no avances de fase hasta que la anterior compile y cumpla su criterio de aceptación. Detente al
> final de cada fase y reporta antes de continuar.

---

## CONTEXTO

Estoy migrando **Sinapsistencia**, una plataforma web de mediación médico-legal (conecta médicos con
abogados de derecho médico; gestiona casos, documentos clínicos con trazabilidad, y evaluación de
riesgo asistida por ML explicable). Caso de estudio: Clínica SANNA "El Golf", San Isidro, Lima.

**Stack actual (origen):** Next.js 16 (App Router + BFF) · Supabase (Postgres + Auth + Storage) ·
DDD por módulos con inyección de dependencias · TanStack Query · Zustand · react-hook-form + Zod ·
Radix UI + CVA + Tailwind v4 · servicio ML en FastAPI (repo aparte) · n8n para alertas.

**Stack destino:**
- **Frontend:** Angular 21 (standalone components, signals, control flow `@if/@for`) + Tailwind v4 +
  spartan/ui + `@tanstack/angular-query-experimental` + lucide-angular.
- **Backend:** Spring Boot 3.5.x + Java 21 + Maven · Spring Web · Spring Data JPA · Spring Security 6 ·
  Validation · Flyway · PostgreSQL · springdoc-openapi · `RestClient`.
- **Base de datos:** PostgreSQL en **Railway** (alt: conservar el Postgres de Supabase solo como BD vía
  JDBC). Es solo una `JDBC_URL`; el esquema casi no cambia.
- **Storage:** **Cloudinary** (avatares + docs de soporte). Alt: Supabase Storage o volumen Railway.
- **Despliegue:** Angular → **Vercel**; Spring Boot + FastAPI ML → **Railway**. **No usar Azure.**
- **ML:** FastAPI **se queda igual** (sklearn/TF/PyTorch + XAI SHAP/LIME); Spring lo consume como proxy.
  **No reescribir el ML.**
- **n8n:** se queda igual; Spring dispara el webhook fire-and-forget.

## OBJETIVO INNEGOCIABLE

Preservar **1:1**: tema, colores, tipografía (Geist), comportamientos, contrato de API
(`{success,data}` / `{success,error}`), paths `/api/...`, valores de enums en español, ruteo por rol
(`/doctor` `/lawyer` `/admin`) y la lógica de negocio. La app nueva debe verse y comportarse idéntica.

## REGLAS GLOBALES

1. **No reescribas el servicio ML.** Solo créale un proxy en Spring (`MlController` → FastAPI vía
   `RestClient`, timeout 5s) que normalice a camelCase y dispare n8n **solo** si
   `riskLevel ∈ {alto, critico}`.
2. **Mismos paths de endpoints** que el BFF actual (lista abajo). Mismo sobre de respuesta.
3. **Enums en español exactos** vía `AttributeConverter` (nunca cambiar los strings).
4. **RLS → ownership en la capa de servicio**: cada query filtra por el usuario autenticado; un
   doctor jamás ve datos de otro. Esto FALLA ABIERTO si lo olvidas, así que enfórzalo explícito y
   testéalo.
5. **Diseño:** porta `globals.css` y los tokens de Tailwind **verbatim**; reimplementa cada primitivo
   `components/ui/*` con las mismas clases/variantes (usa spartan/ui como base). Nada de plantillas
   genéricas.
6. **Estructura DDD por módulo** en backend (`web/application/domain/infrastructure`) y core/shared/
   features en frontend.
7. Java 21, Spring Boot 3.5.x (NO 4.0). Angular 21 (NO 22).
8. Al terminar cada fase: compila, corre, y reporta el criterio de aceptación. Espera mi OK.

## ENDPOINTS A RECREAR (mismos paths)

```
POST /api/auth/login · POST /api/auth/logout · GET /api/auth/me · POST /api/auth/register
GET|POST /api/legal-cases · GET|PUT /api/legal-cases/{id}
GET|POST /api/documents · GET|PUT /api/documents/{id}
GET|POST /api/patients · GET|PUT /api/patients/{id}
GET|POST /api/users · GET|PUT|PATCH /api/users/{id}
GET /api/audit · GET|PUT /api/profile · POST /api/profile/avatar
GET /api/matching/doctors · GET|POST /api/matching/lawyers
GET|POST|PATCH /api/matching/contact-requests · GET /api/matching/relevant-cases
POST /api/ml/risk · GET /api/ml/health
```

## ESQUEMA (12 tablas, recréalo en Flyway)

`profiles` (raíz: id, email, name, role, is_active, avatar_url, **password_hash**),
`doctor_profiles` (cmp, specialty, sub_specialties, hospital, years_experience, languages, embedding),
`lawyer_profiles` (cab, specialties, medical_areas, rating, resolved_cases, embedding),
`admin_profiles` (department, permissions[]), `hospitals`, `patients`, `clinical_episodes`,
`cases` (→ doctor, lawyer, patient, episode), `documents` (→ author, case, episode, patient,
current_version_id), `document_versions`, `document_signatures` (type, hash, is_valid),
`contact_requests` (message, status, ml_score, response_message), `match_recommendations`
(score, reasons[], algorithm_version, is_accepted), `audit_logs`.

**Enums (valores exactos):** `user_role`(doctor/lawyer/admin) · `case_priority`(baja/media/alta/critica) ·
`case_status`(nuevo/en_revision/activo/cerrado/archivado) ·
`contact_request_status`(pendiente/aceptado/rechazado/cancelado) ·
`document_status`(borrador/pendiente_firma/firmado/archivado) ·
`document_type`(historia_clinica/consentimiento_informado/informe_medico/receta/orden_laboratorio/certificado_medico/documento_legal/otro) ·
`patient_gender`(M/F/other) · `signature_type`(digital/huella/firma_manuscrita).
Extensión `vector` (pgvector) para columnas `embedding` (déjalas; el matching actual es por
specialty/medical_areas).

---

## ALINEACIÓN ACADÉMICA (opcional — recomendada para la nota de la tesis)

Por defecto, esta migración preserva el modelo actual **1:1**. Si quieres alinear al Charter/Backlog
(lo que evalúa el jurado), activa estos cambios. Son de **contenido**, no de infra:

1. **Datos de paciente NO identificables (Ley 29733).** Reemplaza `patients`/`clinical_episodes` por una
   entidad de **contexto simulado** (`age_reference`, `medical_area`, `event_date`, `summary`,
   `relevant_factors`, `is_simulated=true`). Sin DNI, nombre ni datos identificables.
2. **Estados de consulta (HU-16):** usa el flujo de 6 estados **Pendiente → Clasificada → Asignada →
   En revisión → Respondida → Cerrada** en vez del `case_status` actual. En UI llama "consulta" al caso.
3. **HUs por agregar:** clasificación ML (HU-29), priorización (HU-30), explicación XAI del matching
   (HU-31/32 con factores SHAP/LIME + mensajes "es apoyo, no decisión" HU-43), dashboards por rol
   (HU-36/37/38), reporte básico de consulta (HU-39), métricas del modelo (HU-35).
4. **Indicadores que condicionan diseño:** tiempo de respuesta ≤ 2 s, matching ≥ 70 % con fallback de
   cold-start (reglas + modelo simple + round-robin por especialidad), usabilidad SUS ≥ 70.

> Si NO activas esta sección, construye el modelo actual tal cual (5 estados, `patients` como está).

---

## FASES (ejecutar en orden, con checkpoint al final de cada una)

### FASE 0 — Scaffold
- `backend/`: Spring Boot 3.5.x, Java 21, Maven. Dependencias: web, data-jpa, security, validation,
  actuator, postgresql, flyway-core + flyway-database-postgresql, jjwt (0.12.x),
  springdoc-openapi-starter-webmvc-ui, (test) starter-test + testcontainers-postgresql.
- `frontend/`: Angular 21 (`ng new`, standalone, routing, sin zone si aplica), Tailwind v4,
  spartan/ui (o @angular/cdk), @tanstack/angular-query-experimental, lucide-angular, clsx,
  tailwind-merge, class-variance-authority, @fontsource-variable/geist.
- `docker-compose.yml`: Postgres 16 para dev (Cloudinary es servicio externo, no necesita contenedor).
- **Aceptación:** ambos proyectos arrancan vacíos; `docker compose up` levanta Postgres.

### FASE 1 — Base de datos (Flyway)
- `V1__schema.sql`: extensión vector, enums, 12 tablas, FKs, índices (índices en doctor_id, lawyer_id,
  patient_id, status, priority para los filtros).
- `V3__seed_demo.sql`: hospitales catálogo + usuarios demo (doctor.demo@/lawyer.demo@/admin.demo@
  sinapsistencia.pe) con `password_hash` BCrypt y sus perfiles por rol.
- **Aceptación:** `./mvnw flyway:migrate` ok; seed visible.

### FASE 2 — Núcleo backend
- `shared/api/ApiResponse<T>` (record) + `GlobalExceptionHandler` (`@RestControllerAdvice` →
  `{success:false,error}`) + `PageResponse<T>`.
- Entidades JPA de las 12 tablas + Java enums con `AttributeConverter` (strings exactos).
- Repositorios Spring Data JPA.
- `SecurityConfig` esqueleto (stateless), `CorsConfig`, `OpenApiConfig`.
- **Aceptación:** compila; `/actuator/health` UP; Hibernate valida el esquema contra Flyway.

### FASE 3 — Auth (espeja Supabase Auth + proxy.ts)
- `AuthController`: `POST /login` (modo email+password con `AuthenticationManager`/BCrypt **y** modo
  `{role}` demo), `POST /logout`, `GET /me`, `POST /register`. Rechaza `is_active=false`.
- `JwtService` (jjwt): claims sub/role/email/name; emite a cookie httpOnly `access_token`.
- `JwtAuthFilter` puebla `SecurityContext` con `ROLE_DOCTOR/LAWYER/ADMIN`.
- Protección por prefijo + `@PreAuthorize` a nivel método.
- **Aceptación:** login demo y email+password devuelven JWT; `/me` resuelve perfil; rutas protegidas
  rechazan sin token.

### FASE 4 — Dominio (cases, documents, patients, users, audit, profile)
- Por cada módulo: `web` (controller, mismos paths/contrato), `application` (service con casos de uso),
  `domain` (entidades/value objects, snapshots aplanados como `DoctorSnapshot{fullName}`),
  `infrastructure` (repos + DTO projections para evitar N+1).
- Paginación (`Pageable`→`PageResponse`, espeja `.range()`), filtros `status/priority/search`.
- **RLS replacement**: ownership por rol en cada query (doctor ve lo suyo; lawyer lo asignado + casos
  sin abogado relevantes; admin todo). 403 al acceder a recursos ajenos.
- `POST /api/profile/avatar`: sube a **Cloudinary** y devuelve la URL (mismo patrón para docs de soporte).
- Firma de documentos: genera `hash` real al firmar.
- **Aceptación:** cada endpoint responde con el contrato exacto; tests de ownership pasan (un doctor
  NO ve casos de otro).

### FASE 5 — Matching + ML proxy + n8n
- `MatchingController`: doctors, lawyers (recomendaciones), contact-requests (GET/POST/PATCH),
  relevant-cases (casos sin abogado relevantes al perfil legal por specialty↔medical_areas).
- `MlController` + `MlProxyService` (`RestClient` → `${ML_SERVICE_URL}/api/v1/risk-assessment`,
  timeout 5s; normaliza a camelCase; si `riskLevel ∈ {alto,critico}` → `N8nNotifier`).
  `/api/ml/health` consulta `/health` + `/api/v1/model/info` en paralelo.
- `N8nNotifier`: `@Async` + `RestClient` (timeout 3s, swallow errors) → `${N8N_WEBHOOK_URL}/webhook/risk-alert`.
- **Aceptación:** risk assessment proxea y dispara n8n solo en alto/critico; health refleja online/offline;
  si n8n cae, la app sigue.

### FASE 6 — Frontend: base y diseño
- Porta `globals.css` + tokens Tailwind **verbatim**. Fuente Geist.
- `shared/ui/`: reimplementa primitivos (Button, Badge, Input, Textarea, Label, Card, Separator,
  Avatar, Dialog, Dropdown-menu, Select, Tabs, Tooltip) con spartan/ui — mismas variantes/clases.
- `shared/layout/DashboardLayout` + `navigation/Sidebar` (nav dinámica por rol) + `Topbar`.
- `core/api/api.service.ts` (wrapper `{success,data}` → throw si `!success`).
- `core/auth`: `AuthService` (signals, reemplaza Zustand), `authGuard`/`roleGuard` (functional,
  reemplazan proxy.ts), `authInterceptor` (withCredentials).
- Genera el cliente TS desde OpenAPI (`springdoc` → `openapi-generator`) en `core/api/generated`.
- `app.config.ts`: `provideHttpClient(withInterceptors)`, `provideRouter`, `provideTanStackQuery`.
- **Aceptación:** login funciona contra el backend; el shell (sidebar/topbar/tema) es visualmente
  idéntico al actual.

### FASE 7 — Frontend: features por portal
- **Doctor:** dashboard, cases, cases/{id}, documents, lawyers, risk, profile.
- **Lawyer:** dashboard, requests, doctors, profile.
- **Admin:** dashboard, users, patients, episodes, documents, audit, profile.
- Cada feature: componentes standalone + angular-query (porta `query-keys.ts`) + cliente generado +
  Reactive Forms (reemplaza react-hook-form). Modales (CaseFormModal, PatientFormModal, UserFormModal,
  document detail/form) y badges de estado replicados.
- **Aceptación:** paridad funcional y visual con el actual, portal por portal.

### FASE 8 — Cierre + Despliegue
- Storage prod (**Cloudinary**), `AuditAspect` (AOP → audit_logs con ip/user-agent), `.env.example`
  (backend y frontend), README con "cómo correr", smoke tests, regenerar cliente OpenAPI.
- **Despliegue:** Angular → **Vercel** (build estático/SSR). Spring Boot → **Railway** (Dockerfile o
  Nixpacks, Java 21). FastAPI ML → **Railway** (ya existente). Postgres → **Railway** (o Supabase como
  BD). Configurar variables de entorno en cada plataforma y CORS del backend hacia el dominio de Vercel.
- **Aceptación:** stack completo corriendo en Vercel + Railway; checklist de paridad (abajo) en verde.

---

## CHECKLIST DE PARIDAD (verificar al final)

- [ ] Sobre `{success,data}` / `{success,error}` idéntico.
- [ ] Paths `/api/...` idénticos y query params de filtros/paginación iguales.
- [ ] Enums en español exactos (round-trip ok).
- [ ] Ruteo por rol + redirecciones (sin sesión → `/login?redirect=`; prefijo ≠ rol → dashboard).
- [ ] Login demo por rol + rechazo de `is_active=false`.
- [ ] ML: normalización camelCase + n8n solo en alto/critico; n8n fire-and-forget no tumba la app.
- [ ] Firma: hash real al firmar.
- [ ] Tema/colores/tipografía/glassmorphism portados verbatim; labels en español.
- [ ] Ownership: un usuario nunca ve datos de otro (tests).

---

## VARIABLES DE ENTORNO

**Backend (`application.yml` / `.env`):** `DB_URL/USER/PASSWORD` (Railway Postgres o Supabase),
`JWT_SECRET`, `JWT_EXPIRATION`, `ML_SERVICE_URL` (http://localhost:8000 dev / URL Railway en prod),
`N8N_WEBHOOK_URL`, `CLOUDINARY_URL` (o `CLOUDINARY_CLOUD_NAME/API_KEY/API_SECRET`),
`CORS_ALLOWED_ORIGINS` (dominio de Vercel).
**Frontend:** `API_BASE_URL` (http://localhost:8080 dev / URL Railway del backend en prod).

---

**Empieza por la FASE 0. Crea la estructura, confirma que ambos proyectos arrancan, y reporta antes de
seguir a la FASE 1.**
