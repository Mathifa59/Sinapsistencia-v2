# Blueprint de Migración — Sinapsistencia
### De **Next.js 16 + Supabase + FastAPI** → **Angular 21 + Spring Boot 3.5 + PostgreSQL**

> Objetivo: migrar la app completa **preservando 1:1** el tema, los colores, las funcionalidades
> y los comportamientos. El servicio ML (FastAPI) se mantiene tal cual; solo cambia quién lo llama.
>
> Documento base: la auditoría técnica del estado real del código (2026-06-09).
> Generado: 2026-06-10.

---

## 0. Decisiones de stack (y por qué)

| Capa | Hoy | Destino | Versión | Nota |
|------|-----|---------|---------|------|
| Frontend | Next.js 16 (App Router) | **Angular** | **21.x** (LTS hasta may-2027) | Standalone + signals + nuevo control flow `@if/@for`. No uso Angular 22 (recién sale jun-2026) para evitar breaking changes en plena tesis. |
| Backend | Next API Routes (BFF) | **Spring Boot** | **3.5.x** + **Java 21 (LTS)** | Elijo 3.5 sobre 4.0.6 a propósito: Spring Security 6 + Hibernate 6.6 tienen muchísima más documentación/tutoriales que el 4.0 (Spring Security 7) recién salido. Para una tesis con deadline, estabilidad de ecosistema > estar en el filo. |
| Base de datos | Supabase (Postgres) | **PostgreSQL en Railway** | 16 | **Supabase ES Postgres.** El esquema casi no cambia. Default: Railway Postgres. Alt 0-fricción: conservar el Postgres de Supabase **solo como BD** (Spring conecta por JDBC) y no migrar datos. Es solo una `JDBC_URL`. |
| ORM/migraciones | — (supabase-js) | **Spring Data JPA + Flyway** | — | Flyway versiona el esquema en SQL. |
| Auth | Supabase Auth (JWT cookies) | **Spring Security + JWT (jjwt) + BCrypt** | — | Ahora **tú** eres el identity provider. Es el punto más delicado (ver §6). |
| Storage | Supabase Storage | **Cloudinary** (default) | — | Avatares y docs de soporte. Alt: conservar Supabase Storage (0 migración de archivos) o volumen persistente de Railway. |
| Cloud / Deploy | Vercel + Railway | **Vercel + Railway** (sin cambios) | — | Front Angular → **Vercel**. Spring Boot + FastAPI ML → **Railway**. |
| CI/CD | — | **GitHub Actions** (o auto-deploy de Vercel/Railway) | — | Push → build → deploy. |
| ML | FastAPI (repo aparte) | **FastAPI (sin cambios)** | — | sklearn/TF/PyTorch + **XAI (SHAP/LIME)**. Spring lo consume vía `RestClient` (proxy). En prod, en Railway. |
| Automatización | n8n webhook | **n8n (sin cambios)** | — | Spring lo dispara fire-and-forget con `@Async` + `RestClient`. |

> Java: usa **Java 21** (LTS). Spring Boot 3.5 soporta 17–24; 21 es el sweet spot.
>
> **Despliegue:** Angular en **Vercel**, Spring Boot y FastAPI en **Railway** (donde ya vivía tu ML).
> La BD por defecto es **Railway Postgres**; cambiarla es solo la cadena JDBC.
>
> **Arquitectura:** Spring Boot como un solo deployable modular (DDD por módulos) + el servicio ML
> FastAPI aparte. Esa topología (API + servicio ML separados) ya es "microservicios" suficiente para
> el MVP y despliega limpio en Railway sin fragmentar de más.

---

## 1. La buena noticia: esto es menos brutal de lo que parece

Tres razones por las que esta migración es más manejable de lo que suena "migrar todo":

1. **Tu base de datos ya es PostgreSQL.** Supabase = Postgres + Auth + Storage. El esquema (12 tablas,
   8 enums) se mueve casi literal con `pg_dump`. Lo que realmente migras es **Auth, RLS y Storage**,
   no las tablas.

2. **Tu frontend ya está arquitectónicamente desacoplado.** Usas DDD con use-cases que reciben el
   repositorio por parámetro, y un único `container.ts` que decide la implementación. Eso significa
   que la lógica de dominio (entidades, casos de uso) **se traduce a Angular casi sin cambiar de forma**.

