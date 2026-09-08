# Estado del componente de ML — Sinapsistencia v2

Documento de estado para compartir entre autores de la tesis. Consolida lo
ya documentado en varios archivos del repositorio — no introduce
información nueva, solo la reúne en un solo lugar. Snapshot al
**2026-09-04**. Para el detalle completo de cada punto, cada sección enlaza
a su fuente.

> Proyecto: mediación médico-legal (caso de estudio SANNA "El Golf", San
> Isidro, Lima). Tesis TP202610004, UPC. Ambos modelos operan como **apoyo
> a la decisión, no decisión** (HU-43): la nota ética acompaña siempre la
> salida, y la revisión humana es obligatoria.

---

## Parte 1 — Corpus de abogados (DS-03), Fase 1 cerrada

### 1.1 Qué es y cuántos perfiles hay

**45 perfiles** de abogados: **12 preservados** (11 con llave foránea real
desde `cases`/`contact_requests`, más la cuenta demo de abogado, que
resultó tener fila propia desde la migración V3) + **33 nuevos**, generados
para dar al corpus volumen suficiente — con 8-12 candidatos por consulta,
Precision@3 no discrimina.

- Script generador:
  [`ml-service/evaluation/build_corpus.py`](../ml-service/evaluation/build_corpus.py)
- Salida: [`ml-service/data/reference/ds03_lawyers.json`](../ml-service/data/reference/ds03_lawyers.json)
- Migración aplicada:
  [`backend/.../V12__seed_lawyers.sql`](../backend/src/main/resources/db/migration/V12__seed_lawyers.sql)
  (generada por el mismo script, no editada a mano)
- Esquema por perfil: `lawyer_id, full_name, bar_number, specialties[],
  medical_areas[], years_experience, rating, resolved_cases, biography`.
  **Sin** `current_caseload`/`max_caseload` — decisión documentada: ninguna
  variante de ablación los usa, y no existe fuente de verdad en producción
  para "capacidad".

### 1.2 Cómo se generaron los perfiles

Distribución **deliberadamente desbalanceada** por área médica, en tres
niveles de cobertura (las 20 áreas de `SPECIALTY_BASELINE`,
`ml-service/app/risk/baselines.py`):

| Nivel | Áreas (ejemplos) | Objetivo por área |
|---|---|---|
| Alta | Cirugía General, Ginecología y Obstetricia, Traumatología, Medicina General, Pediatría (5 áreas) | 5–7 abogados |
| Media | Cardiología, Neurología, Oncología, Anestesiología, Urología, Gastroenterología, Psiquiatría (7 áreas) | 3–4 abogados |
| Escasa (adverso, a propósito) | Dermatología, Endocrinología, Oftalmología, Neumología, Nefrología, Reumatología, Infectología, Hematología (8 áreas) | 1–2 abogados |

El desbalance escaso es intencional: sin especialidades poco cubiertas, el
estudio de ablación no puede probar cómo se comporta el sistema en el caso
adverso realista (pocos candidatos disponibles).

Cada biografía: 40–80 palabras (validado con `assert` en el script), texto
libre redactado a mano, no plantilla. `specialties[]` usa exclusivamente
las 8 etiquetas de `LEGAL_SPECIALTIES`
(`frontend/src/app/shared/constants.ts` — fuente única, no se toca el
frontend); `medical_areas[]` usa exclusivamente las 20 de
`SPECIALTY_BASELINE`.

### 1.3 Taxonomía de especialidades legales y su sustento normativo

Las 8 etiquetas que un abogado real ve y elige al registrarse, con su
respaldo en norma peruana (detalle completo con artículos citados:
[`docs/taxonomia-legal.md`](taxonomia-legal.md)):

