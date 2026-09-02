# MATCHING-SPEC — Validación empírica del emparejamiento médico–abogado

Especificación de la tarea. Complemento de `CLAUDE.md`, que debe leerse primero.

**Versión 2.** Enmienda la v1 en tres puntos, marcados con ⚠ en el texto: la octava variante
de ablación (`bio-only`), el tope de volumen del instrumento de adjudicación, y la
sincronización de proporciones con `docs/protocolo-adjudicacion` (que manda sobre este
documento en todo lo relativo a la adjudicación).

---

## 1. Objetivo

Convertir el emparejamiento médico–abogado de una **decisión de diseño** en un **resultado
empírico**, capaz de responder ante un jurado:

> ¿Por qué a este médico le recomiendas este abogado y no otro?

Hoy el score es `0.70 · coseno + 0.30 · desempeño`, con disponibilidad como filtro duro. Esos
pesos son una elección sin sustento medido. Al terminar esta tarea deben estar justificados con
evidencia, o corregidos.

**Meta comprometida en la tesis (indicador OE4-I2): pertinencia del matching ≥ 70 %**, medida
como Precision@3 sobre juicios de relevancia humanos.

---

## 2. Paradigma de evaluación: Cranfield

No hay usuarios suficientes para evaluar con tráfico real, y no hace falta. La recuperación de
información se evalúa desde hace décadas con **colecciones de prueba**: un corpus, un conjunto
de consultas y juicios de relevancia emitidos por personas. Las colecciones TREC usan ~50
consultas; para una tesis, 20 es defendible.

Referencias ya presentes en la memoria: Manning et al. (2008), Ricci et al. (2015),
Aggarwal (2016).

| ID | Artefacto | Estado |
|---|---|---|
| DS-03 | Corpus de perfiles de abogados | **Cerrado** — 45 perfiles |
| DS-04 | Colección de prueba con juicios de relevancia | Fase 2 |
| — | Estudio de ablación (8 variantes) | Fase 3 |

---

## 3. DS-03 — Corpus de perfiles de abogados ✅ CERRADO

Resultado: **45 perfiles** (12 preservados + 33 nuevos), migración `V12__seed_lawyers.sql`
verificada contra Postgres efímero.

Documentación asociada:

- `docs/taxonomia-legal.md` — sustento normativo de las 8 etiquetas de `LEGAL_SPECIALTIES`
- `docs/datasheet-corpus-ds03.md` — datasheet del corpus (formato Gebru et al., 2021)
- `docs/vectorizacion-tfidf-matching.md` — qué campos entran al vectorizador

Decisiones que rigen para el resto del trabajo:

- `specialties[]` usa exclusivamente las 8 etiquetas de `LEGAL_SPECIALTIES`. El frontend no se
  modifica.
- No existen `current_caseload` ni `max_caseload`. Ninguna variante los consume y la rúbrica de
  adjudicación indica ignorar la carga de trabajo.
- Los pares deliberadamente similares están **exentos** del requisito de coherencia bio/área,
  siempre que la bio no afirme un área distinta de la del campo estructurado. Genérico no es
  contradictorio.

---

## 4. DS-04 — Colección de prueba (Fase 2)

### 4.1 Consultas

⚠ **20 consultas** (la v1 decía 20–25; se fija en 20 por el tope de volumen de §4.4).

```
query_id, case_description, medical_specialty, event_type,
perceived_urgency, procedure_complexity
```

`case_description` es el texto que se vectoriza contra los perfiles. Debe tener la textura de
una consulta real: 60–120 palabras, sin datos identificables.

Cobertura balanceada entre especialidades y niveles de urgencia, con **al menos 3 consultas de
especialidades con pocos abogados disponibles** (caso adverso).

### 4.2 Juicios de relevancia mediante pooling

⚠ **Profundidad de pool: top-3 por variante** (v1: top-10; v2: top-5; ambas excedían el tope de
§4.4 al ejecutarse contra el corpus real de 45 abogados — ver la nota de verificación abajo).

Este parámetro es de **ejecución** (spec), no de **juicio humano** (protocolo): la profundidad
del pool, el número de consultas, las variantes y las métricas se deciden aquí; la rúbrica, las
reglas de casos límite, la escala de confianza y los requisitos de cegamiento los fija
`docs/protocolo-adjudicacion`, que es preregistrado y no se toca por esto.

