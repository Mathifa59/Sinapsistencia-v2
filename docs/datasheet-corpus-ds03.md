# Datasheet — Corpus DS-03 (perfiles de abogados)

Decisiones operativas del corpus generado por
[`ml-service/evaluation/build_corpus.py`](../ml-service/evaluation/build_corpus.py)
que no encajan en `docs/taxonomia-legal.md` (normativa) ni en
`docs/vectorizacion-tfidf-matching.md` (mecánica del vectorizador). Formato
inspirado en Gebru et al. — *Datasheets for Datasets* (2018), el mismo
espíritu que el futuro `docs/model_card_matching.md` (Mitchell et al., 2019).

---

## 1. Campos omitidos deliberadamente: `current_caseload` / `max_caseload`

El esquema original de DS-03 (`docs/MATCHING-SPEC.md` §3.1) los incluía. Se
omiten ambos, no solo `max_caseload`:

- **Ninguna variante del estudio de ablación (§5) los usa.** El filtro duro
  de disponibilidad es únicamente `available` (booleano).
- **No existe fuente de verdad en producción para `max_caseload`** — ningún
  concepto de "capacidad" existe hoy en el backend, ni como columna ni
  calculado.
- `current_caseload` sí sería derivable con una consulta (`COUNT(*) FROM
  cases WHERE lawyer_id = ? AND status NOT IN (...)`), pero como ninguna
  variante lo consume, agregarlo sería ruido sin uso, no señal.

**No se agregaron columnas a `lawyer_profiles`.** El esquema corregido del
corpus (§3.1 del spec, enmendado) es: `lawyer_id, full_name, bar_number,
specialties[], medical_areas[], years_experience, rating, resolved_cases,
biography`.

---

## 2. Coherencia bio ↔ `medical_areas` — regla y su excepción

**Regla general:** toda biografía debe ser semánticamente coherente con el
`medical_areas[]` del perfil. Una bio que describe contenido clínico de un
área distinta a la declarada contamina el componente textual del matching
para consultas de esa especialidad — encontrado y corregido en Claudia Chávez
(bio de dermatología/endocrinología con área Cardiología) y reforzado en
Rocío Ochoa (bio demasiado genérica para su área).

**Excepción documentada — los 3 pares "difíciles":** los perfiles marcados
`pair` (A, B, C) están **exentos** de este requisito, siempre que la bio **no
afirme** un área distinta de la del campo estructurado. La distinción:

| | ¿Rompe el par? |
|---|---|
| Bio genérica, sin contenido clínico específico de ninguna área | ❌ No — es el diseño: solo `medical_areas`/`specialties` deben discriminar entre los dos miembros del par |
| Bio que describe contenido clínico de un área **distinta** a la declarada | ✅ Sí — deja de ser "difícil por diseño" y pasa a ser una contradicción real |

Ejemplo aplicado: la bio de Pilar Zevallos (par C, Gastroenterología) dice
"tratamiento crónico" sin más — genérico, no afirma otra área, **no se
corrige**. Se había editado a "tratamiento crónico para su enfermedad
digestiva" para reforzar Gastroenterología, pero eso introducía una señal
textual que el par no debía tener — revertido a la versión genérica original.

---

## 3. Seguridad de las 33 cuentas nuevas (solo-corpus)

Los 33 perfiles agregados en `ml-service/evaluation/build_corpus.py` (no los
12 preservados) no son cuentas de demo interactiva — existen únicamente para
que el corpus tenga volumen suficiente (Precision@3 no discrimina con 8-12
candidatos). Aun así, quedan como filas reales en `profiles`/`lawyer_profiles`
si se aplica V12, con dos requisitos en tensión:

1. **Deben ser candidatos plenos del matching en vivo** (`is_active = TRUE`),
   no solo del corpus JSON offline — de lo contrario la evaluación offline
   mide sobre 45 perfiles y la aplicación desplegada sobre 12, invalidando la
   equivalencia entre lo que se evalúa y lo que se demuestra ante el jurado.