| Etiqueta | Sustento normativo | Distribución real (45 perfiles) |
|---|---|---|
| Responsabilidad Civil Médica | Código Civil arts. 1969/1970/1985; Ley General de Salud (Ley 26842) arts. 15/29 | 16 (35.6%) |
| Derecho Médico | Sin correlato normativo directo — término paraguas del mercado legal peruano | 15 (33.3%) |
| Derecho Sanitario | Ley General de Salud (Ley 26842) + Ley 29344/D.L. 1158 (SUSALUD) + D.S. 031-2014-SA | 15 (33.3%) |
| Negligencia Médica | Concepto fáctico transversal a Resp. Civil y Penal — no tiene cuerpo normativo autónomo | 14 (31.1%) |
| Bioética y Derecho | Sin correlato normativo directo — interdisciplinario | 10 (22.2%) |
| Consentimiento Informado | Ley General de Salud arts. 4/15 — mapea a `informed_consent` del clasificador de riesgo | 10 (22.2%) |
| Derecho Penal Médico | Código Penal arts. 111 (homicidio culposo) / 124 (lesiones culposas) | 9 (20.0%) |
| Seguros Médicos | Ley 29946 (Contrato de Seguro); supletoriamente Ley 29571 (Protección al Consumidor) | 8 (17.8%) |

Suman más de 100% porque **las 8 etiquetas no son mutuamente
excluyentes** — cada abogado tiene 1–3. Esto es relevante para la rúbrica
de adjudicación de Fase 2: dos abogados con etiquetas distintas pueden
estar describiendo, en realidad, la misma área jurídica a dos niveles de
especificidad (p. ej. Negligencia Médica es transversal a Responsabilidad
Civil y Derecho Penal Médico, no una tercera vía independiente).

### 1.4 Solapamiento intencional y los 3 pares deliberadamente difíciles

Regla general del corpus: toda biografía debe ser semánticamente coherente
con `medical_areas[]` del perfil — una bio que describe contenido clínico
de un área distinta contamina el componente textual del matching
(encontrado y corregido durante la construcción en dos perfiles).

**Excepción documentada — los 3 pares "difíciles" (A, B, C):** 6 perfiles,
en parejas, con **biografía casi idéntica y área/especialidad legal
distinta**, exentos de la regla de coherencia siempre que la bio no
**afirme** un área distinta de la declarada (genérico ≠ contradictorio).
El diseño fuerza que solo los campos estructurados (`specialties[]`,
`medical_areas[]`) puedan discriminar entre los dos miembros de cada par —
el texto libre, deliberadamente, no puede.

| Par | Perfiles | Áreas médicas | Propósito |
|---|---|---|---|
| A | Rubén Gutiérrez / Karina Sotelo | Urología / Psiquiatría | Confusión textual puntual |
| B | Estefanía Rojas / Gonzalo Manrique | Urología / Gastroenterología | Confusión textual puntual |
| C | Pilar Zevallos / Julio Aliaga | Gastroenterología / Reumatología | Confusión textual **amplia** — su bio compartida resulta resonar con un tema narrativo transversal a muchas consultas, no solo con las de su par (ver §2.3 de `docs/datasheet-ds04.md`) |

Estos pares son la base de las observaciones preregistradas de Fase 2/3
sobre cuándo el componente de desempeño corrige una intrusión textual y
cuándo no (`docs/datasheet-ds04.md`).

### 1.5 Verificación contra Postgres real

V12 no se validó solo por inspección del script: se **ejecutó contra una
instancia Postgres real** (contenedor efímero, no el de desarrollo ni
Railway) antes de darla por cerrada. Esa verificación encontró un error de
la investigación original: se asumía que la cuenta demo de abogado (Lucía
Fernández) no tenía fila en `lawyer_profiles`, pero el `INSERT` chocó con
una restricción `UNIQUE` de `user_id` — sí tenía fila, desde la migración
V3. Se corrigió a `UPDATE` como los otros 11 perfiles anclados.

La misma verificación detectó y corrigió dos valores inválidos heredados de
migraciones previas: `'Responsabilidad Civil Profesional'` en
`specialties[]` (no es una de las 8 etiquetas válidas — corregido en dos
perfiles) y `'Medicina de Emergencia'` en `medical_areas[]` de Lucía
Fernández (no es una de las 20 áreas válidas — eliminada sin reemplazo, sin
pérdida de señal porque sus otras 2 áreas ya estaban verificadas contra sus
3 casos reales).

**Seguridad de las 33 cuentas nuevas.** No son cuentas de demo interactiva:
existen únicamente para dar volumen al corpus. Quedan `is_active = TRUE`
(deben ser candidatas plenas del matching en vivo, igual que en la
evaluación offline — de lo contrario la evaluación mide sobre 45 perfiles y
la aplicación desplegada sobre 12), pero **no autenticables**: cada una
tiene un `password_hash` bcrypt de una contraseña aleatoria de un solo uso
(`secrets.token_urlsafe(32)`), verificada en formato y funcionalidad antes
de escribirse y **descartada de inmediato** — nunca se imprime, nunca se
guarda, no existe en ningún artefacto del repositorio. Verificado tras
generar V12: 33 hashes con formato bcrypt válido, distintos entre sí,
ninguno coincide con el hash de la contraseña demo compartida.