**Nota de verificación (Fase 2).** Al ejecutar las 8 variantes contra el corpus real de 45
abogados, tanto top-10 (protocolo) como top-5 (v2) excedían los 250 pares incluso descontando
`random`. Solo una combinación cabe: **top-3 con `random` excluida del pooling**. El primer
cálculo de este número tenía un bug (las funciones de ranking truncaban a un `POOL_DEPTH` fijo
internamente, por lo que top-10 y top-5 daban el mismo resultado) — se detectó porque dos
profundidades distintas no podían dar el mismo número, se corrigió, y se volvió a medir antes de
decidir. Números reales: 187 pares únicos, 206 con el 10 % de duplicados (§4.4).

`random` no se poolea ni se le pide juicio humano. Se calcula **analíticamente**: si hay
`k` abogados relevantes entre los `n` que pasan el filtro de disponibilidad para una consulta,
la Precision@k esperada de un ranking aleatorio es `k/n` — exacta, insesgada, derivable de
`ds04_qrels.csv` una vez exista la adjudicación (Fase 3), sin consumir presupuesto de juicios.
Tratar sus candidatos no-pooleados como "no relevantes" por defecto habría deprimido
artificialmente el piso; este método lo evita. Implementación de referencia:
`expected_random_precision()` en `ml-service/evaluation/build_test_collection.py`.

Procedimiento:

1. Ejecutar las **7 variantes pooleables** (§5, todas menos `random`) sobre cada consulta
2. Tomar la unión de los **top-3** de cada variante
3. Presentar esa unión al adjudicador en **orden aleatorio con semilla fija y documentada**

### 4.3 Escala

```
0 = no apropiado
1 = parcialmente apropiado
2 = claramente apropiado
```

Definiciones operativas completas y reglas para casos límite: `docs/protocolo-adjudicacion`,
sección 4. **Ese documento manda.** No redefinas la rúbrica aquí.

### 4.4 ⚠ Tope de volumen del instrumento

Restricción dura: **250 pares como máximo, incluidos duplicados.**

⚠ Números **reales**, medidos ejecutando las 7 variantes pooleables sobre las 20 consultas y el
corpus de 45 abogados (reemplazan la estimación de ~198 de v2, que no se sostuvo contra datos
reales):

| Componente | Cantidad |
|---|---|
| Consultas | 20 |
| Candidatos únicos por consulta (unión top-3 de 7 variantes, sin `random`) | 7–12 (media 9.3) |
| **Pares únicos totales** | **187** |
| Duplicados (10 %) | 19 |
| **Total** | **206** |

Dentro del tope de 250, con margen de 44 pares. Las combinaciones descartadas antes de llegar a
esta (todas excedían 250 con datos reales): top-10/8 variantes → 570; top-5/8 variantes → 376;
top-5 sin `random` → 297; top-3/8 variantes (con `random` pooleada) → 257.

### 4.4.1 ⚠ Ritmo real de adjudicación (piloto) — el tope de 250 no basta por sí solo

⚠ Reemplaza la estimación implícita anterior. El instrumento del piloto (hoja «Instrucciones»,
`adjudicacion-piloto.xlsx`) decía **"uno o dos minutos por fila"**. El piloto midió lo contrario.

**Datos crudos** (hoja «Registro» del piloto, 21 pares completados):

| Tanda | Fecha | Inicio | Fin | Duración |
|---|---|---|---|---|
| 1 | 2026-08-30 | 21:00 | 21:50 | 50 min |
| 2 | 2026-08-31 | 22:00 | 22:20 | 20 min |
| 3 | 2026-09-01 | 09:00 | 10:00 | 60 min |
| 4 | 2026-09-01 | 09:00 | 10:00 | 60 min |

Las tandas 3 y 4 quedaron anotadas con la misma fecha y el mismo rango horario exacto — no se
puede distinguir desde el instrumento si son dos tandas reales o una sola registrada dos veces.
Sin poder resolver esa ambigüedad, se documentan ambos límites:

