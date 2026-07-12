# Modelos de Machine Learning — Sinapsistencia

Documento de sustento metodológico de los dos modelos de ML de la plataforma.
Redactado para defensa de tesis: incluye formulación, datos, metodología de
entrenamiento, evaluación, **resultados reales reproducibles** y **limitaciones
declaradas con honestidad**.

> ⚖️ **Ley 29733 (datos personales).** Ningún modelo se entrena con datos reales
> de pacientes. El dataset de riesgo es **100 % sintético**; el corpus de
> matching usa perfiles profesionales simulados. Esto es una decisión de diseño,
> no una carencia, y su implicancia sobre la validez externa se discute en
> [Limitaciones](#7-limitaciones-honestas-lo-que-un-jurado-preguntará).

---

## 1. Dos tareas, dos modelos

| Modelo | Técnica | Objetivo | HU |
|--------|---------|----------|-----|
| **Clasificador de riesgo** | Random Forest (clasificación + regresión) | Estimar el nivel de riesgo médico-legal de un caso al crearlo | HU-29/30/31 |
| **Matching médico-abogado** | TF-IDF + similitud coseno | Recomendar al abogado más compatible con el caso | HU-32/33 |

Ambos operan como **apoyo a la decisión, no decisión** (HU-43): el resultado se
muestra siempre acompañado de la nota ética y la revisión humana es obligatoria.

---

## 2. Clasificador de riesgo — formulación

**Entrada** (7 variables, capturadas al crear el caso):

| Variable | Tipo | Justificación médico-legal |
|----------|------|----------------------------|
| `specialty` | categórica (20) | La frecuencia de reclamos varía fuertemente por especialidad (obstetricia, anestesiología y cirugía concentran más litigios). |
| `procedure_complexity` | ordinal (baja/media/alta) | A mayor complejidad, mayor probabilidad de complicación y de reclamo. |
| `priority` | ordinal (baja…crítica) | Proxy de la gravedad clínica percibida. |
| `documentation_complete` | booleana | La documentación incompleta es un **agravante probatorio** central en la defensa. |
| `informed_consent` | booleana | La ausencia de consentimiento es uno de los factores de mayor peso legal. |
| `has_prior_complaints` | booleana | Antecedentes de quejas correlacionan con litigios futuros. |
| `time_since_incident_days` | numérica | La latencia afecta la calidad de la evidencia y la posición procesal. |

**Salida doble:**
- `risk_level` ∈ {bajo, moderado, alto, crítico} — **clasificación** (RandomForestClassifier).
- `risk_score` ∈ [0, 1] — **severidad continua** (RandomForestRegressor), usada para la barra y el ordenamiento fino.

---

## 3. Dataset sintético — metodología de generación

Script: [`ml-service/training/generate_risk_dataset.py`](../ml-service/training/generate_risk_dataset.py)

El "riesgo verdadero" (etiqueta) se obtiene de un **modelo generador** basado en
factores de la literatura de responsabilidad médica. No es una asignación
aleatoria: cada fila tiene una etiqueta coherente con sus variables.

1. **Riesgo base por especialidad** — 20 valores calibrados (obstetricia 0.50,
   dermatología 0.18, etc.).
2. **Efectos aditivos** — complejidad, prioridad, documentación, consentimiento,
   quejas y latencia (esta última **saturada logarítmicamente**, no lineal).
3. **Interacciones no lineales** (clave metodológica) — efectos compuestos que un
   modelo lineal no captura:
   - sin consentimiento **×** alta complejidad → agravante extra;
   - documentación incompleta **×** quejas previas → patrón de alto litigio;
   - prioridad crítica **×** especialidad de alto baseline → se potencia;
   - caso "blindado" (documentación + consentimiento + sin quejas) → atenúa.
4. **Ruido gaussiano heterocedástico** — mayor incertidumbre en la zona media
   (score ≈ 0.5), reproduciendo que los casos ambiguos son más difíciles de
   clasificar que los extremos.
5. **Balanceo de clases** (`--balance`) — rejection sampling para dejar las 4
   categorías al 25 % cada una, evitando que el modelo ignore las minoritarias.

**Dataset actual:** **40 000 filas**, distribución balanceada 25/25/25/25.

```
python generate_risk_dataset.py --rows 40000 --balance --seed 42
```

---

## 4. Entrenamiento y modelo

Script: [`ml-service/training/train_risk_model.py`](../ml-service/training/train_risk_model.py)

- **Preprocesamiento:** One-Hot para `specialty`, Ordinal para complejidad/prioridad, passthrough para booleanas/numérica (`ColumnTransformer`).
- **Modelo:** `RandomForestClassifier` (150 árboles, profundidad 14, `min_samples_leaf=5`, `class_weight="balanced"`) + `RandomForestRegressor` análogo.
- **¿Por qué Random Forest?**
  1. Captura las **interacciones no lineales** del dominio sin ingeniería manual de features.
  2. Maneja variables categóricas y numéricas mezcladas sin escalado.
  3. Provee **importancia de variables** → explicabilidad (el desglose por factor que ve el usuario).
  4. Robusto al sobreajuste con bagging; hiperparámetros podados para un artefacto liviano (**14 MB**, apto para el runtime de Railway).

```
python train_risk_model.py --model-version rf-v2
```

---

## 5. Evaluación — resultados reales (modelo `rf-v2`)

Protocolo: split estratificado 80/20 + **validación cruzada 5-fold**.

| Métrica | Valor |
|---------|-------|
| Accuracy | **0.789** |
| F1 macro (test) | **0.788** |
| **F1 macro (CV 5-fold)** | **0.791 ± 0.003** |
| Precision macro | 0.787 |
| Recall macro | 0.789 |
| Regresor de severidad — R² | **0.928** |
| Regresor de severidad — MAE | 0.054 |

**Por clase** (la clase media es la más difícil, como se diseñó):

| Clase | Precision | Recall | F1 |
|-------|-----------|--------|-----|
| bajo | 0.836 | 0.838 | 0.837 |
| moderado | 0.684 | 0.666 | 0.675 |
| alto | 0.744 | 0.734 | 0.739 |
| crítico | 0.883 | 0.917 | **0.900** |

**Matriz de confusión** (filas = real, columnas = predicho):

| real \ pred | bajo | moderado | alto | crítico |
|-------------|------|----------|------|---------|
| **bajo** | 1675 | 325 | 0 | 0 |
| **moderado** | 328 | 1332 | 340 | 0 |
| **alto** | 1 | 289 | 1468 | 242 |
| **crítico** | 0 | 0 | 165 | 1835 |

> Los errores caen **casi siempre en la clase adyacente** (nunca confunde
> "bajo" con "crítico"). Para un sistema de apoyo, un error de una categoría es
> tolerable; un salto de dos categorías sería grave, y prácticamente no ocurre.

**Comparación contra baselines** (F1 macro):

| Modelo | F1 macro |
|--------|----------|
| Trivial (DummyClassifier estratificado) | 0.250 |
| Regresión logística (lineal) | 0.792 |
| **Random Forest (elegido)** | 0.788 |

**Importancia de variables** (lo que "mira" el modelo): complejidad (0.17),
prioridad (0.16), consentimiento (0.16), documentación (0.14), quejas previas
(0.11), latencia (0.05), especialidad (agregada ≈0.13).

---

## 6. Matching médico-abogado — score compuesto (contenido + desempeño)

El matching **no es solo textual**: combina pertinencia temática con señales de
calidad verificables del abogado (`tfidf-cosine+perf-v2`).

```
score = 0.70 · similitud_coseno(TF-IDF)  +  0.30 · desempeño

desempeño = 0.50 · (rating/5)
          + 0.30 · log(1+casos_resueltos)/log(1+60)     ← saturado
          + 0.20 · min(años_experiencia/20, 1)
```

- **Contenido (70 %)** — se construye un documento textual por abogado
  (especialidades + áreas médicas + bio) y otro por caso (especialidad + área +
  tipo de evento); se vectorizan con **TF-IDF** y se compara con **similitud
  coseno** `cos(θ) = (A·B)/(‖A‖·‖B‖)`. Mide *de qué sabe* el abogado.
- **Desempeño (30 %)** — rating, casos resueltos (log-saturado: pasar de 5 a 15
  casos pesa más que de 45 a 55) y experiencia. Mide *qué tan bien lo hace* y
  evita que una bio larga/repetitiva gane solo por texto: a igual pertinencia,
  se recomienda al de mejor trayectoria.
- **Disponibilidad** — no pondera: es un **filtro duro** previo en el backend
  (abogados no disponibles o inactivos nunca entran al ranking).
- **Fallback determinístico** — si el servicio ML no responde, el backend calcula
  un score de respaldo por coincidencia de área + rating + casos resueltos
  (sin componentes aleatorios), y lo marca como `fallback`.
- La UI muestra el pipeline (vectorización → coseno → ranking compuesto), la
  barra de compatibilidad y **por qué** cada abogado es compatible.

---

## 7. Limitaciones honestas (lo que un jurado preguntará)

1. **Datos sintéticos ⇒ validez externa no demostrada.** El modelo aprende el
   *generador* que nosotros definimos; su desempeño en casos reales es
   **desconocido** hasta validar con datos reales anonimizados. El 0.79 mide
   fidelidad al modelo generador, no exactitud clínica.
2. **RF ≈ regresión logística** en este dataset (0.788 vs 0.792). Se declara
   explícitamente: la estructura es mayormente aditiva, por lo que un modelo
   lineal compite. Se mantiene RF por explicabilidad, manejo de categóricas y
   extensibilidad a features con interacciones más fuertes (texto, temporales).
3. **La clase "moderado" es la más débil** (F1 0.68), por diseño: concentra la
   zona de mayor ruido. Es coherente con que los casos limítrofes sean los más
   difíciles para cualquier evaluador.
4. **Sin features de texto todavía.** La descripción libre del caso no alimenta
   aún al clasificador de riesgo (sí al matching). Es la mejora de mayor impacto.

## 8. Hoja de ruta

### 8.1 Ciclo de retroalimentación (trabajo futuro prioritario)

Hoy el sistema **recomienda pero no aprende** de sus recomendaciones. El diseño
propuesto cierra el ciclo:

1. **Señal explícita** — al cerrar el caso, el médico califica la asesoría
   recibida (1–5) y si la recomendación fue pertinente (sí/no).
2. **Señal implícita** — el sistema ya persiste cada recomendación
   (`match_recommendations`) y cada resultado (solicitud aceptada/rechazada,
   caso cerrado): la tasa de aceptación por abogado y el desenlace del caso son
   etiquetas naturales.
3. **Uso de las señales** — (a) recalibrar los pesos del score compuesto,
   (b) alimentar un componente de **filtrado colaborativo** (el campo
   `collaborative_score` del contrato ya está reservado para esto), y
   (c) construir el primer dataset REAL de riesgo: cada caso cerrado con su
   desenlace es una fila etiquetada que reemplaza progresivamente al sintético.

### 8.2 Otras mejoras

- Incorporar **NLP sobre la descripción** del caso (embeddings) al clasificador.
- **Validación con datos reales anonimizados** y recalibración de umbrales.
- **Curva de calibración** y análisis de sesgo por especialidad.
- Registro de versiones de modelo y *drift monitoring* en producción.

---

## 9. Reproducibilidad

```bash
cd ml-service
python -m venv .venv && .venv/Scripts/pip install -r requirements.txt
cd training
python generate_risk_dataset.py --rows 40000 --balance --seed 42   # dataset
python train_risk_model.py --model-version rf-v2                    # modelo + reporte
```

Artefactos generados: `models/risk_model.joblib` (14 MB),
`models/risk_model_metrics.json` (tabla `model_metrics`, HU-35),
`models/risk_model_report.json` (evaluación detallada de este documento).
