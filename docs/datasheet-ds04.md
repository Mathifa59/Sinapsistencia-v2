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

---

## 4. Composición del panel de adjudicadores — estado a 2026-09-04

**Preregistrado antes de recibir ningún juicio**, en el mismo sentido que
§2: escrito antes de que exista `ds04_qrels.csv`, para que no pueda leerse
como una explicación posterior a un resultado ya conocido.

**Instrumento generado:** 5 copias idénticas del pool completo (20
consultas, `--n-queries 20`), no divididas entre personas —
`docs/adjudicacion-definitivo-01.xlsx` a `-05.xlsx`, generadas con
`ml-service/evaluation/build_instrument.py --copy-id`, mismo `--seed` en
las 5. Verificado antes de entregarlas: las 5 hojas «Adjudicación» son
celda por celda idénticas (mismo pool, mismo orden, mismos 28 duplicados
sobre 187 pares únicos = 215 filas); solo difiere la etiqueta de copia en
la hoja «Registro», sin metadata que identifique a ninguna persona. Esto es
lo que exige el protocolo §3/§7.1 para calcular kappa de Cohen entre
evaluadores: cada fila debe ser comparable uno a uno entre copias.

**Composición confirmada hasta ahora:** los adjudicadores confirmados son
**médicos**. No hay, a esta fecha, un abogado de derecho médico confirmado
en el panel. El protocolo (§3) preveía idealmente un adjudicador secundario
del ámbito legal, además del principal — esa posibilidad **existe, no está
descartada, pero tampoco está confirmada** a la fecha de este preregistro.

**Nota condicional — la resolución depende de qué panel exista al momento
de adjudicar, no se decide aquí de antemano:**

- **Si el panel se mantiene compuesto solo por médicos** al momento de
  adjudicar: el kappa de Cohen resultante mide **consistencia
  intra-gremio médico**, no concordancia entre disciplinas — la dimensión
  jurídica del juicio (§4 del protocolo) queda evaluada exclusivamente
  desde una perspectiva clínica, sin contraste independiente desde el
  derecho. Esto se anota entonces como **limitación definitiva** en este
  datasheet y en el §9 del protocolo (que ya anticipa el escenario de
  "adjudicador único" sin secundario, aunque aquí son varios adjudicadores
  homogéneos, no uno solo — matiz distinto, misma raíz: falta perspectiva
  disciplinar independiente).
- **Si se suma un abogado de derecho médico al panel**, sea antes o
  después de esta fecha: se incorpora como **adjudicador secundario del
  ámbito legal**, tal como el diseño original del protocolo (§3)
  contemplaba. Su copia del instrumento se genera aparte con
  `--copy-id` (mismo `--seed`, mismo pool completo, para que siga siendo
  comparable fila a fila contra las 5 copias médicas), y su participación
  se documenta en el acta de adjudicación (protocolo §10), no reabriendo
  este datasheet.

Ver también: `docs/protocolo-adjudicacion_1.docx` §11 (registro de
desviaciones), donde queda la misma nota condicional con fecha y motivo.

---

## 5. Panel de abogados — segunda colección, derivada del mismo pool (2026-09-14)

**Preregistrado antes de recibir ningún juicio de este panel**, mismo
sentido que §2 y §4.

### 5.1 Dos colecciones distintas del mismo pool de 187 pares

El pool de 187 pares únicos (§1) alimenta **dos instrumentos distintos**,
no uno:

| | Panel médicos | Panel abogados |
|---|---|---|
| Archivo | `docs/adjudicacion-definitivo-01.xlsx` … `-05.xlsx` | `docs/adjudicacion-abogados-01.xlsx` … `-10.xlsx` |
| Copias | 5 | 10 |
| Consultas | Las 20, cobertura completa | Las 20, cobertura parcial (ver §5.3) |
| Pares únicos | 187 (el pool completo) | 49 (subconjunto fijo del pool) |
| Duplicados | 28 (15 %) | 7 (~15 %) |
| Filas totales | 215 | 56 |
| Script | `build_instrument.py` | `build_lawyer_panel.py` |

**Relación exacta:** el conjunto de 49 pares del panel de abogados es un
**subconjunto estricto** del pool de 187 — cada (consulta, abogado) del
panel de abogados existe también en el pool completo que usa el panel
médico, por construcción (`build_lawyer_panel.py` lee `per_query_union`
con la misma función `pool_for_query` sobre el mismo corpus y el mismo
`RANDOM_STATE`, no un pool recalculado). Verificado programáticamente, no
solo por diseño: las 49 filas del panel de abogados aparecen las 49 dentro
del pool de 187.

**Implicación para métricas de concordancia entre paneles:** kappa (u
otra medida de concordancia) entre el panel médico y el panel de abogados
**debe calcularse solo sobre la intersección de pares que ambos grupos
evaluaron** — los 49 pares del panel de abogados, no los 215 del panel
médico. Los 166 pares que solo evaluó el panel médico no tienen
contraparte de abogados con la que comparar. Calcular concordancia sobre
el total de cualquiera de los dos conjuntos sin restringir a la
intersección produciría un número sin sentido (comparando filas que el
otro panel nunca vio).