- **Límite inferior (130 min):** tandas 1+2+3, tratando la tanda 4 como duplicado de registro.
- **Límite superior (190 min):** las cuatro tandas tal como están anotadas.

**Ritmo medido:** 130 min / 21 pares ≈ **6 min/par** (inferior) a 190 min / 21 pares ≈ **9
min/par** (superior). El supuesto anterior (1–2 min/par) subestimó el ritmo real por un factor de
entre 3× y 6×, según el límite que se use.

**3 min/par — supuesto optimista, no medido.** La mitad del límite inferior medido, asumiendo que
el ritmo mejora con práctica sobre un instrumento más largo y homogéneo. Se documenta como
supuesto para acotar un mejor caso razonable, no como dato observado.

**Extrapolación a 206 pares** (volumen actual dentro del tope de 250):

| Ritmo | Origen | Horas totales |
|---|---|---|
| 3 min/par | Supuesto optimista, régimen sostenido | ≈ 10.3 h |
| 6 min/par | Medido, límite inferior | ≈ 21.2 h |
| 9 min/par | Medido, límite superior | ≈ 30.9 h |

Bajo cualquiera de los tres escenarios, el instrumento definitivo dimensionado a 206 pares excede
holgadamente lo que una sesión o un puñado de sesiones cortas puede absorber.

**Consecuencia para el tope de 250.** El tope numérico de pares (§4.4) sigue vigente como
restricción dura, pero **no es suficiente por sí solo**: el volumen final del instrumento
definitivo queda condicionado además a la disponibilidad real que confirme el adjudicador,
medida en horas que esa persona puede comprometer, no solo en pares por debajo de 250. Hasta que
esa disponibilidad esté confirmada, no se genera el instrumento definitivo (`ds04_pool.xlsx`).

### 4.5 Instrumento

⚠ **Duplicados: parametrizado, por encima del 10 %** (`build_instrument.py --duplicate-rate`,
default 0.15). El piloto validó que la marca de confianza baja predice inconsistencia
intra-evaluador (§4.4.1), pero con un único par duplicado en régimen de inconsistencia sobre 21
filas la medición tiene muy poca potencia — subir la tasa por encima del 10 % (la v2 la fijaba
ahí) da más pares para medir consistencia intra-evaluador. Separados al menos 30 posiciones de su
primera aparición cuando el instrumento lo permite; con `--n-queries` reducido, `build_instrument.py`
usa una regla de separación mínima escalada y documentada (`min_duplicate_gap`), no silenciosa.

- **Número de consultas: parametrizado** (`build_instrument.py --n-queries`), decidido junto con
  la disponibilidad confirmada del adjudicador (§4.4.1), no antes.
- **Justificación escrita: 15 %** de los pares, distribuidos a lo largo del instrumento
  (`--justification-rate`).

Formato XLSX con tres hojas —Instrucciones, Adjudicación, Registro— siguiendo la estructura del
piloto ya entregado, con dos ajustes de redacción en Instrucciones tras el piloto (ninguno toca
la rúbrica): se retira la promesa de "uno o dos minutos por fila" (el piloto la desmintió, ver
§4.4.1) y se agrega la regla explícita sobre la vía jurídica como dato dado (protocolo §4.2, ya
existía pero no era visible en el instrumento — el piloto mostró el síntoma: 52 % de las
respuestas en la categoría intermedia, con justificaciones que corregían el encuadre jurídico en
vez de evaluar al abogado). Validación de datos en las columnas de relevancia (0/1/2) y confianza
(Alta/Media/Baja).

**Requisitos de cegamiento, obligatorios.** El instrumento no muestra la puntuación del
sistema, ni la posición en el ranking, ni la variante que propuso cada candidato, ni indicación
alguna de qué respuesta se considera correcta.

La hoja Registro incluye el registro de tandas y la declaración firmada, conforme a la
modalidad asíncrona del protocolo, sección 6.

### 4.6 Salidas

- `ml-service/data/reference/ds04_queries.json`
- `ml-service/data/reference/ds04_pool.xlsx` (instrumento en blanco)
- `ml-service/data/reference/ds04_qrels.csv` (juicios, tras la adjudicación)