3. **Tu identidad visual vive en archivos framework-agnósticos.** Colores, tipografía y variantes
   están en el theme de Tailwind, en `globals.css` y en las definiciones CVA. Tailwind v4 corre en
   Angular igual que en Next. Portar el look es **copiar tokens + reescribir cada primitivo con las
   mismas clases** (ver §5).

El trabajo duro real está en 3 frentes: **(a) Auth + RLS → seguridad en la capa de aplicación**,
**(b) reescribir el BFF como controladores Spring**, y **(c) portar la UI de React a Angular**.
Todo lo demás es mecánico.

---

## 2. Estructura de repos

Recomiendo **dos repos** (o monorepo con dos carpetas) + el repo ML que ya existe:

```
sinapsistencia/
├── backend/          # Spring Boot 3.5 (Maven, Java 21)
├── frontend/         # Angular 21
├── ml-service/       # FastAPI — SIN CAMBIOS (referencia)
├── n8n/              # workflow JSON existente — SIN CAMBIOS
└── docker-compose.yml  # Postgres para dev local (Cloudinary/Storage es servicio externo)
```

### Backend — layout DDD (espeja tus `modules/`)

```
backend/src/main/java/pe/sinapsistencia/
├── SinapsistenciaApplication.java
├── shared/
│   ├── api/ApiResponse.java          # { success, data } / { success, error }
│   ├── api/GlobalExceptionHandler.java
│   ├── api/PageResponse.java         # paginación (espeja .range())
│   └── audit/AuditAspect.java        # AOP → audit_logs
├── config/
│   ├── SecurityConfig.java
│   ├── JwtAuthFilter.java
│   ├── CorsConfig.java
│   └── OpenApiConfig.java            # springdoc
├── auth/                             # ← módulo auth
│   ├── web/AuthController.java
│   ├── application/AuthService.java
│   ├── domain/{Profile, Role, ...}
│   ├── infrastructure/ProfileRepository.java  (Spring Data JPA)
│   └── security/{JwtService, UserDetailsImpl}
├── cases/        web/ application/ domain/ infrastructure/
├── documents/    web/ application/ domain/ infrastructure/
├── patients/     web/ application/ domain/ infrastructure/
├── users/        web/ application/ domain/ infrastructure/
├── matching/     web/ application/ domain/ infrastructure/
├── audit/        web/ application/ domain/ infrastructure/
├── profile/      web/ application/ ...
└── ml/           web/MlController.java  application/MlProxyService.java  N8nNotifier.java
src/main/resources/
├── application.yml
└── db/migration/
    ├── V1__schema.sql       # extensiones, enums, 12 tablas, FKs, índices
    ├── V2__functions.sql    # (si conservas helpers)
    └── V3__seed_demo.sql    # hospitales + usuarios demo (doctor/lawyer/admin)
```

> Cada módulo conserva tu separación `domain / application(use-cases) / infrastructure / web`.
> Tus `Api*Repository` desaparecen del backend (ahora hablas con la BD directo); su rol lo toman
> los `@Repository` de Spring Data JPA. El concepto de `container.ts` lo reemplaza la inyección de
> dependencias nativa de Spring (`@Service`, `@Repository`, constructor injection).

### Frontend — layout Angular (espeja tu `src/`)

```
frontend/src/app/
├── app.config.ts            # providers (HttpClient, router, query, interceptors)
├── app.routes.ts            # rutas raíz + lazy por rol
├── core/
│   ├── api/api.service.ts    # wrapper { success, data } → throw si !success  (= apiFetch)
│   ├── api/generated/        # cliente TS generado desde OpenAPI (springdoc)
│   ├── auth/auth.service.ts  # signals (= Zustand auth.store)
│   ├── auth/auth.guard.ts    # functional guard (= proxy.ts protección)
│   ├── auth/role.guard.ts    # routing por rol /doctor /lawyer /admin
│   └── auth/auth.interceptor.ts  # adjunta JWT (withCredentials)
├── shared/
│   ├── ui/                   # primitivos (Button, Badge, Card, Dialog...) ← spartan/ui
│   ├── layout/dashboard-layout/
│   └── navigation/{sidebar, topbar}/
├── features/
│   ├── auth/login/  register/
│   ├── doctor/   dashboard cases cases-detail documents lawyers risk profile
│   ├── lawyer/   dashboard requests doctors profile
│   └── admin/    dashboard users patients episodes documents audit profile
└── styles/
    ├── globals.css           # ← portado verbatim
    └── theme tokens (Tailwind v4 @theme)
```