Detalle completo de estas decisiones:
[`docs/datasheet-corpus-ds03.md`](datasheet-corpus-ds03.md).

### 1.6 Checks de calidad del corpus

El script imprime, en cada corrida, un reporte de verificación (no solo
genera el JSON a ciegas):

- **Distribución real vs. objetivo** por área médica y por etiqueta legal.
- **Chequeo de degeneración TF-IDF**: vocabulario único tras stopwords,
  longitud media/desviación de bio, término más frecuente del top-20 y en
  qué % de perfiles aparece (umbral de alerta: 60%).
- **% de bios que nombran su área médica explícitamente** vs. la dejan
  implícita — relevante porque `_lawyer_text()` de producción ya concatena
  `medical_areas[]` como texto plano; nombrarla siempre en la bio
  duplicaría la señal (ver Parte 2.3).
- **Verificación cruzada honesta**: detecta si alguna bio marcada "no
  nombra el área" en realidad la contiene como substring — sin corrección
  silenciosa, se reporta como aviso.

---

## Parte 2 — Estado completo del componente de ML

### 2.1 Dos modelos

| Modelo | Técnica | Objetivo | HU |
|---|---|---|---|
| Clasificador de riesgo | Random Forest (clasificación + regresión) | Estimar el nivel de riesgo médico-legal de un caso al crearlo | HU-29/30/31 |
| Matching médico–abogado | TF-IDF + similitud coseno + score de desempeño | Recomendar al abogado más compatible con el caso | HU-32/33 |

Documento de sustento metodológico completo:
[`docs/modelo-ml.md`](modelo-ml.md).

### 2.2 Clasificador de riesgo (`rf-v2`, en producción)

**Entrada — 7 variables** (contrato cerrado, no se agregan ni quitan sin
detenerse a preguntar, `CLAUDE.md` §4.1):

`specialty` (categórica, 20 valores), `procedure_complexity`
(baja/media/alta), `priority` (baja…crítica), `documentation_complete`
(booleana), `informed_consent` (booleana), `has_prior_complaints`
(booleana), `time_since_incident_days` (numérica).

**Salida doble:** `risk_level` ∈ {bajo, moderado, alto, crítico}
(`RandomForestClassifier`) + `risk_score` ∈ [0,1] continuo
(`RandomForestRegressor`).

**Pipeline en producción:** el backend invoca el modelo al crear el caso
(`CaseClassificationService`), persiste score/nivel/desglose de
factores/versión en `ml_classifications` (V11), y la prioridad del caso es
la sugerida por el modelo — la urgencia percibida del médico queda
documentada y puede imponerse editando el caso (HU-43). Si el servicio ML
no responde, degrada a reglas (`rules-v1`) usando la urgencia percibida —
fallback declarado, no silencioso.

**Dataset sintético — 40,000 filas**, balanceado 25/25/25/25 entre las 4
clases (`ml-service/training/generate_risk_dataset.py`, `--seed 42`). El
"riesgo verdadero" no es aleatorio: riesgo base por especialidad (20
valores calibrados) + efectos aditivos (complejidad, prioridad,
documentación, consentimiento, quejas, latencia log-saturada) +
**interacciones no lineales** (sin consentimiento × alta complejidad; doc.
incompleta × quejas previas; prioridad crítica × especialidad de alto
baseline; caso "blindado" que atenúa) + ruido gaussiano heterocedástico
(mayor incertidumbre en la zona media, score≈0.5).

**Entrenamiento:** preprocesamiento con `ColumnTransformer` (One-Hot para
especialidad, ordinal para complejidad/prioridad, passthrough para
booleanas/numérica). `RandomForestClassifier(n_estimators=150, max_depth=14,
min_samples_leaf=5, class_weight="balanced", random_state=42)` +
`RandomForestRegressor` análogo. Random Forest elegido por
interpretabilidad (requisito del dominio, no preferencia — decisión
cerrada, `CLAUDE.md` §6), captura de interacciones no lineales sin
ingeniería manual de features, manejo nativo de categóricas, e importancia
de variables como explicabilidad. Artefacto: 14 MB, apto para el runtime
de Railway.