Hasta que exista adjudicación humana, los juicios se marcan `provisional: true`. **No reportes
métricas basadas en juicios provisionales como si fueran validadas.**

Decisiones operativas del pool, composición por etiqueta legal/área médica, y las observaciones
**preregistradas** sobre los pares difíciles (qué tan seguido intrusa cada par en pools de área
equivocada, y las predicciones comprobables para Fase 3 que se derivan de eso): ver
`docs/datasheet-ds04.md`. Preregistradas significa escritas antes de generar `ds04_qrels.csv` —
no se editan una vez que existan los qrels.

### 4.7 Fase piloto

Antes del instrumento definitivo se aplica un piloto de 20–25 pares (`adjudicacion-piloto.xlsx`,
ya entregado). Verifica comprensión de las consultas, aplicabilidad de la rúbrica y usabilidad
del formato. **Sus juicios se descartan** y no entran al análisis. Los comentarios pueden
motivar ajustes de redacción, nunca cambios en la rúbrica.

---

## 5. Estudio de ablación (Fase 3)

El corazón de la defensa.

⚠ **Ocho variantes** (la v1 tenía siete):

| Variante | Descripción | Qué demuestra | ⚠ Pooling (§4.2) |
|---|---|---|---|
| `random` | Orden aleatorio, semilla fija | El piso absoluto | **No se poolea.** Precision@k esperada calculada analíticamente como `k/n` a partir de `ds04_qrels.csv` (Fase 3) — ver §4.2 |
| `area-match` | Solo coincidencia de área médica | Que el sistema supera una regla simple | Pooleada |
| `tfidf-full` | Coseno sobre el documento completo (`specialties` + `medical_areas` + `bio`) | El componente textual tal como opera hoy en producción | Pooleada |
| `bio-only` | Coseno sobre **únicamente** la biografía | El aporte textual **real**, aislado de la señal de área | Pooleada |
| `performance-only` | Solo score de desempeño | El aporte del historial | Pooleada |
| `composite-070` | 0.70 coseno + 0.30 desempeño (actual) | La configuración vigente | Pooleada |
| `composite-sweep` | Barrido de α en [0.0, 1.0] paso 0.1 | Que 0.70 no es arbitrario | Pooleada solo con α=0.5 (evita duplicar `composite-070`); el barrido completo corre en Fase 3 |
| `bm25` | BM25 en lugar de coseno TF-IDF | Si otra función de ranking mejora | Pooleada |

En todas, la disponibilidad se aplica como **filtro duro previo**. Para las métricas de Fase 3
(no para el pooling), las 8 variantes sí se evalúan todas contra los qrels — la exclusión de
`random` del §4.2 es solo para no gastar presupuesto de adjudicación en algo cuyo resultado
esperado ya se conoce analíticamente.

### 5.1 ⚠ Por qué existe `bio-only`

`_lawyer_text()` concatena `specialties[]` + `medical_areas[]` + `bio`, y `_doctor_text()`
incluye la especialidad de la consulta como texto plano (verificado en Fase 1, documentado en
`docs/vectorizacion-tfidf-matching.md`).

Consecuencia: **`tfidf-full` ya contiene la señal de coincidencia de área.** Comparada contra
`area-match`, ambas miden parcialmente lo mismo, y la diferencia entre ellas no aísla el aporte
del texto libre.

`bio-only` resuelve eso vectorizando solo la biografía. Es la única variante que puede sustentar
la afirmación de que el componente textual aporta algo por encima de la coincidencia de
especialidad — que es justamente lo que un evaluador va a cuestionar.

**No modifiques `_lawyer_text()` en producción.** `bio-only` se construye como vectorizador
separado dentro del código de evaluación.

### 5.2 ⚠ Métricas — ajustadas a profundidad de pool 3

Consecuencia directa de §4.2: con pooling a profundidad 3, las posiciones 4 y 5 quedan
mayormente sin juzgar (solo aparecen ahí si algún otro par las metió por coincidencia) y se
cuentan por defecto como no relevantes. Reportar métricas de profundidad 5 sobre un pool de
profundidad 3 sería engañoso — subestimarían sistemáticamente al sistema.

**Métricas primarias (confiables, profundidad 3):**