### 5.2 Confirmación del Par B con el panel de abogados

El Par B (Estefanía Rojas / Gonzalo Manrique) **nunca co-ocurre** en el
pool de ninguna consulta y **nunca produce intrusión** por área
equivocada (§2.2) — a diferencia de los pares A y C, no existe una
"consulta de intrusión" en la que incluirlo. Se incluye cada miembro por
separado, en su propia área correcta: **Estefanía en q11** (Urología),
**Gonzalo en q12** (Gastroenterología).

Esto **no repite la prueba de los otros dos pares** — busca otra cosa: si
el panel de abogados, con juicio humano independiente del panel médico,
**también** encuentra a Estefanía y Gonzalo apropiados en sus respectivas
áreas sin señal de confusión, eso **confirma con un segundo panel** el
hallazgo ya registrado en §2.2 de que este par no genera ambigüedad
observable — no es una laguna de la selección, es la validación cruzada
de un resultado ya conocido.

### 5.3 Desviación preregistrada — cobertura de especialidades escasas reducida

El criterio original pedía cobertura "completa o casi completa" de las 5
consultas de especialidad escasa (q16–q20) dentro de un total de 40–50
pares. **Esto no era posible:** cobertura completa de las 5 solas ya
suma **49 pares** (q16=10, q17=10, q18=8, q19=10, q20=11), lo que por sí
solo casi agota el presupuesto total antes de cubrir las otras 15
consultas y los pares difíciles.

**Desviación adoptada:** tope fijo de **4 candidatos por consulta
escasa** (20 pares en total para las 5), en vez de cobertura completa.
Tabla final verificada (49 pares, dentro del rango 40–50 pedido, en el
borde superior por decisión explícita de no recortar más la cobertura de
las escasas):

| Consulta | Especialidad | Escasa | Candidatos | Obligatorios |
|---|---|---|---|---|
| q01 | Cirugía General | | 4 | Rubén Gutiérrez, Karina Sotelo (Par A) |
| q02 | Ginecología y Obstetricia | | 1 | — |
| q03 | Traumatología | | 1 | — |
| q04 | Medicina General | | 3 | Pilar Zevallos, Julio Aliaga (Par C) |
| q05 | Pediatría | | 1 | — |
| q06 | Cirugía General | | 3 | Pilar Zevallos, Julio Aliaga (Par C) |
| q07 | Cardiología | | 1 | — |
| q08 | Neurología | | 2 | Julio Aliaga (Par C, sin Pilar) |
| q09 | Oncología | | 1 | — |
| q10 | Anestesiología | | 1 | — |
| q11 | Urología | | 4 | Pilar Zevallos, Julio Aliaga (Par C) + Estefanía Rojas (Par B) |
| q12 | Gastroenterología | | 2 | Gonzalo Manrique (Par B) |
| q13 | Psiquiatría | | 1 | — |
| q14 | Cardiología | | 3 | Pilar Zevallos, Julio Aliaga (Par C) |
| q15 | Traumatología | | 1 | — |
| q16 | Dermatología | sí | 4 | Pilar Zevallos, Julio Aliaga (Par C) |
| q17 | Endocrinología | sí | 4 | — |
| q18 | Nefrología | sí | 4 | Julio Aliaga (Par C, sin Pilar) |
| q19 | Reumatología | sí | 4 | — |
| q20 | Hematología | sí | 4 | Pilar Zevallos, Julio Aliaga (Par C) |
| **Total** | | | **49** | |

Candidatos "extra" (no obligatorios) que completan cada cupo: criterio
explícito, no aleatorio — el candidato que aparece en más de las 7
variantes pooleadas para esa consulta (varios métodos de ranking
coinciden en recomendarlo), desempate por `lawyer_id` ascendente. Código:
`ml-service/evaluation/build_lawyer_panel.py` (`TARGET`, `MANDATORY`,
`build_selection`).

### 5.4 Duplicados compartidos con el panel médico donde es posible

De los 28 pares duplicados en el panel médico, **11 también están
dentro** de los 49 pares del panel de abogados. Los 7 duplicados del
panel de abogados se seleccionan **priorizando ese conjunto de 11** (ver
`priority_duplicate_pairs` en `build_lawyer_panel.py`) — con 11
disponibles y solo 7 necesarios, los 7 salen íntegramente de pares que
**también** están duplicados en el panel médico, para que la medición de
consistencia intra-evaluador sea comparable entre ambos paneles sobre los
mismos ítems repetidos, no solo sobre una tasa similar.

### 5.5 Anonimato y generación

10 copias idénticas (`docs/adjudicacion-abogados-01.xlsx` … `-10.xlsx`),
mismo `--seed`, verificadas celda por celda idénticas entre sí (0
diferencias sobre 56×12 celdas comparadas × 9 pares de copias). Sin
metadata que identifique a nadie (`creator=openpyxl`, sin
`lastModifiedBy`, sin `title`). `--copy-id` parametrizado para copias
sueltas adicionales, igual que el panel médico.