**Métricas reales** (split 80/20 estratificado + CV 5-fold; fuente:
`ml-service/models/risk_model_report.json`):

| Métrica | Valor |
|---|---|
| Accuracy | 0.7887 |
| F1 macro (test) | 0.7877 |
| **F1 macro (CV 5-fold)** | **0.7911 ± 0.0033** |
| Precision macro | 0.7870 |
| Recall macro | 0.7887 |
| Regresor de severidad — R² | 0.9277 |
| Regresor de severidad — MAE | 0.0541 |

Por clase (la clase "moderado" es la más débil, por diseño — concentra la
zona de mayor ruido del generador):

| Clase | Precision | Recall | F1 | Soporte |
|---|---|---|---|---|
| bajo | 0.836 | 0.838 | 0.837 | 2000 |
| moderado | 0.684 | 0.666 | 0.675 | 2000 |
| alto | 0.744 | 0.734 | 0.739 | 2000 |
| crítico | 0.883 | 0.917 | **0.900** | 2000 |

Matriz de confusión (filas = real, columnas = predicho) — los errores caen
casi siempre en la clase adyacente, nunca confunde "bajo" con "crítico":

| real \ pred | bajo | moderado | alto | crítico |
|---|---|---|---|---|
| **bajo** | 1675 | 325 | 0 | 0 |
| **moderado** | 328 | 1332 | 340 | 0 |
| **alto** | 1 | 289 | 1468 | 242 |
| **crítico** | 0 | 0 | 165 | 1835 |

**Comparación contra baselines** (F1 macro, mismo split de test):

| Modelo | F1 macro |
|---|---|
| Trivial (`DummyClassifier` estratificado) | 0.2501 |
| Regresión logística (lineal) | 0.7918 |
| **Random Forest (elegido, rf-v2)** | 0.7877 |

RF **queda ligeramente por debajo** de la regresión logística lineal, no la
supera — se declara así explícitamente (`docs/modelo-ml.md` §7, punto 2):
la estructura del generador es mayormente aditiva, por lo que un modelo
lineal compite de cerca. Se mantiene RF por explicabilidad, manejo nativo
de categóricas y extensibilidad a features con interacciones más fuertes
(texto, temporales) — no por ganar en F1.

**Importancia de variables:** `procedure_complexity` (0.174), `priority`
(0.157), `informed_consent` (0.155), `documentation_complete` (0.137),
`has_prior_complaints` (0.105), `time_since_incident_days` (0.055),
especialidad (agregada, ≈0.13 repartido entre sus 20 dummies).

**Limitaciones declaradas** (`docs/modelo-ml.md` §7): validez externa no
demostrada (el 0.79 mide fidelidad al generador sintético, no exactitud
clínica — desconocida hasta validar con datos reales anonimizados); RF ≈
regresión logística; la clase "moderado" es la más débil por diseño; sin
features de texto todavía en el clasificador de riesgo (sí en el
matching).

### 2.3 Matching médico–abogado (`tfidf-cosine+perf-v2`, en producción)

**Score compuesto** — no es solo textual:

```
score = 0.70 · similitud_coseno(TF-IDF)  +  0.30 · desempeño

desempeño = 0.50 · (rating / 5)
          + 0.30 · log(1 + casos_resueltos) / log(1 + 60)   ← log-saturado
          + 0.20 · min(años_experiencia / 20, 1)
```

- **Vectorización:** documento del abogado = `specialties[]` +
  `medical_areas[]` + `bio` (`_lawyer_text()`); documento de la consulta =
  `specialty` + `sub_specialties` + `hospital` + `case_text`
  (`_doctor_text()`). `TfidfVectorizer` + similitud coseno.
- **Hallazgo documentado (no corregido en producción, solo documentado
  para la interpretación de Fase 3):** `specialty` entra **dos veces** al
  vector de la consulta — como campo estructurado y otra vez dentro de
  `case_text` (vía `buildCaseText()`) — y se combina con que
  `medical_areas[]` del abogado ya entra como campo estructurado del lado
  del abogado. El resultado: la variante `tfidf-full` (tal como opera hoy
  en producción) está más dominada por la coincidencia de área de lo
  estimado al diseñar la ablación original — sesgo compuesto por dos
  fuentes, no una. `bio-only` es la única variante de evaluación que aísla
  el aporte textual real, sin tocar el pipeline de producción. Detalle:
  [`docs/vectorizacion-tfidf-matching.md`](vectorizacion-tfidf-matching.md).
