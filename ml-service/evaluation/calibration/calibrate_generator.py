"""
Fase 5 (alcance replanteado) — contrasta tres de los efectos que
generate_risk_dataset.py asigna hoy contra el NPDB Public Use Data File.

No calibra por especialidad (SPECIALTY_BASELINE): el NPDB no expone
especialidad clinica, solo LICNFELD (MD/DO). No calibra por tipo de
incidente (ALGNNATR): el generador no modela esa variable. Ambas quedan
como limitacion declarada en docs/calibracion-generador.md, sin sustituto.

Tres variables del generador SI tienen correlato directo, sin depender de
especialidad:

  generador                  -> NPDB
  informed_consent (bool)    -> ALEGATN1/ALEGATN2 == 707
                                 ("Failure to Obtain Consent or Lack of
                                 Informed Consent", codigo verificado
                                 contra el codebook oficial del NPDB)
  has_prior_complaints (bool)-> NPMALRPT > 1 (mas de un reporte de pago
                                 de mala praxis para el mismo profesional
                                 en el archivo completo)
  time_since_incident_days   -> ORIGYEAR - MALYEAR1, en anios (el NPDB
                                 no tiene resolucion diaria; ver nota de
                                 resolucion en el datasheet de salida)

Este script SOLO MIDE. No modifica generate_risk_dataset.py.

Uso:
    python calibrate_generator.py --input <ruta al NPDB2601.CSV> \
        --out ../../../docs/calibracion-generador.md
"""

import argparse
import hashlib
import json
import sys
from datetime import date
from pathlib import Path

import numpy as np
import pandas as pd

RANDOM_STATE = 42
N_BOOTSTRAP = 10_000

# Filtro verificado contra el spec: RECTYPE=='P' (pago de mala praxis,
# formato vigente desde 1/31/2004) + LICNFELD en (10, 20) (MD, DO) +
# OUTCOME != '10' ("Cannot Be Determined from Available Records").
USECOLS = ["RECTYPE", "LICNFELD", "OUTCOME", "ALEGATN1", "ALEGATN2",
           "NPMALRPT", "ORIGYEAR", "MALYEAR1"]
EXPECTED_N_FILTERED = 210_304

ALEGATN_INFORMED_CONSENT = "707"  # "Failure to Obtain Consent or Lack of Informed Consent"

# Rango plausible para anios transcurridos: el codebook advierte que
# MALYEAR1 puede contener anios erroneos (p.ej. 3999) tal como fueron
# reportados, sin corregir. Se descartan lag negativos (imposibles) y
# lags mayores a 20 anios (fuera del rango operativo del NPDB, que
# arranca en 1990).
MIN_LAG_YEARS = 0
MAX_LAG_YEARS = 20


