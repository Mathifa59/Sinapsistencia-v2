# Datasheet — Colección de prueba DS-04

Decisiones y observaciones operativas de
[`ml-service/evaluation/build_test_collection.py`](../ml-service/evaluation/build_test_collection.py).
Mismo espíritu que `docs/datasheet-corpus-ds03.md`. Complementa
`docs/vectorizacion-tfidf-matching.md` (mecánica del vectorizador) y
`docs/taxonomia-legal.md` (sustento de las etiquetas).

---

## 1. Parámetros finales del pool (Fase 2)

- **Profundidad:** top-3 por variante.
- **Variantes pooleadas:** 7 de 8 — `random` queda fuera, se calcula
  analíticamente (`k/n` sobre los qrels, Fase 3).
- **Volumen real:** 187 pares únicos, 206 con el 10 % de duplicados (tope: 250).
- Historial completo de la decisión (bug de `POOL_DEPTH` fijo detectado y
  corregido, conflicto top-10/top-5 entre protocolo y spec, seis escenarios
  medidos): `MATCHING-SPEC.md` §4.2 y §4.4.

---

## 2. Observaciones preregistradas sobre los pares difíciles

**Preregistradas** en el sentido estricto: escritas antes de generar
`ds04_qrels.csv` y antes de correr `run_ablation.py` (Fase 3). Su valor
depende de que existan por escrito ahora, no después de ver si el compuesto
"acierta". No se puede editar esta sección una vez que existan los qrels sin
que la edición quede registrada como tal.

### 2.1 Par A (Rubén Gutiérrez / Karina Sotelo) — confusión puntual, como se diseñó