- **Disponibilidad** — filtro duro previo en el backend (no un componente
  ponderado del score, decisión cerrada — `CLAUDE.md` §6): abogados no
  disponibles o inactivos nunca entran al ranking.
- **Fallback determinístico** — si el servicio ML no responde, el backend
  calcula un score de respaldo por coincidencia de área + rating + casos
  resueltos (sin componentes aleatorios), marcado como `fallback`.

**8 variantes de ablación** (para Fase 3, no todas en producción —
`docs/MATCHING-SPEC.md` §5):

| Variante | Qué mide |
|---|---|
| `random` | El piso absoluto |
| `area-match` | Solo coincidencia de área médica |
| `tfidf-full` | Coseno sobre el documento completo — tal como opera hoy en producción |
| `bio-only` | Coseno sobre únicamente la biografía — aporte textual real, aislado del área |
| `performance-only` | Solo el score de desempeño |
| `composite-070` | La configuración vigente (0.70/0.30) |
| `composite-sweep` | Barrido de α en [0.0, 1.0] — demuestra que 0.70 no es arbitrario |
| `bm25` | BM25 en lugar de coseno TF-IDF |

### 2.4 Qué se probó y qué sigue pendiente de los juicios humanos

**Hecho y cerrado:**

- **Fase 1 (DS-03):** corpus de 45 perfiles, generado, verificado contra
  Postgres real, migración V12 aplicada. Ver Parte 1.
- **Fase 2 (DS-04), colección de prueba:** 20 consultas diseñadas (≥3 de
  especialidad escasa, balance de urgencia/complejidad), pool construido
  con 7 variantes pooleables a profundidad top-3 (`random` excluida,
  calculada analíticamente) — **187 pares únicos**, dentro del tope de 250.
  (`docs/datasheet-ds04.md`, `docs/MATCHING-SPEC.md` §4).
- **Piloto de adjudicación:** ejecutado, 21/21 pares respondidos. Reveló
  que el ritmo real de adjudicación (6–9 min/par medido) es 3×–6× más
  lento que el supuesto original (1–2 min/par). También reveló que la vía
  jurídica del caso se estaba filtrando a las notas del adjudicador (no
  estaba explícita como "dato dado" en el instrumento), y validó que
  marcar "confianza baja" es predictivo de inconsistencia intra-evaluador
  — ese hallazgo motivó subir la tasa de duplicados del instrumento
  definitivo por encima del 10 % original, para medir esa consistencia
  con más potencia. Los 3 ajustes de redacción correspondientes quedaron
  incorporados al generador del instrumento definitivo.
  (`docs/MATCHING-SPEC.md` §4.4.1).
- **Instrumento definitivo — generado (2026-09-04):** en vez de resolver la
  disponibilidad de un único adjudicador (≈21–32 h medidas por persona
  para el pool completo, inviable de confirmar de antemano), se optó por
  un **panel de varios adjudicadores** que responden el **mismo pool
  completo cada uno**, para que el protocolo (§3/§7.1) pueda calcular
  kappa de Cohen entre evaluadores. Se generaron **5 copias idénticas**
  (`docs/adjudicacion-definitivo-01.xlsx` a `-05.xlsx`, vía
  `build_instrument.py --n-queries 20 --copy-id`): **187 pares únicos, 28
  duplicados (15 %), 215 filas totales por copia**, mismo pool y mismo
  orden aleatorio en las 5 — verificado celda por celda, sin metadata que
  identifique a nadie. `--copy-id` queda parametrizado para generar copias
  sueltas adicionales más adelante, con el mismo `--seed`.
- **Composición del panel — nota condicional, preregistrada 2026-09-04:**
  los adjudicadores confirmados hasta ahora son **médicos**; no hay, a
  esta fecha, un abogado de derecho médico confirmado como adjudicador
  secundario (el protocolo §3 lo preveía idealmente, sin estar
  descartado ni confirmado). Si el panel se mantiene solo con médicos al
  momento de adjudicar, el kappa resultante mide **consistencia
  intra-gremio médico**, no concordancia entre disciplinas — limitación
  definitiva en ese caso. Si se suma un abogado, se incorpora como
  adjudicador secundario según el diseño original del protocolo, con su
  copia generada aparte vía `--copy-id`.
  (`docs/datasheet-ds04.md` §4, `docs/protocolo-adjudicacion_1.docx` §11).