---

## 3. Mapa de equivalencias — Frontend (Next.js → Angular)

| Next.js 16 | Angular 21 | Comentario de migración |
|---|---|---|
| Páginas App Router (`app/doctor/cases/page.tsx`) | Componentes standalone enrutados + `app.routes.ts` (lazy) | 1 página = 1 componente standalone. |
| `layout.tsx` por rol | Componente layout + rutas hijas | El `DashboardLayout` se vuelve un componente con `<router-outlet>`. |
| Server Components | — (no aplica) | Todo pasa a cliente (CSR). SSR opcional con Angular SSR si lo necesitas. |
| `proxy.ts` (auth + ruteo por rol) | **Functional guards** (`authGuard`, `roleGuard`) + **HTTP interceptor** | El guard replica: sin sesión → `/login?redirect=`; prefijo de ruta ≠ rol → dashboard correcto. |
| TanStack Query (`useQuery/useMutation`) | **`@tanstack/angular-query`** | Mantiene el modelo mental 1:1. Tu `query-keys.ts` se porta casi literal. Alternativa nativa: `httpResource` (Angular 19+). Recomiendo angular-query por paridad de comportamiento (cache/loading/error). |
| Zustand (`auth.store`, `ui.store`) | **Servicio inyectable con `signal()`** | `AuthService` y `UiService` con estado en signals + `computed`. Persistencia → `effect()` + localStorage. |
| react-hook-form + Zod | **Reactive Forms** + `Validators` | Forms nativos. Zod puede sobrevivir solo para *parsear/validar respuestas* de API si quieres tipado runtime. |
| Radix UI (Dialog, Dropdown, Tabs, Tooltip...) | **spartan/ui (hlm)** o **Angular CDK** | spartan/ui = shadcn/Radix portado a Angular+Tailwind. Mapea **1:1** con tus primitivos actuales. Muy recomendado. |
| class-variance-authority (CVA) | CVA reusable como función pura, o **`tailwind-variants`** | CVA devuelve strings de clases → se puede llamar desde el componente y bindear con `[class]`. spartan ya trae este patrón. |
| `clsx` + `tailwind-merge` (`cn()`) | **Mismos paquetes** | `cn()` idéntico, sin cambios. |
| `lucide-react` | **`lucide-angular`** | Mismos iconos, mismos nombres. |
| Tailwind v4 (`@tailwindcss/postcss`) | **Tailwind v4 en Angular** (PostCSS) | El theme/tokens se copian **verbatim**. Aquí se preserva el color exacto. |
| Fuente Geist (`next/font`) | `@fontsource-variable/geist` o `@font-face` | Misma fuente. |
| `apiFetch<T>()` + sobre `{success,data}` | `ApiService` (HttpClient) que lanza `Error` si `success===false` | Contrato idéntico → integra con angular-query igual que con TanStack. |
| `Providers` (QueryClientProvider) | `provideTanStackQuery()` en `app.config.ts` | — |

### Preservación del diseño (tu requisito central)

Tu identidad visual está en **tres lugares**, todos portables:

1. **Theme/tokens de Tailwind** (`tailwind.config` / `@theme` en CSS) → se **copia verbatim** al
   setup de Tailwind en Angular. Mismos colores, mismos radios, misma escala.
2. **`globals.css`** (gradientes, glassmorphism, variables CSS) → se **copia verbatim**.
3. **Variantes CVA por componente** (Button, Badge, etc.) → se reescriben en el componente Angular
   equivalente con **las mismas clases y las mismas variantes**.

Estrategia recomendada: usar **spartan/ui** como base de los primitivos (`shared/ui/`). Como es el
equivalente directo de shadcn para Angular, cada Button/Card/Dialog/Dropdown que hoy tienes tiene su
gemelo con la misma estructura y clases. El resultado es **pixel-idéntico** si copias tus tokens.

