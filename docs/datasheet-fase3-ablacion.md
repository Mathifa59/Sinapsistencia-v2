# Datasheet — Fase 3 (estudio de ablación)

Decisiones metodológicas de
[`ml-service/evaluation/run_ablation.py`](../ml-service/evaluation/run_ablation.py).
Mismo espíritu que `docs/datasheet-corpus-ds03.md` y `docs/datasheet-ds04.md`:
preregistrado significa escrito **antes** de que exista `ds04_qrels.csv` con
juicios humanos reales, para que la decisión no pueda estar contaminada por
haber visto el resultado.

---

## 1. El problema: comparaciones múltiples sin corregir

`run_ablation.py` evalúa 8 variantes de ablación (`docs/MATCHING-SPEC.md`
§5). Comparar cada variante contra cada otra da C(8,2) = **28 pruebas de
permutación pareadas**.

Con 28 pruebas a α = 0.05 cada una, sin ajustar, la probabilidad de al
menos un falso positivo por puro azar —incluso si **ninguna** variante
difiere realmente de otra— es sustancial: aproximadamente
1 − (1 − 0.05)^28 ≈ 76&nbsp;%. En la práctica esperable, entre 1 y 2 de las
28 comparaciones saldrían "significativas" solo por el volumen de pruebas,
no por una diferencia real.

Es el mismo problema de fondo que el umbral del Par C en
`docs/datasheet-ds04.md` §2.3: un resultado que parece evidencia pero que
en realidad depende de cómo se planteó el análisis. La corrección ahí fue
fijar el umbral antes de conocer el resultado. La corrección aquí es la
misma lógica aplicada a comparaciones múltiples: decidir **antes** cuáles
28 comparaciones cuentan como confirmatorias del argumento central y
cuáles son exploratorias, y ajustar el umbral de significancia de cada
grupo según corresponda — no elegir la corrección después de ver qué
salió significativo.

## 2. La división — preregistrada 2026-09-02

### 2.1 Confirmatorias (7)

**`composite-070` contra cada una de las otras 7 variantes.**

Es la pregunta que sostiene la tesis: si el score compuesto vigente en
producción (70 % coseno + 30 % desempeño) supera a cada uno de sus
componentes evaluado por separado (`area-match`, `tfidf-full`, `bio-only`,
`performance-only`, `bm25`), al piso (`random`) y a la variante que
demuestra que 0.70 no es arbitrario (`composite-sweep`).

**Reportadas a α = 0.05, sin ajustar.** Son pocas (7) y están directamente
ligadas al argumento central — no es un barrido exploratorio de todas las
combinaciones posibles, es la comparación que la tesis necesita sostener.

### 2.2 Exploratorias (21)

**Todas las combinaciones restantes** entre las 7 variantes que no son
`composite-070` (C(7,2) = 21). Por ejemplo `area-match` vs. `bio-only`,
`tfidf-full` vs. `bm25`, etc.

**Reportadas con corrección de Bonferroni:** α = 0.05 / 21 ≈ 0.00238.

Sirven para entender el comportamiento del sistema (¿el texto libre aporta
más que la coincidencia de área sola? ¿BM25 rankea distinto que el coseno
TF-IDF?), **no para sustentar la decisión de diseño** de usar el score
compuesto. Se etiquetan explícitamente como exploratorias en el reporte
(`run_ablation.py` → `report["pairwise_permutation_p3_strict"]["exploratory"]`)
para que nadie las cite como si fueran confirmatorias.

### 2.3 Por qué Bonferroni y no Benjamini-Hochberg

El enunciado del problema permitía cualquiera de las dos. Se eligió
**Bonferroni** porque su umbral (α / n_comparaciones) se calcula de una
cantidad conocida de antemano — el número de comparaciones exploratorias,
fijo en 21 — y por lo tanto es preregistrable hoy sin ver ningún dato.
Benjamini-Hochberg calcula su punto de corte ordenando los p-valores
**ya obtenidos**; es una corrección válida y menos conservadora, pero el
umbral de rechazo no queda fijado hasta que existen los p-valores, lo que
lo vuelve un procedimiento dependiente del resultado, no un número que se
pueda anotar aquí antes de correr el análisis. Para el objetivo de este
documento — fijar la regla antes del dato — Bonferroni es la elección más
estricta en el sentido de "no dejar ningún grado de libertad post-hoc",
aunque sea estadísticamente más conservadora.

## 3. Qué NO se hace con esto

No se corrige el número de comparaciones confirmatorias para bajar el
umbral por debajo de 0.05 — 7 comparaciones ligadas directamente al
argumento central de la tesis se reportan tal como el protocolo estadístico
estándar las trataría en un estudio confirmatorio: sin ajuste. No se elige
Bonferroni vs. Benjamini-Hochberg mirando cuál produce más resultados
significativos — la elección de §2.3 se hizo antes de tener ningún p-valor
real que comparar. No se mueve ninguna variante del grupo confirmatorio al
exploratorio (ni viceversa) una vez que existan los qrels reales, sin que
ese cambio quede registrado aquí como una desviación explícita, con fecha
y motivo — igual que la sección 11 de `protocolo-adjudicacion_1.docx`.

## 4. Verificación

La división y los umbrales están codificados en
`run_ablation.py` (`CONFIRMATORY_PIVOT`, `CONFIRMATORY_PAIRS`,
`EXPLORATORY_PAIRS`, `ALPHA_CONFIRMATORY`, `ALPHA_EXPLORATORY_BONFERRONI`),
no solo documentados aquí en prosa — `--self-test` verifica en cada corrida
que hay exactamente 7 comparaciones confirmatorias y 21 exploratorias, que
el pivote (`composite-070`) aparece en todas las confirmatorias y en
ninguna exploratoria, y que el umbral de Bonferroni es más estricto que el
umbral confirmatorio.
