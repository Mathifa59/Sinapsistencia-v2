# Estado actual del proyecto — Sinapsistencia v2

> **Última actualización:** 2026-08-25 · **Commit de referencia:** `3c47b99` (feat(ml): pipeline unificado)
>
> Documento maestro: foto completa de arquitectura, funcionalidades, infraestructura y —
> con especial detalle, por ser el área con más cambios próximos — el **modelo de ML**.
> Los demás documentos en `docs/` profundizan cada tema; este es el punto de entrada y el
> baseline contra el cual medir cambios futuros (empezando por los del modelo).

---

## 1. Qué es Sinapsistencia

Plataforma web de **mediación médico-legal** que conecta médicos con abogados de derecho
médico: gestiona consultas clínico-legales, documentos con trazabilidad y evaluación de
riesgo asistida por ML explicable. Caso de estudio: Clínica SANNA "El Golf", San Isidro,
Lima. Proyecto de tesis (UPC) — autor Mathias Vasquez.

**Compliance no negociable (Ley 29733):** ningún dato real de paciente. Los casos usan
**contexto simulado** (código de referencia, edad referencial, área) en vez de historias
clínicas reales; hay un detector heurístico de PII que bloquea DNI/teléfonos/nombres
propios en los campos de texto libre. Detalle completo en
[seguridad-y-cumplimiento.md](seguridad-y-cumplimiento.md).

---

## 2. Stack tecnológico

| Capa | Tecnología |
|------|-----------|
| Frontend | Angular 21 (standalone, signals, control flow `@if/@for`) + Tailwind v4 + spartan/ui + TanStack Query |
| Backend | Spring Boot 3.5 + Java 21 + Spring Data JPA + Spring Security 6 + Flyway |
| Base de datos | PostgreSQL 16 + pgvector (Railway managed en prod) |
| Storage | Cloudinary (documentos adjuntos + avatares) |
| ML | FastAPI (`ml-service/`) — Random Forest (riesgo) + TF-IDF/coseno (matching); Spring lo consume vía proxy |
| Correos | n8n + Gmail — webhooks fire-and-forget (`MailNotifier`) |
| CI/CD | GitHub Actions (3 workflows con path filters) + auto-deploy Railway/Vercel desde `main` |
| Despliegue | Frontend → Vercel · Backend + ML → Railway |

## 3. Estructura del monorepo

```
sinapsistencia-v2/
├── backend/                    # Spring Boot 3.5 (Maven, Java 21) — DDD por módulo
│   └── src/main/java/pe/sinapsistencia/
│       ├── audit/               # @Auditable AOP → audit_logs
│       ├── auth/                # login, registro, JWT, LoginAttemptService (rate limit), consentimientos
│       ├── cases/                # consultas, contexto simulado, CaseClassificationService (ML)
│       ├── config/               # CORS, Security, Cloudinary condicional
│       ├── documents/            # versiones, firma SHA-256, upload Cloudinary
│       ├── matching/              # directorio de abogados, contact-requests, RecommendationService
│       ├── ml/                    # MlProxyService, MlClassification (persistencia del RF)
│       ├── notifications/         # MailNotifier + MailTemplates (n8n/Gmail)
│       ├── profile/               # perfiles médico/abogado/admin
│       ├── shared/                # ApiResponse, GlobalExceptionHandler
│       └── users/
│   └── src/main/resources/db/migration/   # V1 → V11 (Flyway)
├── frontend/                   # Angular 21
│   └── src/app/features/
│       ├── admin/    audit · dashboard · documents · metrics · profile · users
│       ├── auth/     login · register · forgot-password · reset-password
│       ├── doctor/   cases · dashboard · documents · lawyers · profile · risk
│       ├── lawyer/   cases · dashboard · doctors · profile · requests
│       └── shared/   manual
│   └── public/logo/             # 6 SVG (color/negro/blanco × horizontal/ícono) + favicon
├── ml-service/                 # FastAPI — Random Forest (riesgo) + TF-IDF/coseno (matching)
│   ├── app/risk/                 # modelo + baselines por especialidad
│   ├── app/matching/              # modelo TF-IDF + score compuesto
│   ├── training/                  # generate_risk_dataset.py + train_risk_model.py
│   ├── data/risk_dataset.csv      # 40 000 filas sintéticas balanceadas
│   └── models/                    # risk_model.joblib + metrics/report JSON
├── n8n/                        # workflow JSON de alertas (referencia)
├── docs/                       # toda la documentación (índice en §8)
├── _legacy/                    # Next.js + Supabase original (SOLO LECTURA, referencia)
└── docker-compose.yml           # Postgres 16 + pgvector para dev local
```