> Checklist de paridad visual: portar `globals.css` → portar `tailwind` theme → reimplementar
> `components/ui/*` uno por uno con sus variantes → reimplementar `Sidebar`/`Topbar`/`DashboardLayout`
> → verificar cada portal (doctor/lawyer/admin) contra capturas del actual.

---

## 4. Mapa de equivalencias — Backend (Supabase + BFF → Spring Boot)

| Supabase / Next API Route | Spring Boot 3.5 | Comentario |
|---|---|---|
| API Routes (`src/app/api/...`) | `@RestController` por recurso | AuthController, LegalCaseController, DocumentController, PatientController, UserController, AuditController, ProfileController, MatchingController, MlController. **Mismas rutas** `/api/...`. |
| Sobre `{ success, data }` / `{ success, error }` | `record ApiResponse<T>(boolean success, T data, String error)` + `@RestControllerAdvice` | Contrato **idéntico**. El advice convierte excepciones a `{success:false,error}`. |
| supabase-js con joins embebidos (`doctor:profiles!...(...)`) | Spring Data JPA + **DTO projections** / `@EntityGraph` | Evita N+1 con fetch joins o proyecciones a DTO. Devuelves los mismos "snapshots aplanados" (p.ej. `DoctorSnapshot{fullName}`). |
| `.range(from,to)` paginación | `Pageable` + `Page<T>` → `PageResponse` | Mantén el mismo shape de respuesta (items + total). |
| Filtros `status`/`priority`/`search` | `Specification<T>` (Criteria API) o query methods | Mismos query params. |
| **RLS + `get_user_role()`** | **Spring Security `@PreAuthorize` + ownership en queries** | **Cambio conceptual clave**: RLS filtra filas en la BD; en Spring enforced en la capa de servicio. Ej.: un doctor solo ve `findByDoctorId(currentUserId)`. Ver §6. |
| Supabase Auth (`signInWithPassword`) | `AuthenticationManager` + `BCryptPasswordEncoder` | `profiles` gana columna `password_hash`. |
| JWT en cookies httpOnly (`@supabase/ssr`) | JWT propio (jjwt) en cookie httpOnly **o** `Authorization: Bearer` | Para espejar comportamiento actual: cookie httpOnly `access_token`. |
| Cookie `sinapsistencia-role` (para el proxy) | **Se elimina** | El rol viaja como *claim* en el JWT; Angular lo lee de `/auth/me` o del token. |
| Supabase Storage (avatares + docs de soporte) | **Cloudinary** (default) / Supabase Storage / volumen Railway | `ProfileController.uploadAvatar` y el repositorio documental suben al provider y devuelven la URL. Cloudinary: SDK oficial Java, free tier, cero infra. |
| Enums Postgres (`case_status`, etc.) | **Java enum + `AttributeConverter`** (valores string exactos) | Conserva `nuevo`, `en_revision`, `activo`... **idénticos** (ver §7). |
| Columnas `embedding` (pgvector) | Mantener extensión pgvector; columna sin mapear en JPA por ahora | El matching actual es por specialty/medical_areas. Dejar dormido. |
| `database.types.ts` (tipos generados) | **springdoc-openapi → openapi-generator → cliente Angular tipado** | Ganas tipos end-to-end automáticos para el front. |
| `audit_logs` | `AuditLog` entity + **`@Aspect` AOP** (o llamadas explícitas) | Registra action/resource/details/ip/user-agent. |
| `lib/n8n.ts` (fire-and-forget, timeout 3s) | `N8nNotifier` con `@Async` + `RestClient` (timeout corto, swallow errors) | Semántica fire-and-forget idéntica: si n8n cae, loguea y sigue. |
| `/api/ml/risk` (proxy + normaliza + dispara n8n) | `MlController` + `MlProxyService` (`RestClient`, timeout 5s) | Normaliza a camelCase (riskScore, riskLevel...); si `riskLevel ∈ {alto, critico}` → dispara n8n. **Mismo contrato.** |
| `/api/ml/health` | `MlController.health()` (consulta `/health` + `/model/info` en paralelo) | Devuelve online/offline + versiones. |

