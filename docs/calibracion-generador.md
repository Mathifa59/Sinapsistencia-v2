# Calibración del generador — NPDB (Fase 5, alcance replanteado)

Contrasta tres efectos de [`generate_risk_dataset.py`](../ml-service/training/generate_risk_dataset.py)
contra el NPDB Public Use Data File. Generado automáticamente por
[`calibrate_generator.py`](../ml-service/evaluation/calibration/calibrate_generator.py)
el 2026-08-27. Números medidos, no fabricados — revisa
[`calibracion-generador.metrics.json`](../ml-service/evaluation/calibration/calibracion-generador.metrics.json)
para el detalle completo.

**Este documento reporta una medición. No modifica el generador.**
Cualquier ajuste a `generate_risk_dataset.py` a partir de estas cifras
requiere una decisión explícita, pendiente.

## 0. Alcance y lo que quedó descartado

El §8 original del spec asumía calibración por especialidad y, alternativamente,
por naturaleza de alegato (`ALGNNATR`). Ambas quedan descartadas:

- **Por especialidad clínica — descartado.** El NPDB no expone especialidad
  clínica en ningún campo; `LICNFELD` solo distingue MD/DO. `SPECIALTY_BASELINE`
  (`ml-service/app/risk/baselines.py`) **permanece sin respaldo empírico externo.**
- **Por naturaleza de alegato (`ALGNNATR`) — descartado.** `generate_risk_dataset.py`
  no modela ninguna variable equivalente a tipo de incidente (cirugía, diagnóstico,
  medicación, obstetricia, etc.). `ALGNNATR` no tiene contraparte en el generador.

Ambas limitaciones se declaran aquí de forma expresa y se llevan a
`CLAUDE.md`/memoria del proyecto. No se sustituyen con una aproximación
inventada.

En su lugar, se contrastan **tres variables del generador con correlato
directo en el NPDB, sin pasar por especialidad ni tipo de incidente**:

| Variable del generador | Proxy NPDB | Campo(s) NPDB |
|---|---|---|
| `informed_consent` | Presencia del código de alegato 707 | `ALEGATN1`, `ALEGATN2` |
| `has_prior_complaints` | Más de un reporte de pago de mala praxis para el mismo profesional | `NPMALRPT` |
| `time_since_incident_days` | Año de procesamiento menos año del acto u omisión | `ORIGYEAR − MALYEAR1` |

## 1. Filtro y procedencia

- Filtro: `RECTYPE == 'P'` (reporte de pago, formato vigente desde 1/31/2004)
  + `LICNFELD ∈ {10, 20}` (médico MD/DO) + `OUTCOME != '10'`
  ("Cannot Be Determined from Available Records").
- `n` filtrado: **210,304** (esperado: 210,304).
- Archivo de origen: `NPDB2601.CSV` — **nunca se copia al repositorio**
  (Data Use Agreement de HRSA, `CLAUDE.md` §5.2).
- SHA-256: `1409fc66686b637033cf1d61d4e478e46fb5e6ed308a61c34f793a7b6b4f044b`
- Semilla / reproducibilidad: `RANDOM_STATE = 42`,
  bootstrap con 10,000 remuestreos.
- Librerías: numpy 2.4.4, pandas 3.0.2.

Cita obligatoria (`CLAUDE.md` §5.2):

> National Practitioner Data Bank Public Use Data File, 31 de marzo de 2026,
> U.S. Department of Health and Human Services, Health Resources and
> Services Administration, Bureau of Health Workforce, Division of
> Practitioner Data Bank.

## 2. `informed_consent` — código de alegato 707

Código verificado contra el codebook oficial del NPDB (`PublicUseDataFile-Format.pdf`,
sección `ALEGATN1`): `707 = "Failure to Obtain Consent or Lack of Informed Consent"`.
Se marca el registro si `ALEGATN1` **o** `ALEGATN2` es igual a 707.

- Con código 707: **2,248** registros (media `OUTCOME` = 4.9488).
- Sin código 707: **208,056** registros (media `OUTCOME` = 6.4344).
- Diferencia de medias (707 − sin 707): **-1.4856**,
  IC 95 % bootstrap [-1.5734, -1.3988].

`OUTCOME` es una escala ordinal 1–9 (1 = lesión emocional únicamente, 9 = muerte),
no comparable en unidades directas con `risk_score` en [0,1]. La comparación es
de **dirección y magnitud relativa del efecto**, no de valores absolutos.

