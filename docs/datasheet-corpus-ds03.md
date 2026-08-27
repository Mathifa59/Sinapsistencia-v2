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

## 4. Ver también

- [`docs/taxonomia-legal.md`](taxonomia-legal.md) — sustento normativo de las
  8 etiquetas de `specialties[]`.
- [`docs/vectorizacion-tfidf-matching.md`](vectorizacion-tfidf-matching.md) —
  qué campos concatena el vectorizador TF-IDF y su implicancia para Fase 3.