### Inventario de endpoints a recrear (mismos paths)

```
POST   /api/auth/login          POST  /api/auth/logout      GET  /api/auth/me     POST /api/auth/register
GET    /api/legal-cases         POST  /api/legal-cases
GET/PUT /api/legal-cases/{id}
GET    /api/documents           POST  /api/documents        GET/PUT /api/documents/{id}
GET    /api/patients            POST  /api/patients         GET/PUT /api/patients/{id}
GET    /api/users               POST  /api/users            GET/PUT|PATCH /api/users/{id}
GET    /api/audit               GET/PUT /api/profile        POST /api/profile/avatar
GET    /api/matching/doctors    GET/POST /api/matching/lawyers
GET/POST/PATCH /api/matching/contact-requests              GET /api/matching/relevant-cases
POST   /api/ml/risk             GET  /api/ml/health
```

---

## 5. Migración de la base de datos (la parte fácil)

Tu BD ya es Postgres. Plan:

1. **Exportar el esquema y datos reales de Supabase**
   ```bash
   pg_dump "postgresql://postgres:[PWD]@db.[PROJ].supabase.co:5432/postgres" \
     --schema=public --no-owner --no-privileges > supabase_dump.sql
   ```
   (Excluye `auth.*`, `storage.*` — son de Supabase; esos los reconstruyes tú.)

2. **Crear la BD** (Railway Postgres por defecto; o conservar el Postgres de Supabase como pura BD) y
   **versionar el esquema con Flyway** (`V1__schema.sql`). Recomiendo esto para la tesis (defendible y
   reproducible), e importar **solo los datos simulados** del dump. En dev usas Postgres en
   `docker-compose`; Flyway corre igual en local, en Railway o en Supabase.

3. **Auth**: la tabla `auth.users` de Supabase (passwords) NO viene. Para la tesis:
   - Agrega `password_hash` a `profiles`.
   - Seedea los usuarios demo (`doctor.demo@…`, etc.) con BCrypt en `V3__seed_demo.sql`.
   - Para usuarios reales (si hay), reset de password en primer login.

4. **Enums**: recréalos en Flyway, pero en JPA los manejas como Java enums con converter (§7).

5. **pgvector**: `CREATE EXTENSION IF NOT EXISTS vector;` y deja las columnas `embedding` tal cual.

> Resultado: el esquema queda idéntico, con los datos reales importados, versionado por Flyway.

---

## 6. Auth + RLS → Seguridad en Spring (el punto crítico)

Esto concentra ~40% del esfuerzo conceptual. Hoy Supabase te regala: hashing de passwords, emisión de
JWT, refresco de sesión y **RLS** (filtrado de filas por usuario/rol en la BD). Al salir de Supabase,
**todo eso lo asume Spring**.

### 6.1 Identidad y login

- `profiles` se vuelve tu tabla de usuarios (id, email, name, role, is_active, avatar_url, **password_hash**).
- `POST /api/auth/login` con dos modos (igual que hoy):
  - **email + password**: `AuthenticationManager.authenticate(...)`, valida `is_active`, emite JWT.
  - **rol demo** `{role}`: mapea a cuenta demo seedeada, emite JWT.
- JWT (jjwt): claims `sub=userId`, `role`, `email`, `name`. Lo dejas en **cookie httpOnly `access_token`**
  (espeja el comportamiento actual) o `Bearer`. Opcional: refresh token.
- `POST /api/auth/logout`: limpia cookie. `GET /api/auth/me`: devuelve perfil desde el token.

### 6.2 Autorización por rol (espeja `proxy.ts` + RLS)

- **`SecurityConfig`** (stateless) + **`JwtAuthFilter`** que valida el token y puebla el
  `SecurityContext` con el rol → `ROLE_DOCTOR / ROLE_LAWYER / ROLE_ADMIN`.
- **Protección por prefijo** (igual que el proxy):
  ```java
  .requestMatchers("/api/auth/**", "/api/ml/health").permitAll()
  .requestMatchers("/api/admin/**").hasRole("ADMIN")
  // + @PreAuthorize a nivel de método
  ```
