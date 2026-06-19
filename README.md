# Sinapsistencia v2

Plataforma web de **mediación médico-legal** (conecta médicos con abogados de derecho médico;
gestiona consultas, documentos clínicos con trazabilidad y evaluación de riesgo asistida por ML
explicable). Caso de estudio: Clínica SANNA "El Golf", San Isidro, Lima.

Migración de **Next.js 16 + Supabase** → **Angular 21 + Spring Boot 3.5 (Java 21) + PostgreSQL**,
preservando 1:1 el tema, el contrato de API, los enums en español y el ruteo por rol.

## Stack

| Capa | Tecnología |
|------|-----------|
| Frontend | Angular 21 (standalone, signals, control flow) + Tailwind v4 + spartan/ui + TanStack Query |
| Backend | Spring Boot 3.5 + Java 21 + Spring Data JPA + Spring Security 6 + Flyway |
| Base de datos | PostgreSQL 16 + pgvector |
| Storage | Cloudinary |
| ML | FastAPI (`ml-service/`) — Random Forest (riesgo) + TF-IDF/coseno (matching); Spring lo consume como proxy |
| Automatización | n8n (webhook fire-and-forget para alertas de riesgo) |
| Despliegue | Frontend → Vercel · Backend + ML → Railway |

## Estructura del monorepo

```
sinapsistencia-v2/
├── backend/            # Spring Boot 3.5 (Maven, Java 21) — DDD por módulo
├── frontend/           # Angular 21
├── ml-service/         # FastAPI — Random Forest (riesgo) + TF-IDF/coseno (matching)
├── n8n/                # workflow JSON de alertas (referencia, sin cambios)
├── docs/               # blueprint + prompt de migración
├── _legacy/            # Next.js + Supabase (SOLO LECTURA, referencia)
└── docker-compose.yml  # Postgres 16 + pgvector para dev local
```

## Requisitos

- **JDK 21** (Microsoft OpenJDK / Temurin)
- **Node 20+** y **npm**
- **Docker Desktop** (para el Postgres de desarrollo)

## Cómo correr (desarrollo local)

### 1. Base de datos

```bash
docker compose up -d        # levanta Postgres 16 + pgvector en localhost:5433
```

### 2. Backend (http://localhost:8080)

```bash
cd backend
./mvnw spring-boot:run      # macOS/Linux
.\mvnw.cmd spring-boot:run  # Windows
```

Health check: http://localhost:8080/actuator/health · Swagger UI: http://localhost:8080/swagger-ui.html

> **Notas para Windows (entorno de la tesis):**
>
> 1. **Proxy con inspección TLS:** si Maven falla con `PKIX path building failed`, tu JDK no
>    confía en el CA raíz del proxy. Usa el almacén de certificados de Windows exportando antes
>    de correr Maven:
>    ```powershell
>    $env:MAVEN_OPTS = "-Djavax.net.ssl.trustStoreType=WINDOWS-ROOT -Djavax.net.ssl.trustStore=NONE"
>    ```
>    (No se comitea porque `WINDOWS-ROOT` solo existe en JVM de Windows y rompería Railway/Linux.)
>
> 2. **`C:\Temp` debe existir:** con el perfil de usuario redirigido por OneDrive (o con reparse
>    points), el self-pipe AF_UNIX del selector NIO de Java falla y Tomcat no arranca
>    (`Unable to establish loopback connection`). El `pom.xml` tiene un perfil auto-activado en
>    Windows que apunta `TEMP`/`TMP` a `C:\Temp` para los procesos forkeados. Crea la carpeta una
>    vez: `New-Item -ItemType Directory C:\Temp`. (En Railway/Linux no aplica.)

### 3. Frontend (http://localhost:4200)

```bash
cd frontend
npm install
npm start
```

### 4. Servicio ML (http://localhost:8000)

```bash
cd ml-service
python -m venv .venv && .venv/Scripts/activate   # Windows
pip install -r requirements.txt
cd training && python generate_risk_dataset.py && python train_risk_model.py && cd ..
uvicorn app.main:app --reload --port 8000
```

Ver [ml-service/README.md](ml-service/README.md) — Random Forest (riesgo) + TF-IDF/coseno (matching).

## Usuarios demo

| Rol | Email | Password |
|-----|-------|----------|
| Médico | `doctor.demo@sinapsistencia.pe` | `Demo123!` |
| Abogado | `lawyer.demo@sinapsistencia.pe` | `Demo123!` |
| Administrador | `admin.demo@sinapsistencia.pe` | `Demo123!` |

## Variables de entorno

Ver `backend/.env.example` y `frontend/.env.example`. En producción se configuran en Railway
(backend/ML) y Vercel (frontend).

## Flujos de usuario

Ver [docs/FLUJOS_USUARIO.md](docs/FLUJOS_USUARIO.md) — mapeo exhaustivo de todos los
procesos que puede realizar cada rol (Médico, Abogado, Administrador), incluyendo los
flujos cruzados entre roles (médico→abogado, alertas a n8n, auditoría centralizada, etc.).

## Estado de la migración

Se ejecuta **por fases con checkpoints** (ver `docs/CLAUDE_CODE_PROMPT_Migracion_Sinapsistencia.md`).

- [x] **Fase 0 — Scaffold:** backend Spring Boot + frontend Angular + docker-compose.
- [x] **Fase 1 — Base de datos (Flyway):** `V1__schema.sql` (17 tablas, alineación académica:
      6 estados de consulta HU-16, contexto simulado Ley 29733, tablas ML/XAI/métricas) +
      `V3__seed_demo.sql` (hospitales + usuarios demo, password `Demo123!`).