def sha256_of(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def bootstrap_mean_diff_ci(a: np.ndarray, b: np.ndarray, rng: np.random.Generator,
                            n_resamples: int = N_BOOTSTRAP) -> tuple[float, float, float]:
    """IC 95% por bootstrap para la diferencia de medias (b - a)."""
    diffs = np.empty(n_resamples)
    for i in range(n_resamples):
        sa = rng.choice(a, size=a.size, replace=True)
        sb = rng.choice(b, size=b.size, replace=True)
        diffs[i] = sb.mean() - sa.mean()
    point = b.mean() - a.mean()
    lo, hi = np.percentile(diffs, [2.5, 97.5])
    return point, lo, hi


def load_filtered(csv_path: Path) -> pd.DataFrame:
    df = pd.read_csv(csv_path, usecols=USECOLS, dtype=str, low_memory=False)
    filt = df[
        (df["RECTYPE"] == "P")
        & (df["LICNFELD"].isin(["10", "20"]))
        & (df["OUTCOME"] != "10")
    ].copy()
    return filt


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__,
                                      formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--input", required=True, type=Path,
                         help="Ruta local al NPDB2601.CSV (nunca se copia al repo)")
    parser.add_argument("--out", type=Path,
                         default=Path(__file__).resolve().parents[3] / "docs" / "calibracion-generador.md",
                         help="Ruta de salida del datasheet en Markdown")
    args = parser.parse_args()

    if not args.input.exists():
        sys.exit(f"No existe: {args.input}")

    print(f"Hasheando {args.input} (SHA-256)...")
    file_hash = sha256_of(args.input)
    print(f"  {file_hash}")

    print("Cargando y filtrando...")
    df = load_filtered(args.input)
    n = len(df)
    print(f"  n filtrado: {n}")
    if n != EXPECTED_N_FILTERED:
        print(f"  AVISO: n filtrado ({n}) difiere del esperado ({EXPECTED_N_FILTERED}). "
              f"Puede deberse a una edicion distinta del Public Use File.")

    df["outcome_int"] = df["OUTCOME"].astype(int)
    rng = np.random.default_rng(RANDOM_STATE)

    results: dict = {
        "generated_at": date.today().isoformat(),
        "random_state": RANDOM_STATE,
        "n_bootstrap": N_BOOTSTRAP,
        "input_file": args.input.name,
        "input_sha256": file_hash,
        "n_filtered": n,
        "n_filtered_expected": EXPECTED_N_FILTERED,
        "library_versions": {"numpy": np.__version__, "pandas": pd.__version__},
    }

    # ── Variable 1: informed_consent -> ALEGATN1/ALEGATN2 == 707 ────────
    has_707 = (df["ALEGATN1"] == ALEGATN_INFORMED_CONSENT) | (df["ALEGATN2"] == ALEGATN_INFORMED_CONSENT)
    n_707 = int(has_707.sum())
    a = df.loc[~has_707, "outcome_int"].to_numpy()
    b = df.loc[has_707, "outcome_int"].to_numpy()
    point, lo, hi = bootstrap_mean_diff_ci(a, b, rng)
    results["informed_consent"] = {
        "n_with_707": n_707,
        "n_without_707": int((~has_707).sum()),
        "mean_outcome_without_707": float(a.mean()),
        "mean_outcome_with_707": float(b.mean()),
        "mean_diff_707_minus_no707": point,
        "ci95_lo": float(lo),
        "ci95_hi": float(hi),
        "generator_effect": {
            "informed_consent_false_main": 0.20,
            "informed_consent_false_x_alta_complexity": 0.12,
            "note": "Suma directa sobre risk_score en [0,1]; no es una escala OUTCOME 1-9.",
        },
    }

    # ── Variable 2: has_prior_complaints -> NPMALRPT > 1 ─────────────────
    npmalrpt = pd.to_numeric(df["NPMALRPT"], errors="coerce")
    valid_np = npmalrpt.notna()
    prior = (npmalrpt > 1) & valid_np
    a2 = df.loc[valid_np & ~prior, "outcome_int"].to_numpy()
    b2 = df.loc[valid_np & prior, "outcome_int"].to_numpy()
    point2, lo2, hi2 = bootstrap_mean_diff_ci(a2, b2, rng)
    results["has_prior_complaints"] = {
        "n_valid_npmalrpt": int(valid_np.sum()),
        "n_prior_gt1": int(prior.sum()),
        "n_first_report": int((valid_np & ~prior).sum()),
        "mean_outcome_first_report": float(a2.mean()),
        "mean_outcome_prior_gt1": float(b2.mean()),
        "mean_diff_prior_minus_first": point2,
        "ci95_lo": float(lo2),
        "ci95_hi": float(hi2),
        "generator_effect": {
            "has_prior_complaints_true_main": 0.15,
            "has_prior_complaints_true_x_documentation_incomplete": 0.10,
        },
        "caveat": "NPMALRPT cuenta reportes en el archivo publico COMPLETO, "
                  "no en el subconjunto filtrado aqui; el filtro aplicado es "
                  "a nivel de fila (RECTYPE/LICNFELD/OUTCOME), no de sujeto, "
                  "asi que el conteo de NPMALRPT sigue siendo valido.",
    }

    # ── Variable 3: time_since_incident_days -> ORIGYEAR - MALYEAR1 ─────
    origyear = pd.to_numeric(df["ORIGYEAR"], errors="coerce")
    malyear1 = pd.to_numeric(df["MALYEAR1"], errors="coerce")
    lag_years = origyear - malyear1
    valid_lag = lag_years.notna() & (lag_years >= MIN_LAG_YEARS) & (lag_years <= MAX_LAG_YEARS)
    n_dropped = int((~valid_lag).sum())
    lag_valid = lag_years[valid_lag]
    outcome_valid = df.loc[valid_lag, "outcome_int"]

    # Correlacion simple (Pearson) entre anios transcurridos y severidad.
    corr = float(np.corrcoef(lag_valid, outcome_valid)[0, 1])

    # Medias de severidad por bucket de anios, para inspeccionar monotonicidad.
    buckets = {}
    for y in range(0, 6):
        mask = lag_valid == y
        if mask.sum() > 0:
            buckets[str(y)] = {
                "n": int(mask.sum()),
                "mean_outcome": float(outcome_valid[mask].mean()),
            }
    mask_6plus = lag_valid >= 6
    if mask_6plus.sum() > 0:
        buckets["6+"] = {
            "n": int(mask_6plus.sum()),
            "mean_outcome": float(outcome_valid[mask_6plus].mean()),
        }

    results["time_since_incident"] = {
        "n_valid_lag": int(valid_lag.sum()),
        "n_dropped_out_of_range_or_missing": n_dropped,
        "lag_range_kept_years": [MIN_LAG_YEARS, MAX_LAG_YEARS],
        "pearson_corr_lag_years_vs_outcome": corr,
        "outcome_mean_by_lag_year_bucket": buckets,
        "generator_effect": {
            "formula": "score += log1p(days) / log1p(365) * 0.08",
            "days_domain_in_generator": [0, 365],
            "note": "El generador NUNCA representa lags mayores a un anio: "
                    "time_since_incident_days se muestrea uniforme en [0, 365]. "
                    "El NPDB opera en anios y con lags que superan holgadamente "
                    "un anio. Ver nota de resolucion en el datasheet de salida.",
        },
    }

    args.out.parent.mkdir(parents=True, exist_ok=True)
    write_datasheet(args.out, results)
    metrics_path = args.out.parent / "calibracion-generador.metrics.json"
    metrics_path.write_text(json.dumps(results, indent=2, ensure_ascii=False), encoding="utf-8")
    print(f"Escrito: {args.out}")
    print(f"Escrito: {metrics_path}")