- **RLS → ownership en la capa de aplicación** (lo más importante): cada query filtra por el usuario
  autenticado. Ejemplos:
  - Doctor lista casos → `caseRepository.findByDoctorId(currentUser.id, pageable)`.
  - Doctor abre caso ajeno → el servicio verifica `case.doctorId == currentUser.id` o lanza 403.
  - Abogado ve solo casos donde es `lawyer_id` o casos sin abogado relevantes a su perfil.
  - Admin: sin filtro de ownership (ve todo), pero con `@PreAuthorize("hasRole('ADMIN')")`.
- **Auditoría de cobertura**: haz una tabla "rol × tabla × operación" y verifica que cada combinación
  tenga su check, igual que verificarías que las RLS policies cubran los 3 roles en las 12 tablas.

> Riesgo a vigilar: en RLS olvidar una policy = no se ve nada (falla cerrado). En Spring, olvidar un
> filtro de ownership = **se ve de más** (falla abierto). Por eso el enforcement de ownership debe ser
> explícito y testeado en cada endpoint que devuelva datos del usuario.

### 6.3 Frontend (Angular)

- `authInterceptor`: `withCredentials: true` (cookie) o adjunta `Authorization`.
- `authGuard` / `roleGuard` (functional): replican el ruteo del proxy (sin sesión → login con
  `redirect`; prefijo de ruta ≠ rol → dashboard correcto).
- `AuthService` (signals): `currentUser`, `isAuthenticated`, `role`, `login()`, `logout()`,
  hidrata desde `/auth/me`. Reemplaza el `auth.store` de Zustand.

---

## 7. Detalle: enums, contrato de API y comportamientos a preservar **exactos**

### Enums (valores en español, sin cambios)

Define cada enum como Java enum con `AttributeConverter` para garantizar el string exacto:

```java
public enum CaseStatus {
    NUEVO("nuevo"), EN_REVISION("en_revision"), ACTIVO("activo"),
    CERRADO("cerrado"), ARCHIVADO("archivado");
    private final String value;
    /* getter + fromValue */
}
@Converter(autoApply = true)
class CaseStatusConverter implements AttributeConverter<CaseStatus, String> { /* ... */ }
```

Aplica el mismo patrón a: `user_role`, `case_priority` (baja/media/alta/critica),
`contact_request_status` (pendiente/aceptado/rechazado/cancelado),
`document_status` (borrador/pendiente_firma/firmado/archivado),
`document_type` (historia_clinica/...), `patient_gender` (M/F/other),
`signature_type` (digital/huella/firma_manuscrita).

### Comportamientos que NO deben cambiar (checklist de paridad)

- [ ] Sobre de respuesta `{ success, data }` / `{ success, error }` byte-por-byte.
- [ ] Paths de endpoints `/api/...` idénticos (para no romper nada en el front durante la transición).
- [ ] Query params de filtros/paginación (`status`, `priority`, `search`, page/range).
- [ ] Ruteo por rol: prefijos `/doctor`, `/lawyer`, `/admin` + redirecciones.
- [ ] Login demo por rol funciona igual.
- [ ] Cuentas con `is_active=false` rechazadas en login.
- [ ] ML: normalización a camelCase + disparo n8n **solo** si `riskLevel ∈ {alto, critico}`.
- [ ] n8n fire-and-forget: si cae, la app no se cae.
- [ ] Firma de documentos: el `hash` se genera realmente al firmar (era deuda pendiente — buen
      momento para cerrarla bien en Spring).
- [ ] Tokens de tema Tailwind + `globals.css` portados verbatim (color/tipografía/glassmorphism).
- [ ] Labels en español de toda la UI.

---

## 8. Plan por fases (orden de ejecución)