- **Precision@3** — métrica principal, la del indicador OE4-I2
- **nDCG@3** — aprovecha la escala graduada 0/1/2
- **MRR@3** — rango recíproco, truncado a la profundidad pooleada
- MAP (calculada sobre los mismos 3 primeros)

**Métricas de profundidad 5 (`nDCG@5`, `Recall@5`): NO se reportan como resultado principal.**
Si se calculan, se marcan explícitamente como **cotas inferiores** (lower bounds) en cualquier
tabla o gráfico, con una nota al pie que explique por qué — nunca junto a las métricas de
profundidad 3 sin esa aclaración.

### 5.3 Rigor estadístico — obligatorio

Con 20 consultas, Precision@3 se mueve en saltos de 0.33 por consulta. Reportar un número pelado
sería engañoso.

- **Intervalos de confianza al 95 % por bootstrap** (10 000 remuestreos sobre consultas)
- **Prueba de permutación pareada** entre variantes — ⚠ 28 comparaciones (C(8,2)) a α=0.05 sin
  ajustar producirían ~76 % de probabilidad de al menos un falso positivo por azar. Preregistrado
  2026-09-02, antes de qrels reales: **confirmatorias** (7, `composite-070` contra cada otra
  variante, α=0.05 sin ajustar — sostienen el argumento central) vs. **exploratorias** (21, el
  resto, corrección de Bonferroni α≈0.00238 — describen comportamiento, no sustentan diseño).
  Detalle y justificación de Bonferroni sobre Benjamini-Hochberg: `docs/datasheet-fase3-ablacion.md`.
- Si dos variantes no difieren significativamente, **decirlo**, no elegir la de número mayor

Los juicios de valor 1 se reportan **en ambos sentidos** (como relevantes y como no relevantes),
sin elegir a posteriori el que favorezca el resultado.

### 5.4 Barrido de pesos sin sobreajuste — crítico

Elegir α sobre las mismas consultas donde se reporta el resultado es sobreajuste y anula todo el
estudio.

Usar **validación cruzada dejando una consulta fuera**: para cada consulta, seleccionar α con
las otras 19 y evaluar sobre esa. La métrica reportada es el promedio fuera de muestra.

Si α óptimo varía mucho entre pliegues, significa que **los datos no soportan una elección fina
de peso**. Es un hallazgo válido: repórtalo y mantén 0.70 como decisión de diseño razonable, en
lugar de fingir una precisión que no existe.

---

## 6. Explicabilidad del emparejamiento

Reforzar las razones en lenguaje natural con:

- Los **términos concretos** que más contribuyeron a la similitud (top-5 del producto TF-IDF)
- El **desglose numérico** del score compuesto: cuánto vino del texto, cuánto del desempeño
- Las **especialidades coincidentes** explícitas

Esto es lo que permite responder en vivo, sobre un caso concreto, la pregunta del jurado.

---

## 7. Entrenamiento continuo (Fase 4)

Primera fase que toca código de la aplicación. Hasta aquí todo ha sido datos y evaluación.

### 7.1 Reajuste del vectorizador

- **Dirigido por evento**, no por calendario: al cambiar el corpus se marca `corpus_dirty` y se
  reajusta de forma asíncrona
- Versionado `match-v{n}` con fecha, tamaño del corpus y hash del vocabulario
- Versión activa expuesta en `/api/v1/model/info`
- El reajuste **nunca bloquea** una petición: si está en curso, se sirve la versión anterior

### 7.2 Bucle de retroalimentación

`match_recommendations` ya persiste cada recomendación y su desenlace. Capturar:

- Recomendación aceptada frente a rechazada
- Posición en el ranking del abogado finalmente elegido
- Calificación del médico al cerrar el caso

Con eso, recalibrar periódicamente α y los coeficientes del score de desempeño.

### 7.3 Advertencia de honestidad

**No habrá volumen suficiente durante la tesis** para que la retroalimentación produzca mejora
medible. Hacen falta cientos de casos cerrados.

Por tanto: **implementar** el mecanismo y demostrarlo con retroalimentación simulada,
**documentarlo** como diseño de evolución, y **no reportar** mejoras derivadas de
retroalimentación real como si fueran resultado obtenido.

---

