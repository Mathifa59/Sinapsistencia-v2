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

### 4.5 Instrumento

⚠ Proporciones alineadas al protocolo (la v1 daba cantidades absolutas):

- **Duplicados: 10 %** del total, separados al menos 30 posiciones de su primera aparición, sin
  identificación visible
- **Justificación escrita: 15 %** de los pares, distribuidos a lo largo del instrumento

Formato XLSX con tres hojas —Instrucciones, Adjudicación, Registro— siguiendo la estructura del
piloto ya entregado. Validación de datos en las columnas de relevancia (0/1/2) y confianza
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
- **Prueba de permutación pareada** entre variantes
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

## 8. Calibración del clasificador de riesgo (Fase 5, alcance reducido)

Tarea menor, aproximadamente una jornada. **No expandir.** El clasificador de riesgo no es el
núcleo de la tesis: el médico declara su urgencia percibida y puede sobrescribir la sugerencia.

Sobre `NPDB2601.CSV`, filtros `RECTYPE == 'P'`, `LICNFELD in (10, 20)`, `OUTCOME != 10` →
**210 304 registros** (verificado; si tu ETL da otro número, hay un bug).

Estimar la distribución de severidad condicionada a especialidad y contrastarla con
`SPECIALTY_BASELINE` en `ml-service/app/risk/baselines.py`.

Transferir **efectos condicionales**, no frecuencias marginales: el NPDB solo contiene reclamos
que terminaron en pago, y ese sesgo de selección afecta las marginales.

**No importar montos de pago.** Reflejan el sistema legal estadounidense y no son transferibles.

**No reentrenar el modelo.** Ajustar hiperparámetros sobre un corpus cuya etiqueta deriva del
propio generador produce un número más alto pero no más creíble. Salida:
`docs/calibracion-baselines.md` con la comparación y cada cambio justificado.

---

## 9. Entregables

```
ml-service/
├── data/reference/
│   ├── ds03_lawyers.json            ✅
│   ├── ds04_queries.json
│   ├── ds04_pool.xlsx
│   └── ds04_qrels.csv
├── evaluation/
│   ├── build_corpus.py              ✅
│   ├── build_test_collection.py
│   ├── run_ablation.py
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
├── protocolo-adjudicacion           ✅
├── calibracion-baselines.md
└── model_card_matching.md
```

---

## 10. Criterios de aceptación

- [x] Corpus de 45 abogados con biografías variadas, no plantilla
- [x] 20 consultas con al menos 3 de especialidades escasas — 5 de 20, verificado
- [x] Pool con profundidad top-3, total ≤ 250 pares incluidos duplicados — 187 únicos, 206 con duplicados
- [ ] Instrumento sin filtrar la salida del modelo, con 10 % duplicados y 15 % justificaciones
- [ ] Las 8 variantes de ablación ejecutadas y reportadas
- [ ] `bio-only` construida sin modificar `_lawyer_text()` en producción
- [ ] Métricas con intervalos de confianza por bootstrap
- [ ] Barrido de α con validación dejando una consulta fuera
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
