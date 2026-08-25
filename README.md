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

## Estado actual del proyecto

> 📋 **Documento maestro (foto completa, actualizada):**
> [`docs/ESTADO_ACTUAL_PROYECTO.md`](docs/ESTADO_ACTUAL_PROYECTO.md) — arquitectura,
> las 44 HUs, el modelo de ML en detalle, infraestructura y pendientes. Este README
> se mantiene como punto de entrada rápido; para el estado real, ese documento manda.

La migración descrita originalmente en
`docs/CLAUDE_CODE_PROMPT_Migracion_Sinapsistencia.md` **se completó**: las 8 fases
(scaffold, base de datos, núcleo backend, auth, dominio, matching/ML/n8n, frontend base,
features por portal) están cerradas, con **44/44 Historias de Usuario implementadas** y
desplegadas en producción (Angular → Vercel, Spring Boot + FastAPI ML → Railway,
Postgres → Railway).

Desde el cierre de la migración se agregó, entre otras cosas: subida de archivos a
Cloudinary, correos transaccionales vía n8n + Gmail, CI/CD (GitHub Actions), un
**pipeline de ML unificado** (el Random Forest real clasifica y persiste el riesgo al
crear cada caso, ver `docs/modelo-ml.md`), matching compuesto (contenido + desempeño del
abogado), protecciones Ley 29733 (`user_consents`) y hardening de login (rate limiting).

Pendiente activo: resolver el vencimiento de los trials de Railway y n8n Cloud antes de
la validación con usuarios reales (hasta agosto) — detalle en
`docs/ESTADO_ACTUAL_PROYECTO.md` §7.1 y §9.