## 4. Cómo se conectan los servicios

```
Angular (Vercel)  ──HTTPS──▶  Spring Boot (Railway)  ──JDBC──▶  PostgreSQL (Railway)
                                     │
                                     ├──HTTP (proxy)──▶  FastAPI ML (Railway)
                                     │                    ├─ RandomForest → riesgo
                                     │                    └─ TF-IDF/coseno → matching
                                     │
                                     └──webhook fire-and-forget──▶  n8n ──▶  Gmail
                                                                       (correos + alertas de riesgo)
```

- El backend nunca bloquea si ML o n8n están caídos: **degradan con fallback declarado**
  (reglas `rules-v1` para clasificación, score determinístico para matching, correo
  omitido con `log.warn`).
- Autenticación: JWT (HS384) en cookie httpOnly `access_token`, 24h de expiración.

---

## 5. Estado funcional — 44 Historias de Usuario

**44/44 HUs implementadas** y desplegadas en `main`. Mapa completo por bloque:

| Bloque | HUs | Estado |
|---|---|---|
| Auth y usuarios | HU-01 a HU-10 | ✅ Registro, login, recuperación (n8n), roles, navegación, auditoría |
| Gestión de consultas | HU-11 a HU-23 | ✅ Contexto simulado, filtros, transiciones de estado, panel abogado, matching, respuesta legal, cierre |
| Documentos | HU-24/25/40/41 | ✅ Upload Cloudinary, control de acceso, validación de archivo |
| Eventos y timeline | HU-26/27 | ✅ Registro de eventos + filtro por tipo en la línea de tiempo |
| ML | HU-29 a HU-35 | ✅ Clasificación + priorización (RF real, pipeline unificado), matching TF-IDF, resultados visibles, métricas admin |
| Dashboards y reportes | HU-36 a HU-39 | ✅ 3 dashboards por rol + generación de informe HTML |
| Auditoría, ética, manual | HU-42 a HU-44 | ✅ AOP + nota ética HU-43 junto a cada salida ML + manual por rol |
| Historial | HU-28 | ✅ Listas con búsqueda |

**90/90 Casos de Prueba cubiertos** (CP001-CP090); el único condicionado es CP008
(recuperación de contraseña: token por correo si `N8N_WEBHOOK_URL` está configurado, o en
la respuesta como fallback si no).

### 5.1 Funcionalidades agregadas después del backlog original

No son HUs nuevas — son endurecimiento/UX sobre HUs existentes, del commit `84055ce` al
`3c47b99`:

- Médico puede **cancelar** su solicitud de contacto pendiente; admin puede **eliminar**
  solicitudes (limpieza de datos demo).
- **Rediseño de consultas** + análisis IA visible y animado (RF y matching) al crear un caso.
- **Matching compuesto** (contenido + desempeño del abogado, ver §6.3).
- Modales de motivo (cierre de caso, rechazo de solicitud) reemplazando `window.prompt`.
- Nota ética más discreta + deduplicación del score en la UI.
- Fix de scroll horizontal en móvil (dashboards y listado de documentos).
- **15 íconos lucide sin registrar** causaban que bloques enteros de plantilla se
  rompieran silenciosamente (botones sin estilo, textos vacíos) — corregido en
  `app.config.ts`; lección documentada para no repetirla.
- Logo real integrado (sidebar, login, registro, favicon) reemplazando el ícono de Angular.

