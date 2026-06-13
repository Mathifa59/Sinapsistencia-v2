# Sinapsistencia — Servicio ML

Servicio FastAPI que implementa los dos modelos de IA de Sinapsistencia:

| Tarea | Algoritmo | HUs |
|-------|-----------|-----|
| Evaluación de riesgo médico-legal | **Random Forest** (clasificación `risk_level` + regresión `risk_score`) | HU-29, HU-30, HU-35 |
| Matching médico ↔ abogado | **TF-IDF + similitud coseno** | HU-31, HU-32 |

Expone exactamente los mismos paths que consume `MlProxyService` (Spring) en
`backend/`, así que no requiere cambios en el backend ni en el frontend.

## Estructura

```
ml-service/
├── app/
│   ├── main.py            # FastAPI app (endpoints)
│   ├── schemas.py          # Modelos Pydantic (contrato)
│   ├── risk/
│   │   └── model.py         # Carga risk_model.joblib y arma la respuesta
│   └── matching/
│       ├── model.py          # TF-IDF + coseno
│       └── lawyers_corpus.json  # Corpus de abogados (debe existir en la BD con los mismos IDs)
├── training/
│   ├── generate_risk_dataset.py  # Genera dataset sintético
│   └── train_risk_model.py        # Entrena y guarda el modelo
├── data/
│   └── risk_dataset.csv    # (generado)
├── models/
│   ├── risk_model.joblib         # (generado)
│   └── risk_model_metrics.json   # (generado)
└── requirements.txt
```

## Cómo correr

```bash
cd ml-service
python -m venv .venv
.venv/Scripts/activate        # Windows
# source .venv/bin/activate    # macOS/Linux

pip install -r requirements.txt

# 1) Generar dataset sintético + entrenar el modelo de riesgo (una sola vez,
#    o cada vez que cambien las reglas en training/generate_risk_dataset.py)
cd training
python generate_risk_dataset.py
python train_risk_model.py
cd ..

# 2) Levantar el servicio
uvicorn app.main:app --reload --port 8000
```

El backend Spring apunta a `app.ml.service-url=http://localhost:8000` por
defecto (ver `backend/.env.example`).

## Modelo de riesgo (Random Forest)

- **Dataset**: sintético (`training/generate_risk_dataset.py`). Cada fila es
  un caso simulado: combina especialidad médica, complejidad del
  procedimiento, prioridad, documentación/consentimiento/quejas previas y
  días desde el incidente. El `risk_score` "verdadero" se calcula con una
  fórmula de reglas de negocio + ruido gaussiano, y `risk_level` se deriva
  por umbrales (`bajo` < 0.30 ≤ `moderado` < 0.55 ≤ `alto` < 0.80 ≤ `critico`).
  No usa datos reales de pacientes (Ley 29733).
- **Entrenamiento**: `RandomForestClassifier` (200 árboles) para `risk_level`
  y `RandomForestRegressor` (200 árboles) para `risk_score`, sobre las mismas
  features preprocesadas (one-hot para especialidad, ordinal para
  complejidad/prioridad).
- **Explicabilidad (HU-31/32, "es apoyo no decisión" HU-43)**: para cada
  predicción se construyen `risk_factors` combinando la **importancia
  global** de cada feature (`feature_importances_` del RF) con su **valor
  normalizado en el caso concreto** — una explicación tipo SHAP simplificada,
  sin la dependencia pesada de `shap`.
- **Métricas**: se guardan en `models/risk_model_metrics.json` (precision,
  recall, f1 macro sobre un 20% de validación) — listas para insertar en
  `model_metrics` (HU-35).

Para reentrenar con otros datos (p. ej. si luego consigues datos
anonimizados reales), reemplaza `data/risk_dataset.csv` manteniendo las
columnas: `specialty, procedure_complexity, priority,
documentation_complete, informed_consent, has_prior_complaints,
time_since_incident_days, risk_score, risk_level` y vuelve a correr
`train_risk_model.py`.

## Modelo de matching (TF-IDF + coseno)

- **Corpus**: `app/matching/lawyers_corpus.json` — perfiles de abogados
  (especialidades legales, áreas médicas, bio). El texto de cada abogado y
  del médico consultante se vectoriza con `TfidfVectorizer` (con stopwords
  en español) y se compara con similitud coseno.
- **`lawyer_id`**: cada entrada del corpus tiene un UUID que debe coincidir
  con `profiles.id` / `lawyer_profiles.user_id` en Postgres, para que
  `MatchingService` (Spring) pueda resolver el perfil completo del abogado a
  partir del `lawyer_id` devuelto por el ML. El seed actual
  (`V3__seed_demo.sql`) solo trae 1 abogado demo
  (`d0000000-0000-0000-0000-000000000002`); el corpus incluye 8 abogados
  adicionales (`...0003`–`...0009`) que **no existen aún en la BD** — si
  quieres que el matching devuelva resultados completos para todos, hay que
  añadir esos perfiles en una migración Flyway (`V4__seed_lawyers.sql`).
- **`collaborative_model`**: queda como `"none"` por ahora (no se implementó
  ALS ni un colaborativo simple); `collaborative_score` siempre es `0.0`.

## Endpoints

```
GET  /health                    -> {status, service, version}
GET  /api/v1/model/info         -> {status, model_version, content_model, collaborative_model, risk_model}
POST /api/v1/risk-assessment    -> ver app/schemas.py (RiskAssessmentRequest/Response)
POST /api/v1/recommendations    -> ver app/schemas.py (RecommendationsRequest/Response)
```