## 8. ⚠ Calibración del generador de riesgo (Fase 5, alcance replanteado)

Tarea menor, independiente del adjudicador — puede avanzar en paralelo a la Fase 2. El
clasificador de riesgo no es el núcleo de la tesis: el médico declara su urgencia percibida y
puede sobrescribir la sugerencia.

Sobre `NPDB2601.CSV`, filtros `RECTYPE == 'P'`, `LICNFELD in (10, 20)`, `OUTCOME != 10` →
**210 304 registros** (verificado contra el codebook oficial del NPDB; si tu ETL da otro número,
hay un bug).

⚠ **El alcance original de esta sección (calibrar por especialidad) no es ejecutable — descartado
tras verificación, no reemplazado por una aproximación.**

- **Por especialidad clínica.** El NPDB no expone especialidad clínica en ningún campo;
  `LICNFELD` solo distingue MD/DO. `SPECIALTY_BASELINE`
  (`ml-service/app/risk/baselines.py`) **permanece sin respaldo empírico externo.**
- **Por tipo de incidente (`ALGNNATR`).** `generate_risk_dataset.py` no modela ninguna variable
  equivalente a naturaleza de alegato (cirugía, diagnóstico, obstetricia, etc.). `ALGNNATR` no
  tiene contraparte en el generador.

Ambas quedan declaradas como limitación permanente, no como pendiente a resolver.

**Alcance ejecutado en su lugar:** contraste de tres variables del generador con proxy directo en
el NPDB, sin pasar por especialidad ni tipo de incidente — `informed_consent` (código de alegato
`707`, verificado contra el codebook oficial), `has_prior_complaints` (`NPMALRPT > 1`),
`time_since_incident_days` (`ORIGYEAR − MALYEAR1`, con desajuste de dominio declarado: el
generador nunca representa más de un año, el NPDB sí). Resultado, metodología completa y
limitaciones adicionales: `docs/calibracion-generador.md` (generado por
`ml-service/evaluation/calibration/calibrate_generator.py`, no ejecutar contra el CSV sin
`--input` apuntando a una copia local — el archivo nunca se versiona en el repo).

**Hallazgo y decisión, cerrados.** Dos de las tres variables (`informed_consent`,
`has_prior_complaints`) midieron efecto en dirección opuesta a la que el generador asume hoy
(intervalo de confianza al 95 % que no cruza cero en ambos casos). **No son un desmentido del
generador — cada uno tiene mecanismo identificado:** `informed_consent` es un artefacto del sesgo
de selección del NPDB (solo contiene reclamos pagados, y el deber de informar genera
responsabilidad legal aun con daño clínico leve); `has_prior_complaints` compara con un proxy
(`NPMALRPT`) que cuenta la carrera completa del profesional, no el historial previo al caso — no
es la misma variable con signo invertido, es una comparación temporalmente inválida. **Decisión
explícita: no se ajusta el generador en ninguna de las tres variables.** Invertir el signo de
`informed_consent` comunicaría al médico que un caso sin consentimiento firmado es de menor
riesgo — indefendible en este dominio. Detalle completo y razonamiento por variable:
`docs/calibracion-generador.md` §2, §3, §6.

**Transferir efectos condicionales, no frecuencias marginales:** el NPDB solo contiene reclamos
que terminaron en pago, y ese sesgo de selección afecta las marginales — declarado en el
datasheet de salida.

**No importar montos de pago.** Reflejan el sistema legal estadounidense y no son transferibles.
Ningún paso de `calibrate_generator.py` lee `PAYMENT` ni `TOTALPMT`.

**No reentrenar el modelo.** Ajustar hiperparámetros sobre un corpus cuya etiqueta deriva del
propio generador produce un número más alto pero no más creíble.

---

## 9. Entregables