---

## 6. Modelo de ML — estado detallado

> Fuente completa y sustento metodológico: [modelo-ml.md](modelo-ml.md).
> Esta sección resume lo esencial para no tener que saltar de documento cada vez que se
> planee un cambio.

### 6.1 Dos modelos, dos tareas

| Modelo | Técnica | Objetivo | HU |
|---|---|---|---|
| Clasificador de riesgo | RandomForest (clasificación + regresión) | Nivel de riesgo médico-legal al crear un caso | HU-29/30/31 |
| Matching médico-abogado | TF-IDF + similitud coseno + score de desempeño | Recomendar el abogado más compatible | HU-32/33 |

Ambos se muestran siempre junto a la **nota ética HU-43** (apoyo a la decisión, no
decisión definitiva) y quedan **persistidos** para trazabilidad (`ml_classifications`,
`match_recommendations`).

### 6.2 Clasificador de riesgo

**Pipeline unificado (desde `3c47b99`):** el RF real se invoca **en el backend al crear el
caso** (`CaseClassificationService`) — no solo en la pantalla de evaluación manual:

```
Crear caso → backend llama al RF (proxy a FastAPI)
           → persiste score, nivel, desglose de factores y versión del modelo
             en ml_classifications (V11)
           → la PRIORIDAD del caso es la sugerida por el modelo
           → riesgo alto/crítico dispara alerta n8n
           → la UI (animación de creación + detalle) lee el resultado persistido
           → si el servicio ML no responde: fallback a reglas (`rules-v1`), declarado, no silencioso
```

**7 variables de entrada:** `specialty` (20 categorías), `procedure_complexity`,
`priority`, `documentation_complete`, `informed_consent`, `has_prior_complaints`,
`time_since_incident_days`.

**Salida doble:** `risk_level` (bajo/moderado/alto/crítico) + `risk_score` continuo [0,1].

**Dataset:** 100 % sintético, **40 000 filas balanceadas** (25/25/25/25), generado con
efectos aditivos + **interacciones no lineales** (ej. sin consentimiento × alta
complejidad se agrava más que la suma) + ruido heterocedástico. Script:
[`generate_risk_dataset.py`](../ml-service/training/generate_risk_dataset.py).

**Métricas actuales (`rf-v2`, verificadas en `ml-service/models/risk_model_metrics.json`):**

| Métrica | Valor |
|---|---|
| Accuracy | 0.789 |
| F1 macro (test) | 0.788 |
| F1 macro (CV 5-fold) | 0.791 ± 0.003 |
| Regresor de severidad — R² | 0.928 |
| Baseline lineal (regresión logística) | F1 0.792 — el RF **no** le gana; se mantiene por explicabilidad e interacciones |

La clase **"moderado" es la más débil** (F1 0.68) — por diseño, concentra la zona de
mayor ruido/ambigüedad.

### 6.3 Matching médico-abogado

Score compuesto, **no solo textual**:

```
score = 0.70 · similitud_coseno(TF-IDF)  +  0.30 · desempeño
desempeño = 0.50·(rating/5) + 0.30·log(1+casos_resueltos)/log(1+60) + 0.20·min(años_exp/20, 1)
```

Disponibilidad es **filtro duro** previo (no pondera). Fallback determinístico
(coincidencia de área + rating + casos, sin componentes aleatorios) si el ML no responde.

### 6.4 Limitaciones declaradas (honestas, para la sustentación)

1. **Validez externa no demostrada** — el 0.79 mide fidelidad al generador sintético, no
   exactitud clínica real.
2. RF ≈ regresión logística en este dataset — la estructura es mayormente aditiva.
3. Clase "moderado" débil por diseño (zona de mayor ruido).
4. Sin features de texto todavía en el clasificador de riesgo (sí en el matching).

### 6.5 Investigación en curso — datasets reales