**Efecto actual del generador:** `informed_consent = False` suma 0.20 a
`risk_score`, más 0.12 adicional si además `procedure_complexity == "alta"`
([`generate_risk_dataset.py:93,104-105`](../ml-service/training/generate_risk_dataset.py#L93)).

**Lectura:** la diferencia observada es negativa y el intervalo no cruza cero, en dirección opuesta a la que el generador asigna.

**Recomendación:** revisar antes de mantener el signo actual del efecto — pendiente de decisión del investigador.

## 3. `has_prior_complaints` — `NPMALRPT > 1`

`NPMALRPT` cuenta los reportes de pago de mala praxis del profesional en el
archivo público **completo**, no en el subconjunto filtrado aquí — el filtro
aplicado es a nivel de fila (`RECTYPE`/`LICNFELD`/`OUTCOME`), no de sujeto,
así que el conteo permanece correcto para cada registro individual. Se
considera "con quejas previas" cuando `NPMALRPT > 1` (este reporte más al
menos otro).

- Primer y único reporte (`NPMALRPT == 1`): **78,834** registros
  (media `OUTCOME` = 6.5639).
- Con reportes previos (`NPMALRPT > 1`): **131,470** registros
  (media `OUTCOME` = 6.3314).
- Diferencia de medias (previos − primero): **-0.2326**,
  IC 95 % bootstrap [-0.2520, -0.2125].

**Efecto actual del generador:** `has_prior_complaints = True` suma 0.15 a
`risk_score`, más 0.10 adicional si además `documentation_complete = False`
([`generate_risk_dataset.py:96,107-108`](../ml-service/training/generate_risk_dataset.py#L96)).

**Lectura:** la diferencia observada es negativa y el intervalo no cruza cero, en dirección opuesta a la que el generador asigna.

**Recomendación:** revisar el signo del efecto — el proxy NPDB apunta en dirección contraria a la que asume el generador; antes de tocar el código, considerar que un profesional con más reportes previos no necesariamente produce reclamos individuales más severos (puede reflejar mayor volumen de práctica, no mayor gravedad por caso).

## 4. `time_since_incident_days` — nota de resolución (leer antes de comparar)

**Esta comparación tiene un límite de resolución que la vuelve solo
orientativa, no uno a uno:**

1. **Unidad.** El generador mide días; el NPDB solo permite reconstruir años
   (`ORIGYEAR − MALYEAR1`). No existe forma de recuperar resolución diaria
   desde el NPDB.
2. **Dominio.** `time_since_incident_days` en el generador se muestrea
   **uniforme en [0, 365]** ([`generate_risk_dataset.py:131`](../ml-service/training/generate_risk_dataset.py#L131)):
   nunca representa una demora mayor a un año. El NPDB, en cambio, mide el
   lag entre el acto/omisión y el año en que el reporte se procesó, que
   habitualmente **excede un año** (litigios y liquidaciones de mala praxis
   suelen tardar). Los dos no están midiendo el mismo fenómeno con distinta
   unidad — están midiendo ventanas temporales de escala distinta: la
   demora de reporte de la plataforma (subanual, por diseño) frente a la
   demora real de resolución de un caso de mala praxis en EE. UU.
   (plurianual, en la mayoría de los registros).
3. **Calidad del dato.** El codebook del NPDB advierte que `MALYEAR1` puede
   contener años erróneos (p. ej. 3999) registrados tal como se reportaron,
   sin corregir. Se descartan lags fuera de [0, 20] años
   (520 de 210,304 registros, 0.2%).

Con esas salvedades, sobre los **209,784** registros con lag válido:

- Correlación de Pearson entre años transcurridos y `OUTCOME`: **0.0889**.

| Años transcurridos | n | Media `OUTCOME` |
|---|---|---|
| 0 | 1,235 | 4.8049 |
| 1 | 8,260 | 5.2920 |
| 2 | 22,952 | 6.0147 |
| 3 | 36,039 | 6.3631 |
| 4 | 39,694 | 6.4798 |
| 5 | 32,876 | 6.5751 |
| 6+ | 68,728 | 6.6407 |

**Efecto actual del generador:** `score += log1p(days) / log1p(365) * 0.08`
— un efecto saturante pequeño (máximo +0.08) dentro de una ventana que
nunca excede un año.

**Advertencia de causalidad, además del desajuste de dominio:** la
correlación positiva es débil (0.0889) y admite una
lectura opuesta a la que el generador asume. El generador supone que más
tiempo transcurrido *aumenta* el riesgo. Pero en el NPDB es igual de
plausible la causalidad inversa: los casos más severos (lesión permanente,
muerte) suelen tardar más años en litigarse y liquidarse antes de
reportarse, mientras que los casos leves se resuelven y reportan rápido.
Es decir, la severidad podría estar *causando* el lag observado, no al
revés. Esta comparación no permite distinguir entre ambas direcciones.

**Recomendación:** no ajustar la magnitud ni la forma funcional a partir de
esta comparación. El desajuste de dominio (subanual vs. plurianual) y la
ambigüedad de causalidad hacen que cualquier número derivado del NPDB para
esta variable no sea transferible sin rediseñar qué representa
`time_since_incident_days` en el generador — decisión que excede el alcance
de esta medición y queda pendiente.

## 5. Limitaciones declaradas

- **`SPECIALTY_BASELINE` permanece sin respaldo empírico externo.** El NPDB
  no expone especialidad clínica (`LICNFELD` es MD/DO, no lo suficientemente
  granular). No hay fuente externa disponible en este proyecto para
  contrastar los 20 valores de `ml-service/app/risk/baselines.py`.
- **El generador no modela tipo de incidente.** `ALGNNATR` (grupo de
  alegación: diagnóstico, anestesia, cirugía, medicación, obstetricia,
  tratamiento, monitoreo, equipo, salud conductual, otros) no tiene
  contraparte en `generate_risk_dataset.py`. No se introduce una variable
  nueva para forzar el cruce.
- **`time_since_incident_days` — desajuste de dominio**, no solo de unidad
  (ver §4).
- **Los tres proxies miden severidad de lesión (`OUTCOME`) o historial de
  pagos, no `risk_score`.** La comparación es de dirección y magnitud
  relativa del efecto, no de valores numéricos intercambiables.
- **Sesgo de selección de pagos.** El NPDB solo contiene casos que
  terminaron en pago (reporte tipo "P"); no incluye demandas desestimadas
  ni reclamos sin pago. Cualquier lectura de estos números hereda ese sesgo
  y no se extrapola a la población general de casos médico-legales.

## 6. Qué no se hizo

No se modificó `generate_risk_dataset.py`. No se reentrenó el modelo de
riesgo. No se importaron montos de pago (`PAYMENT`/`TOTALPMT`) en ningún
paso de este análisis. Cualquier cambio al generador a partir de este
documento requiere una decisión explícita y separada.