```
ml-service/
├── data/reference/
│   ├── ds03_lawyers.json            ✅
│   ├── ds04_queries.json            ✅
│   ├── ds04_pool.xlsx               pendiente — disponibilidad del adjudicador (§4.4.1)
│   └── ds04_qrels.csv               pendiente — tras adjudicación
├── evaluation/
│   ├── build_corpus.py              ✅
│   ├── build_test_collection.py     ✅
│   ├── build_instrument.py          ✅ escrito, no ejecutado (§4.4.1)
│   ├── calibration/
│   │   └── calibrate_generator.py   ✅
│   ├── run_ablation.py              ✅ escrito, verificado con --self-test (qrels sintéticos); no corrido contra ds04_qrels.csv real (no existe todavía)
│   └── report.py
├── artifacts/matching/
│   ├── metrics.json
│   └── figures/
└── app/matching/                    # Fase 4: reajuste continuo + versionado
backend/src/main/resources/db/migration/V12__seed_lawyers.sql   ✅
docs/
├── taxonomia-legal.md               ✅
├── vectorizacion-tfidf-matching.md  ✅
├── datasheet-corpus-ds03.md         ✅
├── datasheet-ds04.md                ✅
├── datasheet-fase3-ablacion.md      ✅ (§5.3, confirmatorias/exploratorias preregistrado)
├── protocolo-adjudicacion_1.docx    ✅ (5 desviaciones en §11, nota §12)
├── adjudicacion-piloto.xlsx         ✅ piloto respondido, 21/21 pares
├── calibracion-generador.md         ✅ (§8, ya no calibracion-baselines.md)
└── model_card_matching.md
```

---

## 10. Criterios de aceptación

- [x] Corpus de 45 abogados con biografías variadas, no plantilla
- [x] 20 consultas con al menos 3 de especialidades escasas — 5 de 20, verificado
- [x] Pool con profundidad top-3, total ≤ 250 pares incluidos duplicados — 187 únicos, 206 con duplicados
- [x] Piloto ejecutado (21/21 pares) y ritmo real medido — 6–9 min/par, ver §4.4.1
- [ ] Disponibilidad real del adjudicador confirmada y redimensionamiento decidido (§4.4.1) —
      bloquea la generación del instrumento definitivo
- [ ] Instrumento sin filtrar la salida del modelo, con duplicados por encima del 10 % y 15 % justificaciones
- [ ] Las 8 variantes de ablación ejecutadas y reportadas contra `ds04_qrels.csv` real —
      `run_ablation.py` escrito y verificado con `--self-test`, pendiente de juicios humanos
- [x] `bio-only` construida sin modificar `_lawyer_text()` en producción
- [ ] Métricas con intervalos de confianza por bootstrap — implementado y verificado en
      `--self-test`, pendiente de correrse contra resultado real
- [ ] Barrido de α con validación dejando una consulta fuera — implementado (`composite_sweep_loo`),
      pendiente de correrse contra resultado real
- [ ] Juicios de valor 1 reportados en ambos sentidos
- [ ] Contrato de la API intacto; fallbacks operativos
- [ ] Reejecutar produce resultados idénticos

---

## 11. Cuándo detenerte y preguntar

- Si Precision@3 supera 0.95 — con juicios humanos reales eso sugiere fuga o consultas demasiado
  fáciles, no un sistema excelente
- Si el pool supera 250 pares
- Si necesitas cambiar el contrato de la API o las 7 variables del clasificador
- Si el ETL del NPDB no da 210 304 registros
- Si consideras ajustar pesos mirando el resultado de la evaluación final
- Si el ritmo real de adjudicación (§4.4.1) hace inviable el volumen dentro de la disponibilidad
  que confirme el adjudicador — no generes el instrumento definitivo, redimensiona primero
- Si un criterio de aceptación no se puede cumplir

**Nunca** ajustes el protocolo de evaluación para alcanzar una meta numérica. Si el resultado
queda por debajo de 70 %, repórtalo y analiza por qué: eso es un hallazgo de tesis, no un
fracaso.

---

## 12. Lección de la Fase 1

Los tres hallazgos que más importaron —la fila real de Lucía Fernández en `lawyer_profiles`, el
valor inválido `'Responsabilidad Civil Profesional'` colado por segunda vez, y
`'Medicina de Emergencia'` fuera de las 20 áreas de `baselines.py`— **no se detectaron leyendo
código. Se detectaron ejecutando contra una base de datos real.**

Aplica lo mismo en las fases siguientes: las métricas no se validan inspeccionando el script, se
validan corriéndolo y comparando contra valores esperados fijados de antemano.