Línea de trabajo activa para calibrar (no reemplazar) el generador sintético con
evidencia epidemiológica real. Ver
[investigacion-datasets-reales-riesgo.md](investigacion-datasets-reales-riesgo.md) /
[.docx](investigacion-datasets-reales-riesgo.docx) para el detalle completo. Resumen:

- **No existe** un dataset real de mala praxis por caso, para Perú, con estas columnas.
- Fuentes reales usables para **calibrar proporciones relativas** (no importar la
  etiqueta tal cual — el resultado/pago refleja el sistema legal de cada país, no es
  transferible): NPDB Public Use File (EE. UU.), Kaggle Medical Malpractice Insurance
  Dataset, literatura académica (referencia de accuracy realista en datos reales: ~66 %).
- SUSALUD (Perú) solo publica estadísticas agregadas, no casos individuales.
- CMP/CAL: registros de médicos/abogados verificables uno por uno, sin descarga masiva —
  sirven para muestreo de referencia, no como dataset de entrenamiento.
- **Expectativa realista:** llegar a 85 % es más alcanzable calibrando el sintético que
  reemplazándolo por datos reales (que en la literatura rondan 60-70 %).

**Próximo paso concreto:** comparar campos de las fuentes reales contra
`SPECIALTY_BASELINE` en [`ml-service/app/risk/baselines.py`](../ml-service/app/risk/baselines.py)
y documentar la calibración en `modelo-ml.md` §3.

### 6.6 Hoja de ruta del modelo

1. **Ciclo de retroalimentación** (prioritario) — al cerrar un caso, el médico califica la
   asesoría; el sistema ya persiste cada recomendación y su desenlace
   (`match_recommendations`), que son etiquetas naturales para: recalibrar pesos del
   score compuesto, alimentar filtrado colaborativo (`collaborative_score` reservado), y
   construir el primer dataset **real** de riesgo con los casos cerrados de la plataforma.
2. Incorporar NLP sobre la descripción del caso al clasificador de riesgo.
3. Validación con datos reales anonimizados y recalibración de umbrales (§6.5).
4. Curva de calibración y análisis de sesgo por especialidad.

---

## 7. Infraestructura y despliegue

### 7.1 Producción

| Servicio | Dónde | URL/nota |
|---|---|---|
| Backend | Railway | `sinapsistencia-v2-production.up.railway.app` |
| Servicio ML | Railway | Servicio separado, mismo proyecto |
| Frontend | Vercel | Proxy `/api/**` → Railway vía `vercel.json` |
| Base de datos | Railway (Postgres managed) | |
| Correos | n8n Cloud (trial) + Gmail | Ver [n8n-correos-setup.md](n8n-correos-setup.md) |

⚠️ **Pendiente de decisión activa:** el trial gratuito de Railway y el de n8n Cloud están
por vencer. Plan acordado: pagar Railway Hobby (~$5/mes, sin migración) y self-hostear
n8n como servicio adicional dentro del mismo proyecto Railway (plantilla oficial +
nodo SMTP en vez de OAuth Gmail) para no depender de infraestructura nueva ni migrar a
mitad de la validación con usuarios reales (plazo: agosto). Ver [automatizacion-n8n-y-cicd.md](automatizacion-n8n-y-cicd.md).

### 7.2 CI/CD

3 workflows de GitHub Actions con path filters (solo corren si hay cambios relevantes):

- `ci-backend.yml` — JDK 21 + `mvnw verify` (Testcontainers)
- `ci-frontend.yml` — Node 22 + `npm run build`
- `ci-ml.yml` — Python 3.12 + `compileall`

Deploy automático por Railway/Vercel al hacer push a `main`.

### 7.3 Variables de entorno críticas

| Variable | Efecto si vacía | Efecto si configurada |
|---|---|---|
| `CLOUDINARY_URL` | Uploads responden 503 (graceful) | Archivos van a Cloudinary |
| `N8N_WEBHOOK_URL` | Correos omitidos, token en respuesta (fallback) | Correos vía n8n/Gmail |
| `APP_FRONTEND_URL` | Links de correo a localhost | Links al dominio de Vercel |
| `DEMO_DOCTOR_EMAIL` / `DEMO_LAWYER_EMAIL` / `DEMO_MAIL_BASE` | Correos ficticios del seed | Correos reales para la demo |
| `JWT_SECRET` | Secret débil de ejemplo | Secret seguro |
| `SPRING_DOCKER_COMPOSE_ENABLED` | — | `true` solo en dev local; **nunca** en Railway |