- [x] **Fase 2 — Núcleo backend:** contrato `ApiResponse`/`PageResponse` + `GlobalExceptionHandler`,
      17 entidades JPA con `AttributeConverter` (enums en español exactos), 17 repositorios
      Spring Data, `SecurityConfig` stateless + CORS + OpenAPI. Hibernate valida contra Flyway.
- [x] **Fase 3 — Auth:** login (email+password y modo demo `{role}`), JWT (jjwt) en cookie
      httpOnly `access_token` + Bearer, `JwtAuthFilter` → `ROLE_DOCTOR/LAWYER/ADMIN`,
      logout, `/me`, register con perfiles profesionales. Mensajes de error del legacy.
- [x] **Fase 4 — Dominio:** módulos cases (consultas + contexto simulado), documents
      (versiones + firma con hash SHA-256 real), users, audit, profile (+avatar Cloudinary).
      Ownership por rol en cada query (reemplazo de RLS) con 8 tests de integración.
- [x] **Fase 5 — Matching + ML proxy + n8n:** directorios, recomendaciones ML con explicación
      XAI persistida (HU-31/32) y fallback cold-start por especialidad, contact-requests
      (aceptar → consulta `en_revision`, HU-18), relevant-cases; `MlController` proxy
      (camelCase, n8n solo en alto/critico, fire-and-forget). Mocks de dev en `tools/mocks/`.
- [x] **Fase 6 — Frontend: base y diseño:** scaffold Angular 21 (standalone, signals, control
      flow), Tailwind v4 + spartan/ui replicando tema/tipografía (Geist) del legacy 1:1,
      TanStack Query, capa de API (`ApiService` + clientes generados desde OpenAPI), UI
      compartida (badges de estado, stat cards, diálogos, selects).
- [x] **Fase 7 — Frontend: features por portal:** 7 páginas portal médico, 4 portal abogado,
      5 portal admin, ruteo completo por rol (`/doctor` `/lawyer` `/admin`) verificado contra
      el backend real (build + checks visuales).
- [ ] **Fase 8 — Cierre + despliegue** (en curso):
  - [x] `AuditAspect` (AOP → `audit_logs` con ip/user-agent) sobre login/logout, CRUD de
        consultas/documentos/usuarios, contact-requests y perfil (incluye detección de
        firma HU-34: `update` documento → `sign` si queda `firmado`).
  - [x] Storage Cloudinary en producción (config condicional + `ProfileService.uploadAvatar`).
  - [x] `.env.example` (backend y frontend).
  - [x] Smoke tests E2E manuales (ver "Verificación end-to-end" abajo).
  - [ ] Regenerar cliente OpenAPI (si cambian DTOs/endpoints).
  - [ ] Despliegue: Angular → Vercel, Spring Boot → Railway, FastAPI ML → Railway, Postgres →
        Railway; variables de entorno y CORS.

## Verificación end-to-end

> Última verificación manual: 2026-06-19, con los 3 servicios corriendo localmente
> (Postgres vía Docker, Backend Spring Boot en :8080, ml-service FastAPI en :8000,
> Frontend Angular en :4200) y navegador controlado para simular usuarios reales.

**Arranque de servicios**
- [x] Backend Spring Boot: arranca limpio (`Started SinapsistenciaApplication`), Flyway
      valida el schema, Hikari conecta a Postgres, Tomcat sirve en `:8080`.
- [x] ml-service FastAPI: arranca limpio en `:8000`, modelo Random Forest (`rf-v1`) cargado.
- [x] `GET /api/ml/health` — el backend hace proxy correctamente al ml-service (`200 OK`,
      `status: "online"`).

**Flujos probados en navegador (cuentas demo, los 3 roles)**
- [x] Login médico, abogado y administrador (con email/password y con los botones de
      "Acceso rápido (demo)").
- [x] Evaluación de riesgo médico-legal (HU-29/30): formulario → `POST /api/ml/risk` →
      score, nivel de riesgo, desglose de factores XAI y recomendaciones, todo renderizado.
- [x] Recomendaciones de abogados para un médico (HU-31/32): `GET /api/matching/lawyers?doctorId=`
      con score de compatibilidad y razones explicadas.
- [x] Creación de solicitud de contacto médico → abogado (`POST /api/matching/contact-requests`).
- [x] Respuesta del abogado a una solicitud, tanto **aceptar** como **rechazar**
      (`PATCH /api/matching/contact-requests`) — el dashboard legal actualiza sus contadores
      y "Casos en seguimiento" correctamente en ambos casos.
- [x] Casos relevantes para el abogado (`GET /api/matching/relevant-cases`) — devuelve las
      áreas médicas del perfil del abogado correctamente.

**Bugs encontrados y corregidos en esta verificación** (commit `a38b5be`)
- `lawyers.component.ts` enviaba el `id` de `LawyerProfile` como `toLawyerId` al crear una
  solicitud de contacto, pero el backend resuelve ese campo contra `User.id`
  (`ProfileRepository.findById`). Causaba `404 Abogado no encontrado` al pedir contacto con
  cualquier abogado. Fix: usar `lawyer.userId` en `sendContactRequest()` y `hasActiveRequest()`.
- `risk.component.ts` mostraba `"Modelo vrf-v1"` (prefijo `v` duplicado, ya que
  `modelVersion` del ml-service ya incluye la `v`: `"rf-v1"`). Fix: quitar el prefijo hardcodeado.

**Pendiente de verificar**
- [ ] Panel de Administrador (usuarios, métricas, auditoría).
- [ ] Documentos clínicos: subida, versionado y firma (hash SHA-256).
- [ ] Despliegue real en Vercel/Railway (la verificación de arriba es 100% local).