| Fase | Qué | Criterio de "hecho" |
|------|-----|---------------------|
| **0. Scaffold** | Crear `backend/` (Spring Initializr: Web, JPA, Security, Validation, Flyway, PostgreSQL, Actuator) y `frontend/` (Angular CLI + Tailwind v4 + spartan/ui + lucide-angular + angular-query). `docker-compose` con Postgres. | Ambos arrancan en vacío. |
| **1. BD** | Flyway `V1__schema.sql` (extensión vector, enums, 12 tablas, FKs, índices) + `V3__seed_demo.sql`. Importar datos reales del dump. | `./mvnw flyway:migrate` ok; datos visibles. |
| **2. Núcleo backend** | `ApiResponse`, `GlobalExceptionHandler`, `PageResponse`, entidades JPA + converters de enums, repositorios Spring Data, `SecurityConfig` esqueleto. | Compila; `/actuator/health` ok. |
| **3. Auth** | login/logout/me/register, JWT, BCrypt, demo roles, `JwtAuthFilter`, guards de método. | Login demo y email+password devuelven token; `/me` funciona. |
| **4. Dominio** | Controllers+services de **cases, documents, patients, users, audit, profile** con **ownership/rol (RLS replacement)**, paginación, filtros, joins (DTO projections). Avatar upload. | Cada endpoint responde con el contrato exacto y respeta ownership. |
| **5. Matching + ML + n8n** | `/matching/*`, `MlController` (proxy a FastAPI), `N8nNotifier`. | Risk assessment proxea y dispara n8n en alto/critico. |
| **6. Frontend base** | App Angular, **portar theme Tailwind + globals.css**, primitivos `shared/ui/` (spartan), `DashboardLayout` + `Sidebar` + `Topbar`, `AuthService`+guards+interceptor, generar cliente OpenAPI. | Login funciona contra el backend; shell idéntico al actual. |
| **7. Frontend features** | Portar cada página/portal (doctor/lawyer/admin) con angular-query + cliente generado, preservando comportamientos. | Paridad funcional y visual con el actual. |
| **8. Cierre** | Storage (avatares) prod, `AuditAspect`, firma (hash), `.env.example`, README, smoke tests, OpenAPI client regenerado. | App completa corriendo; checklist §7 verde. |

---

## 9. Dependencias clave

### Backend (`pom.xml`)
`spring-boot-starter-web`, `-data-jpa`, `-security`, `-validation`, `-actuator`,
`org.postgresql:postgresql`, `org.flywaydb:flyway-core` + `flyway-database-postgresql`,
`io.jsonwebtoken:jjwt-api/impl/jackson` (0.12.x), `org.springdoc:springdoc-openapi-starter-webmvc-ui`,
`com.cloudinary:cloudinary-http5` (avatares + docs; o el SDK del provider que elijas),
(test) `-starter-test`, `org.testcontainers:postgresql`.
ML/n8n: `RestClient` viene en spring-web (no necesitas WebFlux).

### Frontend (`package.json`)
`@angular/* ^21`, `tailwindcss ^4`, `@spartan-ng/*` (o `@angular/cdk`),
`@tanstack/angular-query-experimental`, `lucide-angular`, `clsx`, `tailwind-merge`,
`class-variance-authority`, `@fontsource-variable/geist`,
(dev) `@openapitools/openapi-generator-cli` para generar el cliente.

---

## 10. Riesgos y deuda (heredada + nueva)

| Riesgo | Mitigación |
|--------|-----------|
| **RLS → ownership falla abierto** | Test por endpoint: un doctor NO debe ver casos de otro. Tabla rol×recurso×operación. |
| **Passwords**: Supabase no exporta hashes | Seed demo + reset en primer login para usuarios reales. |
| **N+1 al reemplazar joins embebidos** | DTO projections / `@EntityGraph` / fetch joins desde el día 1. |
| **Enums**: romper valores en español | `AttributeConverter` con strings exactos + test de round-trip. |
| **Paridad visual** | Portar tokens verbatim + spartan/ui + revisión página por página contra capturas. |
| **TanStack → angular-query** API distinta | Centralizar en `core/api` + `query-keys.ts` portado; angular-query mantiene el modelo. |
| **Firma digital (hash)** ya era deuda | Implementarla bien ahora (hash real al firmar) en `documents`. |
| **Sin tests hoy** | Añadir al menos tests de auth/ownership y de contrato de API en la migración. |
| **Spring Boot 4 vs 3.5** | Quedarse en 3.5 para la tesis; subir a 4 después si quieres. |

---

## 11. Estrategia de transición (opcional, para no romper en caliente)