Listas completas: [`backend/.env.example`](../backend/.env.example) ·
[`frontend/.env.example`](../frontend/.env.example).

### 7.4 Migraciones Flyway (V1 → V11)

| Rango | Contenido |
|---|---|
| V1 | Schema base (17 tablas) |
| V3-V6 | Seeds de usuarios/casos demo + password reset |
| V7 | 32 documentos demo |
| V8 | 16 solicitudes de contacto demo |
| V9 | Métricas ML actualizadas a `rf-v2` |
| V10 | `user_consents` (Ley 29733) |
| V11 | `ml_classifications` — pipeline unificado |

### 7.5 Cuentas demo

| Rol | Email (default) | Contraseña |
|---|---|---|
| Médico | `doctor.demo@sinapsistencia.pe` (o `DEMO_DOCTOR_EMAIL`) | `Demo123!` |
| Abogado | `lawyer.demo@sinapsistencia.pe` (o `DEMO_LAWYER_EMAIL`) | `Demo123!` |
| Admin | `admin.demo@sinapsistencia.pe` (fijo) | `Demo123!` |

---

## 8. Índice de documentación (`docs/`)

| Documento | Contenido |
|---|---|
| **ESTADO_ACTUAL_PROYECTO.md** (este) | Punto de entrada — foto completa del proyecto |
| [modelo-ml.md](modelo-ml.md) | Sustento metodológico completo de los 2 modelos ML |
| [investigacion-datasets-reales-riesgo.md](investigacion-datasets-reales-riesgo.md) | Investigación de datasets reales para calibrar el modelo |
| [seguridad-y-cumplimiento.md](seguridad-y-cumplimiento.md) | Medidas de seguridad + mapeo Ley 29733 |
| [n8n-correos-setup.md](n8n-correos-setup.md) | Setup n8n + Gmail paso a paso |
| [automatizacion-n8n-y-cicd.md](automatizacion-n8n-y-cicd.md) | n8n + CI/CD, panorama general |
| [cuentas-demo.md](cuentas-demo.md) | Guía de cuentas demo y env vars relacionadas |
| [funcionalidades-y-flujos.md](funcionalidades-y-flujos.md) | Catálogo de funcionalidades y flujos |
| [FLUJOS_USUARIO.md](FLUJOS_USUARIO.md) | Flujos de usuario (histórico, fase de migración) |
| [API_ENDPOINTS.md](API_ENDPOINTS.md) | Referencia de endpoints |
| [MIGRATION_BLUEPRINT_Sinapsistencia.md](MIGRATION_BLUEPRINT_Sinapsistencia.md) | Plan original de migración Next.js→Angular/Spring (histórico) |
| [ESTADO_VERIFICACION.md](ESTADO_VERIFICACION.md) | Log de pruebas manuales (histórico, 2026-06-12) |

> Los marcados **histórico** describen decisiones/estado de la fase de migración, no el
> estado actual — se conservan por trazabilidad, no como referencia viva.

---

## 9. Pendientes / próximos pasos

1. **ML:** calibrar `SPECIALTY_BASELINE` con fuentes reales (§6.5), diseñar el ciclo de
   retroalimentación (§6.6.1).
2. **Infraestructura:** resolver Railway (pagar Hobby) y n8n (self-host en Railway + SMTP)
   antes de que venzan los trials — ver §7.1.
3. **Dominio propio:** evaluado, en espera — no bloquea la validación.
4. **Validación con usuarios reales** hasta agosto — validación de aceptación/UX, distinta
   de un reentrenamiento del modelo.
5. Smoke tests automatizados y regeneración del cliente OpenAPI si cambian DTOs.