---

## 6. Bug corregido — duplicados perdían el par correcto (2026-09-14)

**Encontrado antes de distribuir el panel de abogados**, al verificar la
intersección de pares duplicados entre ambos paneles (§5.4) — la
verificación cruzada para una cosa (comparabilidad de duplicados entre
paneles) expuso un defecto distinto (duplicados corruptos dentro de un
mismo archivo).

### 6.1 Causa raíz

`build_rows()` (`build_instrument.py`) insertaba duplicados dentro de un
bucle que leía `dict(rows[orig_idx])` — pero `rows` es la lista de
**salida**, que va creciendo dentro de ese mismo bucle por cada
`rows.insert()`. `orig_idx` son índices calculados contra `base_rows`
**antes** de cualquier inserción. Una vez que una inserción cae en una
posición ≤ un `orig_idx` posterior en la iteración, ese índice deja de
apuntar a la fila original que debía — apunta a una fila ya desplazada
por la inserción anterior, a veces a un duplicado recién insertado.

**El total de filas se mantiene correcto pese al bug** (215 = 187+28),
porque el número de operaciones `insert()` no cambia — lo que cambia es
**cuáles** pares terminan duplicados. Por eso verificar solo el total no
detecta este defecto; hace falta verificar el conjunto real de pares
duplicados contra el conjunto pretendido.

### 6.2 Efecto verificado en el instrumento de médicos ya generado

De los 28 duplicados pretendidos:

- **2 pares quedaron triplicados** (3 apariciones en vez de 2):
  - Abg. Fernando Zúñiga Palacios — consulta sobre resultado estético de
    una cicatriz tras intervención abdominal.
  - Abg. Mateo Huamán Ríos — consulta sobre cateterismo con disección
    coronaria.
- **2 pares que debían duplicarse no se duplicaron** (su lugar en
  `chosen_originals` terminó apuntando, por el desplazamiento de índices,
  a una de las filas ya triplicadas de arriba en vez de a su propio
  original).
- Total de filas: 215, correcto. Total de pares únicos con al menos una
  aparición: 187, correcto. Conjunto real de pares con exactamente 2
  apariciones: 26, no 28.

### 6.3 Corrección

Leer siempre de `base_rows` (inmutable), nunca de `rows`:
`dup = dict(base_rows[orig_idx])`. Extraído a una función compartida,
`insert_duplicates()` en `build_instrument.py`, reutilizada por
`build_lawyer_panel.py` — un solo lugar donde puede volver a introducirse
este bug, no dos.

**Verificación estructural agregada al propio código**, no solo
comprobada una vez a mano: `insert_duplicates()` calcula el conjunto de
pares que *pretendía* duplicar (`chosen_originals`, contra `base_rows`) y
al final compara ese conjunto contra el conjunto real de pares con
exactamente 2 apariciones en la salida — si no coinciden, o si alguna
fila quedó con 3 o más apariciones, la función lanza `AssertionError` y
la generación se detiene. Cualquier corrida futura se autoverifica; no
depende de que alguien vuelva a comprobarlo a mano.

### 6.4 Estado tras la corrección — confirmado, ambos paneles reemplazados

**Se detectó y corrigió antes de distribuir cualquier archivo a un
adjudicador.** El investigador confirmó (2026-09-14) que ningún médico ni
abogado del panel había empezado a responder — ni las copias con el bug
llegaron a usarse. Con esa confirmación, ambos paneles se regeneraron
**desde cero** con la corrección aplicada y reemplazaron directamente los
archivos en `docs/` (no quedó una versión con el bug en ningún lugar del
repositorio).

**Verificación de los 15 archivos finales**, explícita sobre el conjunto
de pares duplicados —no solo el total de filas, que fue justo lo que
falló la vez anterior—:

| Archivo | Únicos | Duplicados (2×) | 3× o más | Total filas | Sin metadata |
|---|---|---|---|---|---|
| adjudicacion-definitivo-01.xlsx | 187 | 28 | 0 | 215 | ✅ |
| adjudicacion-definitivo-02.xlsx | 187 | 28 | 0 | 215 | ✅ |
| adjudicacion-definitivo-03.xlsx | 187 | 28 | 0 | 215 | ✅ |
| adjudicacion-definitivo-04.xlsx | 187 | 28 | 0 | 215 | ✅ |
| adjudicacion-definitivo-05.xlsx | 187 | 28 | 0 | 215 | ✅ |
| adjudicacion-abogados-01.xlsx … -10.xlsx (las 10) | 49 | 7 | 0 | 56 | ✅ |

Verificado además: las 5 copias médicas son celda por celda idénticas
entre sí (0 diferencias vs. copia 01), y las 10 copias de abogados
también (0 diferencias vs. copia 01) — el criterio de comparabilidad
fila a fila (protocolo §3/§7.1, §5.1 de este documento) se mantiene tras
la corrección.

No queda ninguna copia con el bug en `docs/`. El directorio de
transición `docs/pendiente-reemplazo-duplicados/` se eliminó una vez
completado el reemplazo.