Como conservo los **mismos paths `/api/...`**, puedes migrar **endpoint por endpoint** poniendo el
front (viejo o nuevo) a apuntar al backend Spring vía variable de entorno de base URL. Pero dado que
también cambias el front a Angular, lo más limpio para tesis es **big-bang por entorno**: levantas el
stack nuevo completo en paralelo, validas con el checklist §7, y recién ahí jubilas el Next/Supabase.

---

## 12. Reconciliación con el Charter + Backlog académico (lo que SÍ sigue aplicando)

Decisiones de infraestructura overrideadas por ti: **Spring Boot se queda**, despliegue en
**Vercel + Railway**, **sin Azure** y **sin plazos**. Pero el Charter y el backlog imponen dos cosas
de **contenido** (no de infra) que tu app actual no cumple del todo y que te evalúan. No son opcionales
para la nota; conviene resolverlas en la misma migración.

### 12.1 Datos de paciente: NO identificables (Ley 29733)

El Charter excluye explícitamente "uso de historias clínicas reales o datos personales identificables"
y la HU-12 pide **contexto clínico-legal simulado** (edad referencial, área médica, sin info
identificable, etiquetado como simulado). Tu modelo actual tiene `patients` con **DNI, nombre,
apellido, fecha de nacimiento** → eso está fuera de alcance.

**Acción en la migración:** remodelar `patients` + `clinical_episodes` → una entidad de **contexto
simulado** (`case_context` o similar): `age_reference`, `medical_area`, `event_date`, `summary`,
`relevant_factors`, `is_simulated = true`. Sin DNI, sin nombre, sin datos identificables. Esto además
te simplifica RLS/ownership.

### 12.2 Alineación con el backlog (44 HU, 198 SP, 4 sprints)

| Concepto | App actual | Backlog académico | Decisión recomendada |
|----------|-----------|-------------------|----------------------|
| Terminología | "caso" (`cases`) | "consulta médico-legal" | Mantener el modelo, exponer la UI como **"consulta"** (es lo que evalúan). |
| Estados | `case_status`: nuevo/en_revision/activo/cerrado/archivado | **Pendiente → Clasificada → Asignada → En revisión → Respondida → Cerrada** (HU-16) | **Adoptar los 6 estados del backlog** (es criterio de aceptación explícito). |
| Pacientes | tabla con DNI/nombre | contexto simulado, no identificable | Remodelar (§12.1). |

**HUs que tu app aún no cubre del todo** (añadir en la migración):

- **HU-29 Clasificar consulta con ML** (tipo, urgencia, complejidad, especialidad sugerida).
- **HU-30 Priorizar consulta** (Baja/Media/Alta/Crítica con justificación).
- **HU-31/32 Matching + explicación XAI** (ranking de abogados + factores SHAP/LIME, lenguaje no
  determinista). Tu matching actual por specialty/medical_areas + el riesgo SHAP-like ya es buena base;
  falta exponer la **explicación** y los **mensajes de "es apoyo, no decisión"** (HU-43).
- **HU-36/37/38 Dashboards por rol** (médico, abogado, admin) con indicadores.
- **HU-39 Reporte básico de consulta** (vista/descarga consolidada).
- **HU-35 Métricas del modelo ML** (precisión/recall/F1, % matching pertinente) — para la validación OE4.

> Indicadores de éxito que condicionan el diseño (Charter): **tiempo de respuesta ≤ 2 s** (OE3-I2),
> **matching ≥ 70 %** y manejo de **cold-start** (riesgo 17: arrancar con reglas + modelo simple +
> fallback manual/round-robin por especialidad — exactamente tu enfoque actual). **SUS ≥ 70** de
> usabilidad → cuida que la paridad visual no se rompa.

### 12.3 Lo que NO cambia por el override

Auth (Spring Security + JWT), RLS→ownership, contrato `{success,data}`, paths `/api/...`, enums en
español, ruteo por rol, ML en FastAPI y n8n fire-and-forget: **idéntico al resto del blueprint**. Solo
cambió dónde se despliega y dónde vive la BD/Storage.

---

*Blueprint listo para ejecutar con el prompt de Claude Code adjunto (`CLAUDE_CODE_PROMPT_Migracion_Sinapsistencia.md`).*