- **Protocolo de adjudicación:** preregistrado, con **6 desviaciones**
  registradas y motivadas en su §11 (5 tras el piloto + la nota
  condicional de composición del panel)
  (`docs/protocolo-adjudicacion_1.docx`).
- **Fase 5 (calibración del generador de riesgo contra el NPDB):**
  cerrada. De 210,304 registros filtrados, 2 de 3 variables contrastadas
  (`informed_consent`, `has_prior_complaints`) mostraron efecto en
  dirección opuesta a la que asume el generador — con mecanismo
  identificado en ambos casos (sesgo de selección por pago; proxy
  `NPMALRPT` temporalmente inválido), no un desmentido del generador.
  **Decisión explícita y documentada: no se ajusta el generador.**
  `SPECIALTY_BASELINE` queda sin respaldo empírico externo (el NPDB no
  expone especialidad clínica), declarado como limitación permanente.
  (`docs/calibracion-generador.md`).
- **Corrección de una afirmación falsa desplegada:** las métricas del
  clasificador de riesgo afirmaban "supera baseline lineal", falso —
  corregido en el dato (`risk_model_metrics.json`, migración V13) y en la
  causa raíz (`train_risk_model.py` generaba la nota sin comparar de
  verdad).

**Pendiente de juicios humanos reales** (`ds04_qrels.csv` no existe
todavía — depende de que el panel devuelva las copias respondidas):

- **`run_ablation.py`** (Fase 3) — Precision@3, nDCG@3, MRR@3 y MAP@3 como
  métricas primarias (nDCG@5/Recall@5 solo como cotas inferiores,
  etiquetadas); intervalos de confianza al 95% por bootstrap (10,000
  remuestreos); barrido de α con validación cruzada dejando una consulta
  fuera. Las 28 pruebas de permutación pareada entre variantes están
  preregistradas como 7 confirmatorias (`composite-070` contra cada otra
  variante, α=0.05) y 21 exploratorias (Bonferroni, α≈0.00238), para no
  exponerse a falsos positivos por comparaciones múltiples sin corregir
  (`docs/datasheet-fase3-ablacion.md`). Verificado de punta a punta con
  qrels sintéticos (`--self-test`); no corrido contra datos reales.

**Bloqueante actual:** ya no es la generación del instrumento (resuelto,
5 copias entregables). Ahora depende de que el panel de adjudicadores
devuelva las copias respondidas — cada una implica el mismo ritmo medido
en el piloto (≈21–32 h por persona para las 215 filas, según el ritmo
6–9 min/par) — y de construir `ds04_qrels.csv` a partir de esas
respuestas. Todo el pipeline de Fase 3 (métricas, significancia
estadística, barrido de α) está implementado y verificado; correrlo contra
el resultado real es, en este momento, cuestión de tener esos juicios.

---

## Referencias rápidas

| Tema | Archivo |
|---|---|
| Especificación completa de matching (todas las fases) | `MATCHING-SPEC.md` |
| Sustento metodológico de ambos modelos | `docs/modelo-ml.md` |
| Datasheet del corpus DS-03 | `docs/datasheet-corpus-ds03.md` |
| Taxonomía legal y sustento normativo | `docs/taxonomia-legal.md` |
| Vectorización TF-IDF — qué se concatena | `docs/vectorizacion-tfidf-matching.md` |
| Datasheet de la colección de prueba DS-04 | `docs/datasheet-ds04.md` |
| Preregistro de las pruebas de significancia de Fase 3 | `docs/datasheet-fase3-ablacion.md` |
| Calibración del generador de riesgo contra el NPDB (Fase 5) | `docs/calibracion-generador.md` |
| Protocolo de adjudicación humana (6 desviaciones en §11) | `docs/protocolo-adjudicacion_1.docx` |
| Instrumento definitivo, 5 copias idénticas (panel de kappa) | `docs/adjudicacion-definitivo-01.xlsx` … `-05.xlsx` |