def write_datasheet(out_path: Path, r: dict) -> None:
    ic = r["informed_consent"]
    pc = r["has_prior_complaints"]
    ts = r["time_since_incident"]

    def fmt(x: float) -> str:
        return f"{x:.4f}"

    buckets_rows = "\n".join(
        f"| {y} | {b['n']:,} | {fmt(b['mean_outcome'])} |"
        for y, b in ts["outcome_mean_by_lag_year_bucket"].items()
    )

    md = f"""# Calibración del generador — NPDB (Fase 5, alcance replanteado)

Contrasta tres efectos de [`generate_risk_dataset.py`](../ml-service/training/generate_risk_dataset.py)
contra el NPDB Public Use Data File. Generado automáticamente por
[`calibrate_generator.py`](../ml-service/evaluation/calibration/calibrate_generator.py)
el {r['generated_at']}. Números medidos, no fabricados — revisa
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
  + `LICNFELD ∈ {{10, 20}}` (médico MD/DO) + `OUTCOME != '10'`
  ("Cannot Be Determined from Available Records").
- `n` filtrado: **{r['n_filtered']:,}** (esperado: {r['n_filtered_expected']:,}).
- Archivo de origen: `{r['input_file']}` — **nunca se copia al repositorio**
  (Data Use Agreement de HRSA, `CLAUDE.md` §5.2).
- SHA-256: `{r['input_sha256']}`
- Semilla / reproducibilidad: `RANDOM_STATE = {r['random_state']}`,
  bootstrap con {r['n_bootstrap']:,} remuestreos.
- Librerías: numpy {r['library_versions']['numpy']}, pandas {r['library_versions']['pandas']}.

Cita obligatoria (`CLAUDE.md` §5.2):

> National Practitioner Data Bank Public Use Data File, 31 de marzo de 2026,
> U.S. Department of Health and Human Services, Health Resources and
> Services Administration, Bureau of Health Workforce, Division of
> Practitioner Data Bank.

## 2. `informed_consent` — código de alegato 707

Código verificado contra el codebook oficial del NPDB (`PublicUseDataFile-Format.pdf`,
sección `ALEGATN1`): `707 = "Failure to Obtain Consent or Lack of Informed Consent"`.
Se marca el registro si `ALEGATN1` **o** `ALEGATN2` es igual a 707.

- Con código 707: **{ic['n_with_707']:,}** registros (media `OUTCOME` = {fmt(ic['mean_outcome_with_707'])}).
- Sin código 707: **{ic['n_without_707']:,}** registros (media `OUTCOME` = {fmt(ic['mean_outcome_without_707'])}).
- Diferencia de medias (707 − sin 707): **{fmt(ic['mean_diff_707_minus_no707'])}**,
  IC 95 % bootstrap [{fmt(ic['ci95_lo'])}, {fmt(ic['ci95_hi'])}].

`OUTCOME` es una escala ordinal 1–9 (1 = lesión emocional únicamente, 9 = muerte),
no comparable en unidades directas con `risk_score` en [0,1]. La comparación es
de **dirección y magnitud relativa del efecto**, no de valores absolutos.

**Efecto actual del generador:** `informed_consent = False` suma 0.20 a
`risk_score`, más 0.12 adicional si además `procedure_complexity == "alta"`
([`generate_risk_dataset.py:93,104-105`](../ml-service/training/generate_risk_dataset.py#L93)).

**Lectura:** {'la diferencia observada es positiva y el intervalo no cruza cero, consistente en dirección con el efecto que el generador ya asigna' if ic['ci95_lo'] > 0 else ('la diferencia observada es negativa y el intervalo no cruza cero, en dirección opuesta a la que el generador asigna' if ic['ci95_hi'] < 0 else 'el intervalo de confianza cruza cero — no hay evidencia suficiente en este proxy para afirmar dirección del efecto')}.

**Recomendación:** {'mantener la dirección del efecto sin cambios; la magnitud exacta no es transferible entre una escala de severidad clínica (OUTCOME) y risk_score, así que no se propone un valor numérico de reemplazo' if ic['ci95_lo'] > 0 else 'revisar antes de mantener el signo actual del efecto — pendiente de decisión del investigador'}.

## 3. `has_prior_complaints` — `NPMALRPT > 1`

`NPMALRPT` cuenta los reportes de pago de mala praxis del profesional en el
archivo público **completo**, no en el subconjunto filtrado aquí — el filtro
aplicado es a nivel de fila (`RECTYPE`/`LICNFELD`/`OUTCOME`), no de sujeto,
así que el conteo permanece correcto para cada registro individual. Se
considera "con quejas previas" cuando `NPMALRPT > 1` (este reporte más al
menos otro).

- Primer y único reporte (`NPMALRPT == 1`): **{pc['n_first_report']:,}** registros
  (media `OUTCOME` = {fmt(pc['mean_outcome_first_report'])}).
- Con reportes previos (`NPMALRPT > 1`): **{pc['n_prior_gt1']:,}** registros
  (media `OUTCOME` = {fmt(pc['mean_outcome_prior_gt1'])}).
- Diferencia de medias (previos − primero): **{fmt(pc['mean_diff_prior_minus_first'])}**,
  IC 95 % bootstrap [{fmt(pc['ci95_lo'])}, {fmt(pc['ci95_hi'])}].

**Efecto actual del generador:** `has_prior_complaints = True` suma 0.15 a
`risk_score`, más 0.10 adicional si además `documentation_complete = False`
([`generate_risk_dataset.py:96,107-108`](../ml-service/training/generate_risk_dataset.py#L96)).

**Lectura:** {'la diferencia observada es positiva y el intervalo no cruza cero, consistente en dirección con el efecto que el generador ya asigna' if pc['ci95_lo'] > 0 else ('la diferencia observada es negativa y el intervalo no cruza cero, en dirección opuesta a la que el generador asigna' if pc['ci95_hi'] < 0 else 'el intervalo de confianza cruza cero — no hay evidencia suficiente en este proxy para afirmar dirección del efecto')}.

**Recomendación:** {'mantener la dirección del efecto sin cambios' if pc['ci95_lo'] > 0 else ('revisar el signo del efecto — el proxy NPDB apunta en dirección contraria a la que asume el generador; antes de tocar el código, considerar que un profesional con más reportes previos no necesariamente produce reclamos individuales más severos (puede reflejar mayor volumen de práctica, no mayor gravedad por caso)' if pc['ci95_hi'] < 0 else 'no hay evidencia suficiente en este proxy para justificar un cambio; dejar como está')}.

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
   sin corregir. Se descartan lags fuera de [{ts['lag_range_kept_years'][0]}, {ts['lag_range_kept_years'][1]}] años
   ({ts['n_dropped_out_of_range_or_missing']:,} de {r['n_filtered']:,} registros, {ts['n_dropped_out_of_range_or_missing']/r['n_filtered']*100:.1f}%).

Con esas salvedades, sobre los **{ts['n_valid_lag']:,}** registros con lag válido:

- Correlación de Pearson entre años transcurridos y `OUTCOME`: **{fmt(ts['pearson_corr_lag_years_vs_outcome'])}**.

| Años transcurridos | n | Media `OUTCOME` |
|---|---|---|
{buckets_rows}

**Efecto actual del generador:** `score += log1p(days) / log1p(365) * 0.08`
— un efecto saturante pequeño (máximo +0.08) dentro de una ventana que
nunca excede un año.

**Advertencia de causalidad, además del desajuste de dominio:** la
correlación positiva es débil ({fmt(ts['pearson_corr_lag_years_vs_outcome'])}) y admite una
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
"""
    out_path.write_text(md, encoding="utf-8")


if __name__ == "__main__":
    main()