2. **No deben ser autenticables** — 33 cuentas con contraseña conocida
   (`Demo123!`, la misma de todas las cuentas demo) en un sistema desplegado
   en Railway es superficie de ataque innecesaria.

**Resolución:** `is_active = TRUE` para las 33 + `password_hash` generado por
perfil con `secrets.token_urlsafe(32)` → `bcrypt.hashpw()`, verificado en
formato y funcionalidad (`bcrypt.checkpw()`) antes de escribirse, y **la
contraseña en texto plano se descarta de inmediato** — nunca se imprime, nunca
se guarda, no existe en ningún artefacto de este repositorio. Nadie puede
autenticarse porque nadie —ni siquiera quien generó el corpus— conoce la
contraseña.

Verificado tras generar V12: 33 hashes con formato bcrypt válido, los 33
distintos entre sí, ninguno coincide con el hash compartido de `Demo123!`.

---

## 5. Ampliación de producción post-congelamiento (2026-09-30)

**No es una corrección del corpus DS-03.** Es una decisión de producto tomada
después de que la evaluación DS-03/DS-04 ya estaba cerrada, y se documenta
aquí para que quede explícito que el corpus congelado (§ arriba) y los datos
en vivo de `lawyer_profiles` **divergieron deliberadamente** a partir de esta
fecha.

**Qué cambió:** la migración `V14__expand_scarce_specialty_lawyers.sql`
agrega 8 perfiles de abogado nuevos (`INSERT`, rango de id `b6000000-...`),
subiendo cada una de las 8 especialidades "Escasa" de §TARGET_COVERAGE
(Dermatología, Endocrinología, Oftalmología, Neumología, Nefrología,
Reumatología, Infectología, Hematología) de 2 a 4 abogados con `medical_areas`
que la mencionan — al nivel de la franja "Media" original, no más.

**Por qué era seguro hacerlo sin invalidar DS-03/DS-04:** verificado en el
código, no supuesto, antes de aplicar la migración —
[`build_test_collection.py:42`](../ml-service/evaluation/build_test_collection.py),
`run_ablation.py:53` (importa la misma constante `CORPUS_PATH`) y
[`build_lawyer_panel.py:78-83`](../ml-service/evaluation/build_lawyer_panel.py)
(importa `CORPUS_PATH` y `pool_for_query` del mismo módulo) leen
**exclusivamente** el snapshot congelado
`ml-service/data/reference/ds03_lawyers.json`, escrito una sola vez por
`build_corpus.py`. Ninguno de los tres consulta `lawyer_profiles` en vivo.
Por lo tanto: (1) los 45 perfiles y sus `specialties`/`medical_areas`/`bio` ya
evaluados quedan intactos — V14 es únicamente `INSERT`, nunca `UPDATE`; (2) re-
correr cualquiera de los tres scripts hoy reproduce el mismo JSON de entrada y
por lo tanto el mismo pool, RANDOM_STATE=42 incluido; (3) los instrumentos ya
distribuidos (`docs/adjudicacion-definitivo-0X.xlsx`,
`docs/adjudicacion-abogados-0X.xlsx`) siguen siendo válidos sin regenerarse.

**Consecuencia práctica a futuro:** los 8 perfiles nuevos son candidatos
plenos del *matching en producción* (`is_active = TRUE`, `available = TRUE`)
pero **invisibles** para `build_corpus.py`/`ds03_lawyers.json` — no van a
aparecer en ninguna corrida futura de `build_test_collection.py` ni
`run_ablation.py` a menos que alguien los agregue explícitamente a
`EXISTING_LAWYERS`/`NEW_LAWYERS` y regenere el corpus como un DS-03-v2
deliberado (con su propio datasheet, no como edición silenciosa de este).

---

## 6. Ver también

- [`docs/taxonomia-legal.md`](taxonomia-legal.md) — sustento normativo de las
  8 etiquetas de `specialties[]`.
- [`docs/vectorizacion-tfidf-matching.md`](vectorizacion-tfidf-matching.md) —
  qué campos concatena el vectorizador TF-IDF y su implicancia para Fase 3.