Área declarada: Urología / Psiquiatría. Bio compartida: variante corta
("procedimientos ambulatorios de baja complejidad... consentimientos
incompletos...").

Ambos se cuelan en el pool de **q01** (Cirugía General — ni Urología ni
Psiquiatría) vía `tfidf-full`, `bio-only` y `bm25`. Es la **única** consulta
donde este par produce una intrusión por área equivocada — comportamiento
puntual y acotado, consistente con el diseño original: bio deliberadamente
genérica, sin afirmar ningún área, para que solo `medical_areas[]` discrimine
entre ambos miembros del par.

**Predicción cuantitativa, umbral fijado ahora:** de la única consulta donde
el Par A intruye (q01, n=1), ¿`composite-070` desplaza a **ambos** miembros
fuera de su propio top-3? Con n=1 el umbral de 6/4 del Par C (§2.3) no
tiene sentido — se reporta el hecho crudo, no se fuerza un veredicto de
"evidencia" o "limitación" sobre una sola observación.

**Hecho ya determinado por los datos del pool (no depende de qrels):**
`composite-070` desplaza a **ambos** (Rubén y Karina) fuera de su top-3 en
q01. Esto es una posición de ranking ya calculada al construir el pool, no
una predicción a futuro — se reporta aquí en vez de ocultarla hasta Fase 3
porque ya está determinada.

**Lo que sigue pendiente de qrels:** que los desplace no prueba por sí solo
que el desplazamiento sea una *mejora* — falta confirmar con los juicios
humanos que los 3 candidatos que sí quedan en el top-3 de `composite-070`
para q01 puntúan igual o mejor en relevancia que Rubén/Karina. Desplazamiento
es necesario pero no suficiente para sostener "el desempeño corrige
intrusiones"; con n=1 tampoco alcanzaría para sostenerlo aunque los qrels lo
confirmen — es un solo dato, ilustrativo, no una prueba.

### 2.2 Par B (Estefanía Rojas / Gonzalo Manrique) — sin intrusión observada

Área declarada: Urología / Gastroenterología. Aparecen en el pool de **q11**
(Urología) y **q12** (Gastroenterología) respectivamente, en ambos casos con
área **correcta**, vía `area-match`. No se coló en ninguna de las 20
consultas con área equivocada.

**Registro honesto, no un hallazgo nulo sin valor:** no todo par difícil
produce ambigüedad observable dado un conjunto de consultas concreto — que
las bios sean parecidas no garantiza que el texto termine resonando con
consultas de otras áreas. No hay predicción de Fase 3 que preregistrar aquí
más allá de que este par debería comportarse de forma aburrida (ranking
determinado casi enteramente por `area-match`/desempeño, poco margen para que
el componente textual puro lo desordene).

### 2.3 Par C (Pilar Zevallos / Julio Aliaga) — intrusión amplia, no puntual

Área declarada: Gastroenterología / Reumatología. Bio compartida: "Atiende
sobre todo casos donde el paciente reclama no haber entendido de verdad el
tratamiento crónico que le indicaron, más allá de haber firmado el papel
correspondiente. Sostiene que buena parte de estos reclamos se resuelven si
el médico documenta la conversación, no solo la firma."

Se cuelan con área equivocada en **8 de las 20 consultas** (40 %): q04, q06,
q08, q11, q14, q16, q18, q20. Julio aparece en las 8; Pilar en 6 de ellas.
Solo tienen área correcta en **q19** (Reumatología, Julio).

| Consulta | Especialidad | Quién se cuela | Vía |
|---|---|---|---|
| q04 | Medicina General | Julio, Pilar | tfidf-full, bio-only, composite-070, bm25 |
| q06 | Cirugía General | Pilar, Julio | tfidf-full, bio-only, bm25 |
| q08 | Neurología | Julio | bio-only |
| q11 | Urología | Pilar, Julio | tfidf-full, bio-only, bm25 |
| q14 | Cardiología | Pilar, Julio | tfidf-full, bio-only, bm25 |
| q16 | Dermatología | Pilar, Julio | tfidf-full, bio-only, composite-070, bm25 |
| q18 | Nefrología | Julio | tfidf-full, bio-only |
| q20 | Hematología | Julio, Pilar | tfidf-full, bio-only, bm25 |

**Esto excede claramente el efecto "confusión puntual entre dos áreas
específicas" que el diseño de pares buscaba.** La lectura más probable: la
frase "no haber entendido de verdad el tratamiento... documenta la
conversación, no solo la firma" coincide con un tema narrativo que aparece en
muchas de las 20 consultas (documentación/explicación insuficiente del
proceso), independientemente del área clínica de cada una — no es que el
sistema confunda Gastroenterología con Reumatología, es que la bio de este
par es lo bastante genérica como para resonar con "quejas por documentación
pobre" en general.

**Predicción cuantitativa única, umbral fijado ahora — antes de conocer el
resultado completo:**

> De las 8 consultas donde el Par C intruye por área equivocada, se cuenta
> una consulta como **"corregida"** solo si `composite-070` desplaza fuera de
> su propio top-3 a **todos** los miembros del par que intruyeron en esa
> consulta (no basta con desplazar a uno si el otro se queda).
>
> - **≥ 6 de 8 corregidas** → evidencia de que el componente de desempeño
>   corrige intrusiones textuales, incluso cuando la intrusión es amplia.
> - **≤ 4 de 8 corregidas** → limitación real del corpus DS-03: la bio de
>   este par es demasiado genérica y el desempeño no alcanza a compensarlo.
> - **Exactamente 5** → resultado ambiguo, se reporta como tal, sin forzar
>   una lectura hacia ningún lado.

**No es una predicción a ciegas: la parte de posición de ranking ya está
determinada por los datos del pool, y se reporta aquí en vez de esperar a
Fase 3 innecesariamente** — construir el pool ya corrió `composite-070`, así
que ya se sabe, sin usar ningún qrel, en cuántas de las 8 desplaza al par
completo:

| Consulta | Pilar desplazada | Julio desplazado | ¿Consulta corregida? |
|---|---|---|---|
| q04 | sí | **no** | No — Julio queda dentro |
| q06 | sí | sí | Sí |
| q08 | (no interviene) | sí | Sí |
| q11 | sí | sí | Sí |
| q14 | sí | sí | Sí |
| q16 | **no** | **no** | No — ambos quedan dentro |
| q18 | (no interviene) | sí | Sí |
| q20 | sí | sí | Sí |

**Resultado: 6 de 8 corregidas.** Cae en el umbral "≥ 6" fijado arriba.

**Advertencia sobre la validez de este umbral como predicción ciega.** El
umbral (≥6 / ≤4 / =5) se fijó *después* de haber reportado la tabla de la
§2.3 anterior, en la que `composite-070` ya figuraba como vía de intrusión en
q04 y q16 — es decir, con conocimiento parcial de los datos del pool que
determina el resultado. La distancia entre saber que el compuesto falla en al
menos 2 de las 8 y fijar un umbral que separa "evidencia" de "limitación" no
es información nueva independiente de esa observación previa. Que el
resultado caiga justo en el borde del umbral (exactamente 6, no 7 ni 8)
agrava el problema en vez de disolverlo. **Por decisión explícita: no se
mueve el umbral a posteriori — moverlo después de ver el resultado sería
peor que el sesgo que tiene ahora.** Lo que corresponde es declarar, no
corregir: **la mitad determinística de esta predicción (el conteo 6/8) no
constituye evidencia independiente**, porque el umbral que la clasifica como
"evidencia" no se fijó a ciegas de esa mitad.

**Lo que sigue siendo genuinamente ciego, y es lo único que cuenta como
evidencia:** que `composite-070` saque a Pilar/Julio del top-3 no prueba que
los 3 candidatos que quedan en su lugar sean efectivamente *más relevantes* —
solo prueba que el ranking cambió de posición, y esa parte se conocía antes
de fijar el umbral. La confirmación real de "el desempeño corrige
intrusiones de forma que mejora la relevancia" requiere que los qrels de
Fase 3 muestren que esos reemplazos puntúan igual o mejor que Pilar/Julio en
las consultas donde hubo desplazamiento. Ese juicio sí es anterior a
cualquier dato de pool y es el resultado que se reporta como prueba de la
predicción — el conteo 6/8 queda como contexto descriptivo, no como el
resultado de la predicción cuantitativa.

---

## 3. Qué NO se hace en este documento

No se modifica ninguna bio, `specialties[]` ni `medical_areas[]` de DS-03 a
partir de estas observaciones. El corpus está cerrado (Fase 1). Si en Fase 3
los qrels muestran que el conteo 6/8 del Par C (§2.3) no se traduce en
mejoras reales de relevancia (los reemplazos no puntúan mejor que Pilar/
Julio), la acción sería documentar esa limitación en el datasheet de DS-03 y
en las limitaciones del model card (`docs/model_card_matching.md`), no
reabrir ni regenerar el corpus.
