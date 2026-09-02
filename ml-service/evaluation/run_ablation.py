"""Fase 3 — estudio de ablación (`docs/MATCHING-SPEC.md` §5).

Evalúa las 8 variantes contra `ds04_qrels.csv` (juicios humanos de
adjudicación) y reporta Precision@3, nDCG@3, MRR@3 y MAP@3 como métricas
PRIMARIAS, con nDCG@5/Recall@5 disponibles solo como cotas inferiores
(§5.2) -- nunca junto a las primarias sin esa aclaración. Intervalos de
confianza al 95% por bootstrap (10 000 remuestreos sobre consultas, §5.3),
y barrido de α con validación cruzada dejando una consulta fuera para
`composite-sweep` (§5.4). Los juicios de valor 1 se reportan en ambos
sentidos -- como relevante y como no relevante -- para las métricas
binarias (§5.3).

Las 28 pruebas de permutación pareada (C(8,2)) están divididas en
confirmatorias (7: `composite-070` contra cada otra variante, α=0.05 sin
ajustar) y exploratorias (21: el resto, corrección de Bonferroni,
α≈0.00238) -- preregistrado el 2026-09-02, antes de que existan juicios
reales, para no exponerse a falsos positivos por comparaciones múltiples
sin corregir. Ver el bloque de PREREGISTRO junto a `CONFIRMATORY_PAIRS` y
`docs/datasheet-fase3-ablacion.md`.

⚠ NO SE HA EJECUTADO CONTRA `ds04_qrels.csv` REAL: ese archivo no existe
todavía (depende de que el adjudicador complete el instrumento definitivo,
bloqueado por MATCHING-SPEC.md §4.4.1). Se verificó únicamente contra
qrels SINTÉTICOS de prueba (`--self-test`, generados en memoria, nunca
escritos a disco) para confirmar que el script corre de punta a punta y
produce números con el formato esperado. Cuando lleguen los juicios reales,
correr:

    cd ml-service
    python evaluation/run_ablation.py --qrels data/reference/ds04_qrels.csv

Reproducibilidad: RANDOM_STATE = 42 en todo lo estocástico (CLAUDE.md §7).
"""

from __future__ import annotations

import argparse
import csv
import json
import math
import sys
from dataclasses import dataclass, field
from pathlib import Path
from random import Random

if sys.stdout.encoding and sys.stdout.encoding.lower() != "utf-8":
    sys.stdout.reconfigure(encoding="utf-8")

sys.path.insert(0, str(Path(__file__).resolve().parent))
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from build_test_collection import (  # noqa: E402
    CORPUS_PATH,
    QUERIES,
    RANDOM_STATE,
    _doctor_text_full,
    _lawyer_text_full,
    rank_area_match,
    rank_bm25,
    rank_composite,
    rank_performance_only,
    rank_random,
    rank_tfidf,
)

N_BOOTSTRAP = 10_000
N_PERMUTATIONS = 10_000
RANK_DEPTH = 5  # suficiente para @3 (primarias) y @5 (cotas inferiores, §5.2)
ALPHA_GRID = [round(a * 0.1, 1) for a in range(0, 11)]  # [0.0, 0.1, ..., 1.0] -- §5.4

VARIANT_NAMES = [
    "random", "area-match", "tfidf-full", "bio-only",
    "performance-only", "composite-070", "composite-sweep", "bm25",
]

# ═══════════════════════════════════════════════════════════════════════════
# PREREGISTRO -- 2026-09-02, antes de que exista `ds04_qrels.csv` real.
#
# Con 28 comparaciones pareadas (C(8,2)) a α=0.05 sin ajustar, se esperan 1-2
# falsos positivos por puro azar aunque no exista ninguna diferencia real
# entre variantes -- mismo problema de fondo que el umbral del Par C en
# datasheet-ds04.md: un resultado que parece evidencia pero depende de cómo
# se planteó el análisis, no del dato. Se divide el conjunto de comparaciones
# en confirmatorias y exploratorias AHORA, fijado antes de ver ningún p-valor
# real -- ver docs/datasheet-fase3-ablacion.md para el razonamiento completo.
#
#   Confirmatorias (7): composite-070 contra cada una de las otras 7
#   variantes. Es la pregunta que sostiene la tesis -- si el compuesto
#   supera a cada componente por separado. Se reportan a α=0.05 SIN
#   ajustar: son pocas y están directamente ligadas al argumento central.
#
#   Exploratorias (21 = C(7,2), todas las combinaciones entre las variantes
#   NO pivote): se reportan con corrección de Bonferroni. Bonferroni y no
#   Benjamini-Hochberg porque BH fija su umbral de rechazo mirando la
#   distribución de p-valores YA obtenidos -- no es un umbral fijo
#   preregistrable hoy. Bonferroni sí lo es: α_exploratoria = 0.05 / 21,
#   calculado de la CANTIDAD de comparaciones (conocida de antemano), no de
#   ningún resultado. Sirven para entender el comportamiento del sistema,
#   NO para sustentar la decisión de diseño.
# ═══════════════════════════════════════════════════════════════════════════
CONFIRMATORY_PIVOT = "composite-070"
ALPHA_CONFIRMATORY = 0.05

CONFIRMATORY_PAIRS = [(CONFIRMATORY_PIVOT, v) for v in VARIANT_NAMES if v != CONFIRMATORY_PIVOT]
EXPLORATORY_PAIRS = [
    (va, vb)
    for i, va in enumerate(VARIANT_NAMES) if va != CONFIRMATORY_PIVOT
    for vb in VARIANT_NAMES[i + 1:] if vb != CONFIRMATORY_PIVOT
]
assert len(CONFIRMATORY_PAIRS) == 7, f"Se esperaban 7 comparaciones confirmatorias, hay {len(CONFIRMATORY_PAIRS)}"
assert len(EXPLORATORY_PAIRS) == 21, f"Se esperaban 21 comparaciones exploratorias, hay {len(EXPLORATORY_PAIRS)}"
ALPHA_EXPLORATORY_BONFERRONI = ALPHA_CONFIRMATORY / len(EXPLORATORY_PAIRS)  # 0.05/21 ≈ 0.00238

DEFAULT_QRELS_PATH = Path(__file__).resolve().parents[1] / "data" / "reference" / "ds04_qrels.csv"


# ═══════════════════════════════════════════════════════════════════════════
# 1) CARGA DE QRELS
# ═══════════════════════════════════════════════════════════════════════════

@dataclass
class Qrels:
    """judgments[(query_id, lawyer_id)] = relevancia (0, 1 o 2)."""
    judgments: dict[tuple[str, str], int] = field(default_factory=dict)
    provisional: bool = False

    def get(self, query_id: str, lawyer_id: str) -> int | None:
        return self.judgments.get((query_id, lawyer_id))

    def relevant_lawyers_for(self, query_id: str, threshold: int) -> set[str]:
        return {lid for (qid, lid), rel in self.judgments.items() if qid == query_id and rel >= threshold}


def load_qrels(path: Path) -> Qrels:
    """Formato esperado: CSV con columnas query_id,lawyer_id,relevance
    (relevance ∈ {0,1,2}) y opcionalmente una columna `provisional`
    (true/false). Si CUALQUIER fila trae provisional=true, o si la columna
    no existe (formato aún no definido por un adjudicador real), el objeto
    se marca provisional -- ver MATCHING-SPEC.md §4.6: 'No reportes
    métricas basadas en juicios provisionales como si fueran validadas.'
    """
    rows = list(csv.DictReader(path.open(encoding="utf-8")))
    judgments: dict[tuple[str, str], int] = {}
    provisional = False
    for row in rows:
        qid, lid = row["query_id"], row["lawyer_id"]
        judgments[(qid, lid)] = int(row["relevance"])
        if str(row.get("provisional", "")).strip().lower() in ("true", "1", "yes", "sí", "si"):
            provisional = True
    return Qrels(judgments=judgments, provisional=provisional)


def qrels_from_rows(rows: list[dict]) -> Qrels:
    """Construye Qrels directamente desde una lista de dicts en memoria --
    usado por --self-test para no tener que escribir un CSV a disco."""
    judgments = {(r["query_id"], r["lawyer_id"]): int(r["relevance"]) for r in rows}
    return Qrels(judgments=judgments, provisional=True)


# ═══════════════════════════════════════════════════════════════════════════
# 2) LAS 8 VARIANTES -- ranking completo (no solo el top-N del pool)
# ═══════════════════════════════════════════════════════════════════════════

def rank_variant(
    variant: str, query: dict, lawyers: list[dict], query_idx: int,
    depth: int = RANK_DEPTH, alpha: float = 0.70,
) -> list[str]:
    query_text_full = _doctor_text_full(query)
    lawyer_texts_full = [_lawyer_text_full(l) for l in lawyers]
    lawyer_ids = [l["lawyer_id"] for l in lawyers]
    bio_texts = [l["biography"] for l in lawyers]

    if variant == "random":
        return rank_random(query, lawyers, query_idx, depth=depth)
    if variant == "area-match":
        return rank_area_match(query, lawyers, depth=depth)
    if variant == "tfidf-full":
        return rank_tfidf(query_text_full, lawyer_texts_full, lawyer_ids, depth=depth)
    if variant == "bio-only":
        return rank_tfidf(query["case_description"], bio_texts, lawyer_ids, depth=depth)
    if variant == "performance-only":
        return rank_performance_only(lawyers, depth=depth)
    if variant == "composite-070":
        return rank_composite(query_text_full, lawyers, alpha=0.70, depth=depth)
    if variant == "composite-sweep":
        return rank_composite(query_text_full, lawyers, alpha=alpha, depth=depth)
    if variant == "bm25":
        return rank_bm25(query_text_full, lawyers, depth=depth)
    raise ValueError(f"Variante desconocida: {variant}")


# ═══════════════════════════════════════════════════════════════════════════
# 3) MÉTRICAS -- todas sobre una lista rankeada + los qrels de esa consulta
#    Convención estándar de pooling (TREC): un candidato sin juicio se trata
#    como no relevante (relevance=0), NO como dato faltante.
# ═══════════════════════════════════════════════════════════════════════════

def _rel(qrels: Qrels, query_id: str, lawyer_id: str) -> int:
    return qrels.get(query_id, lawyer_id) or 0


def precision_at_k(ranked: list[str], qrels: Qrels, query_id: str, k: int, threshold: int) -> float:
    top_k = ranked[:k]
    if not top_k:
        return 0.0
    hits = sum(1 for lid in top_k if _rel(qrels, query_id, lid) >= threshold)
    return hits / len(top_k)


def recall_at_k(ranked: list[str], qrels: Qrels, query_id: str, k: int, threshold: int) -> float:
    total_relevant = len(qrels.relevant_lawyers_for(query_id, threshold))
    if total_relevant == 0:
        return 0.0
    top_k = ranked[:k]
    hits = sum(1 for lid in top_k if _rel(qrels, query_id, lid) >= threshold)
    return hits / total_relevant


def mrr_at_k(ranked: list[str], qrels: Qrels, query_id: str, k: int, threshold: int) -> float:
    for i, lid in enumerate(ranked[:k], start=1):
        if _rel(qrels, query_id, lid) >= threshold:
            return 1.0 / i
    return 0.0


def average_precision_at_k(ranked: list[str], qrels: Qrels, query_id: str, k: int, threshold: int) -> float:
    top_k = ranked[:k]
    total_relevant = len(qrels.relevant_lawyers_for(query_id, threshold))
    if total_relevant == 0 or not top_k:
        return 0.0
    hits = 0
    precisions = []
    for i, lid in enumerate(top_k, start=1):
        if _rel(qrels, query_id, lid) >= threshold:
            hits += 1
            precisions.append(hits / i)
    if not precisions:
        return 0.0
    return sum(precisions) / min(total_relevant, k)


def dcg_at_k(ranked: list[str], qrels: Qrels, query_id: str, k: int) -> float:
    dcg = 0.0
    for i, lid in enumerate(ranked[:k], start=1):
        rel = _rel(qrels, query_id, lid)  # graduado 0/1/2, sin umbral -- nDCG no lo necesita
        if rel > 0:
            dcg += (2**rel - 1) / math.log2(i + 1)
    return dcg


def ndcg_at_k(ranked: list[str], qrels: Qrels, query_id: str, k: int) -> float:
    dcg = dcg_at_k(ranked, qrels, query_id, k)
    ideal_rels = sorted(
        (rel for (qid, _lid), rel in qrels.judgments.items() if qid == query_id),
        reverse=True,
    )[:k]
    idcg = sum((2**rel - 1) / math.log2(i + 1) for i, rel in enumerate(ideal_rels, start=1))
    if idcg == 0:
        return 0.0
    return dcg / idcg


# ═══════════════════════════════════════════════════════════════════════════
# 4) BOOTSTRAP Y PERMUTACIÓN (§5.3)
# ═══════════════════════════════════════════════════════════════════════════

def bootstrap_ci(values: list[float], rng: Random, n_resamples: int = N_BOOTSTRAP) -> tuple[float, float, float]:
    """IC 95% por bootstrap sobre CONSULTAS (no sobre pares consulta-abogado)."""
    n = len(values)
    if n == 0:
        return 0.0, 0.0, 0.0
    means = []
    for _ in range(n_resamples):
        sample = [values[rng.randrange(n)] for _ in range(n)]
        means.append(sum(sample) / n)
    means.sort()
    lo = means[int(0.025 * n_resamples)]
    hi = means[min(int(0.975 * n_resamples), n_resamples - 1)]
    return sum(values) / n, lo, hi


def paired_permutation_test(
    values_a: list[float], values_b: list[float], rng: Random, n_permutations: int = N_PERMUTATIONS,
) -> tuple[float, float]:
    """Prueba de permutación pareada de dos colas sobre la diferencia de
    medias (a - b), consulta por consulta. Devuelve (diferencia observada, p-valor)."""
    assert len(values_a) == len(values_b)
    diffs = [a - b for a, b in zip(values_a, values_b)]
    observed = sum(diffs) / len(diffs)
    count_ge = 0
    for _ in range(n_permutations):
        signed = [d if rng.random() < 0.5 else -d for d in diffs]
        permuted_mean = sum(signed) / len(signed)
        if abs(permuted_mean) >= abs(observed):
            count_ge += 1
    p_value = count_ge / n_permutations
    return observed, p_value


# ═══════════════════════════════════════════════════════════════════════════
# 5) BARRIDO DE α CON VALIDACIÓN CRUZADA DEJANDO UNA CONSULTA FUERA (§5.4)
# ═══════════════════════════════════════════════════════════════════════════

def composite_sweep_loo(
    queries: list[dict], lawyers: list[dict], qrels: Qrels, threshold: int,
) -> dict:
    """Para cada consulta: elige α con las OTRAS 19 (maximiza P@3 media),
    evalúa ese α en la consulta dejada fuera. Nunca elige α mirando la
    consulta sobre la que se reporta -- eso sería sobreajuste (§5.4)."""
    # P@3 de cada (query_idx, alpha) -- se computa una sola vez, se reutiliza
    p3_by_query_alpha: dict[tuple[int, float], float] = {}
    ranked_by_query_alpha: dict[tuple[int, float], list[str]] = {}
    for qi, q in enumerate(queries):
        for alpha in ALPHA_GRID:
            ranked = rank_variant("composite-sweep", q, lawyers, qi, alpha=alpha)
            ranked_by_query_alpha[(qi, alpha)] = ranked
            p3_by_query_alpha[(qi, alpha)] = precision_at_k(ranked, qrels, q["query_id"], 3, threshold)

    chosen_alpha_per_fold = []
    out_of_sample_p3 = []
    out_of_sample_rankings = []
    for held_out_idx in range(len(queries)):
        best_alpha, best_mean = None, -1.0
        for alpha in ALPHA_GRID:
            others = [qi for qi in range(len(queries)) if qi != held_out_idx]
            mean_p3 = sum(p3_by_query_alpha[(qi, alpha)] for qi in others) / len(others)
            if mean_p3 > best_mean:
                best_mean, best_alpha = mean_p3, alpha
        chosen_alpha_per_fold.append(best_alpha)
        out_of_sample_p3.append(p3_by_query_alpha[(held_out_idx, best_alpha)])
        out_of_sample_rankings.append(ranked_by_query_alpha[(held_out_idx, best_alpha)])

    alpha_variance = max(chosen_alpha_per_fold) - min(chosen_alpha_per_fold)
    return {
        "chosen_alpha_per_fold": chosen_alpha_per_fold,
        "alpha_range": alpha_variance,
        "out_of_sample_p3": out_of_sample_p3,
        "out_of_sample_rankings": out_of_sample_rankings,  # para nDCG@3/MRR@3/MAP@3 de esta variante
        "warning": (
            "El α óptimo varía mucho entre pliegues -- los datos no soportan una elección fina "
            "de peso. Se mantiene 0.70 como decisión de diseño razonable (§5.4)."
            if alpha_variance >= 0.4 else None
        ),
    }


# ═══════════════════════════════════════════════════════════════════════════
# 6) EVALUACIÓN COMPLETA
# ═══════════════════════════════════════════════════════════════════════════

def evaluate_variant(
    variant: str, queries: list[dict], lawyers: list[dict], qrels: Qrels,
    loo_rankings: list[str] | None = None,
) -> dict:
    """Métricas por consulta para una variante, en ambos sentidos del
    juicio de valor 1 (relevante / no relevante) para las binarias (§5.3).
    nDCG usa la escala graduada 0/1/2 directamente -- no aplica el umbral."""
    per_query_rankings = []
    for qi, q in enumerate(queries):
        if variant == "composite-sweep" and loo_rankings is not None:
            per_query_rankings.append(loo_rankings[qi])
        else:
            per_query_rankings.append(rank_variant(variant, q, lawyers, qi))

    result: dict = {"ndcg3": [], "ndcg5_lower_bound": []}
    for threshold, label in ((2, "strict"), (1, "lenient")):
        result[f"p3_{label}"] = []
        result[f"mrr3_{label}"] = []
        result[f"map3_{label}"] = []
        result[f"recall5_lower_bound_{label}"] = []

    for ranked, q in zip(per_query_rankings, queries):
        qid = q["query_id"]
        result["ndcg3"].append(ndcg_at_k(ranked, qrels, qid, 3))
        result["ndcg5_lower_bound"].append(ndcg_at_k(ranked, qrels, qid, 5))
        for threshold, label in ((2, "strict"), (1, "lenient")):
            result[f"p3_{label}"].append(precision_at_k(ranked, qrels, qid, 3, threshold))
            result[f"mrr3_{label}"].append(mrr_at_k(ranked, qrels, qid, 3, threshold))
            result[f"map3_{label}"].append(average_precision_at_k(ranked, qrels, qid, 3, threshold))
            result[f"recall5_lower_bound_{label}"].append(recall_at_k(ranked, qrels, qid, 5, threshold))

    result["_rankings"] = per_query_rankings
    return result


def summarize(values: list[float], rng: Random) -> dict:
    mean, lo, hi = bootstrap_ci(values, rng)
    return {"mean": round(mean, 4), "ci95_lo": round(lo, 4), "ci95_hi": round(hi, 4)}


def run(queries: list[dict], lawyers: list[dict], qrels: Qrels) -> dict:
    rng = Random(RANDOM_STATE)

    loo = composite_sweep_loo(queries, lawyers, qrels, threshold=2)

    per_variant: dict[str, dict] = {}
    for variant in VARIANT_NAMES:
        loo_rankings = loo["out_of_sample_rankings"] if variant == "composite-sweep" else None
        per_variant[variant] = evaluate_variant(variant, queries, lawyers, qrels, loo_rankings)

    report: dict = {
        "provisional": qrels.provisional,
        "n_queries": len(queries),
        "random_state": RANDOM_STATE,
        "n_bootstrap": N_BOOTSTRAP,
        "n_permutations": N_PERMUTATIONS,
        "composite_sweep_loo": {
            "chosen_alpha_per_fold": loo["chosen_alpha_per_fold"],
            "alpha_range_across_folds": loo["alpha_range"],
            "warning": loo["warning"],
        },
        "variants": {},
    }

    for variant, metrics in per_variant.items():
        report["variants"][variant] = {
            "primary": {
                "p3_strict": summarize(metrics["p3_strict"], rng),
                "p3_lenient": summarize(metrics["p3_lenient"], rng),
                "ndcg3": summarize(metrics["ndcg3"], rng),
                "mrr3_strict": summarize(metrics["mrr3_strict"], rng),
                "mrr3_lenient": summarize(metrics["mrr3_lenient"], rng),
                "map3_strict": summarize(metrics["map3_strict"], rng),
                "map3_lenient": summarize(metrics["map3_lenient"], rng),
            },
            "lower_bounds_depth5_do_not_report_as_primary": {
                "ndcg5": summarize(metrics["ndcg5_lower_bound"], rng),
                "recall5_strict": summarize(metrics["recall5_lower_bound_strict"], rng),
                "recall5_lenient": summarize(metrics["recall5_lower_bound_lenient"], rng),
            },
        }

    # ── Pruebas de permutación pareada sobre P@3 estricto (métrica principal,
    #    indicador OE4-I2), divididas en confirmatorias/exploratorias -- ver
    #    el bloque de PREREGISTRO junto a CONFIRMATORY_PAIRS más arriba. ────
    def run_pairs(pairs: list[tuple[str, str]], alpha: float) -> list[dict]:
        results = []
        for va, vb in pairs:
            obs, p = paired_permutation_test(
                per_variant[va]["p3_strict"], per_variant[vb]["p3_strict"], rng,
            )
            results.append({
                "a": va, "b": vb, "mean_diff_a_minus_b": round(obs, 4), "p_value": round(p, 4),
                "alpha_used": alpha, "significant_at_alpha_used": p < alpha,
            })
        return results

    report["pairwise_permutation_p3_strict"] = {
        "confirmatory": {
            "description": (
                f"{CONFIRMATORY_PIVOT} contra cada una de las otras 7 variantes -- sostiene el "
                f"argumento central de la tesis. Reportado a α={ALPHA_CONFIRMATORY} sin ajustar "
                f"(preregistrado, docs/datasheet-fase3-ablacion.md)."
            ),
            "alpha": ALPHA_CONFIRMATORY,
            "results": run_pairs(CONFIRMATORY_PAIRS, ALPHA_CONFIRMATORY),
        },
        "exploratory": {
            "description": (
                "Todas las combinaciones restantes entre variantes -- describen el comportamiento "
                "del sistema, NO sustentan la decisión de diseño. Corrección de Bonferroni: "
                f"α = {ALPHA_CONFIRMATORY}/{len(EXPLORATORY_PAIRS)} ≈ {ALPHA_EXPLORATORY_BONFERRONI:.5f} "
                "(preregistrado, docs/datasheet-fase3-ablacion.md)."
            ),
            "alpha": round(ALPHA_EXPLORATORY_BONFERRONI, 6),
            "results": run_pairs(EXPLORATORY_PAIRS, ALPHA_EXPLORATORY_BONFERRONI),
        },
    }

    return report


# ═══════════════════════════════════════════════════════════════════════════
# 7) AUTO-TEST CON QRELS SINTÉTICOS (no toca datos de adjudicación reales)
# ═══════════════════════════════════════════════════════════════════════════

def build_synthetic_qrels(queries: list[dict], lawyers: list[dict]) -> Qrels:
    """Qrels de juguete, deterministas (RANDOM_STATE), SOLO para verificar
    que el pipeline de métricas corre de punta a punta. No representan
    juicio humano real -- por eso Qrels.provisional queda en True."""
    rng = Random(RANDOM_STATE)
    rows = []
    for q in queries:
        # simula relevancia correlacionada con coincidencia de área, más ruido
        for l in lawyers:
            area_match = any(a == q["medical_specialty"] for a in l["medical_areas"])
            base = 2 if area_match else 0
            noise = rng.choice([-1, 0, 0, 0, 1])
            rel = max(0, min(2, base + noise)) if rng.random() < 0.6 else 0
            if rel > 0 or rng.random() < 0.05:  # no se juzgan todos los pares -- como el pooling real
                rows.append({"query_id": q["query_id"], "lawyer_id": l["lawyer_id"], "relevance": rel})
    return qrels_from_rows(rows)


def self_test() -> None:
    lawyers = json.loads(CORPUS_PATH.read_text(encoding="utf-8"))
    queries = QUERIES[:6]  # subconjunto -- alcanza para verificar el pipeline, corre rápido
    qrels = build_synthetic_qrels(queries, lawyers)
    assert qrels.provisional is True

    report = run(queries, lawyers, qrels)

    assert report["provisional"] is True
    assert set(report["variants"].keys()) == set(VARIANT_NAMES)
    for variant, data in report["variants"].items():
        for metric_name, summary in data["primary"].items():
            assert 0.0 <= summary["mean"] <= 1.0 or metric_name.startswith("map"), (
                f"{variant}/{metric_name} fuera de rango: {summary}"
            )
            assert summary["ci95_lo"] <= summary["mean"] <= summary["ci95_hi"] + 1e-9, (
                f"{variant}/{metric_name}: la media no cae dentro de su propio IC: {summary}"
            )
    confirmatory = report["pairwise_permutation_p3_strict"]["confirmatory"]
    exploratory = report["pairwise_permutation_p3_strict"]["exploratory"]
    assert len(confirmatory["results"]) == 7, f"Esperadas 7 confirmatorias, hay {len(confirmatory['results'])}"
    assert len(exploratory["results"]) == 21, f"Esperadas 21 exploratorias, hay {len(exploratory['results'])}"
    assert confirmatory["alpha"] == ALPHA_CONFIRMATORY
    assert abs(exploratory["alpha"] - ALPHA_EXPLORATORY_BONFERRONI) < 1e-5  # el reporte redondea a 6 decimales
    assert exploratory["alpha"] < confirmatory["alpha"], "Bonferroni debe ser más estricto que el umbral confirmatorio"
    for group in (confirmatory["results"], exploratory["results"]):
        for pair in group:
            assert 0.0 <= pair["p_value"] <= 1.0
    confirmatory_pivots = {p["a"] for p in confirmatory["results"]}
    assert confirmatory_pivots == {CONFIRMATORY_PIVOT}, "Toda comparación confirmatoria debe partir del pivote"
    exploratory_variants = {v for p in exploratory["results"] for v in (p["a"], p["b"])}
    assert CONFIRMATORY_PIVOT not in exploratory_variants, "El pivote no debe aparecer en exploratorias"

    print("=== AUTO-TEST (qrels sintéticos, NO son datos de adjudicación reales) ===")
    print(f"Consultas: {len(queries)}  |  Variantes: {len(VARIANT_NAMES)}  |  "
          f"Pares juzgados (sintético): {len(qrels.judgments)}")
    print(f"provisional={report['provisional']} (correcto: los qrels sintéticos NUNCA son 'no provisional')")
    print("\nP@3 estricto (relevance>=2) por variante:")
    for variant in VARIANT_NAMES:
        s = report["variants"][variant]["primary"]["p3_strict"]
        print(f"  {variant:18s} mean={s['mean']:.3f}  IC95=[{s['ci95_lo']:.3f}, {s['ci95_hi']:.3f}]")
    print(f"\nConfirmatorias ({CONFIRMATORY_PIVOT} vs. las otras 7, α={confirmatory['alpha']}, sin ajustar):")
    for pair in confirmatory["results"]:
        flag = " *significativo*" if pair["significant_at_alpha_used"] else ""
        print(f"  {pair['a']:18s} vs {pair['b']:18s} diff={pair['mean_diff_a_minus_b']:+.3f} "
              f"p={pair['p_value']:.4f}{flag}")
    print(f"\nExploratorias (21 combinaciones restantes, Bonferroni α≈{exploratory['alpha']:.5f}): "
          f"{sum(1 for p in exploratory['results'] if p['significant_at_alpha_used'])} de "
          f"{len(exploratory['results'])} significativas tras la corrección.")
    print(f"\nBarrido α (composite-sweep, LOO): rango entre pliegues = "
          f"{report['composite_sweep_loo']['alpha_range_across_folds']:.2f}")
    if report["composite_sweep_loo"]["warning"]:
        print(f"  {report['composite_sweep_loo']['warning']}")
    print("\nOK -- el pipeline corre de punta a punta contra qrels sintéticos.")
    print("NO EJECUTAR sin --self-test contra ds04_qrels.csv hasta que ese archivo "
          "exista con juicios humanos reales (no provisionales).")


# ═══════════════════════════════════════════════════════════════════════════
# 8) MAIN
# ═══════════════════════════════════════════════════════════════════════════

def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--qrels", type=Path, default=DEFAULT_QRELS_PATH)
    parser.add_argument("--out", type=Path,
                         default=Path(__file__).resolve().parents[1] / "artifacts" / "matching" / "metrics.json")
    parser.add_argument("--self-test", action="store_true",
                         help="Corre contra qrels sintéticos generados en memoria, no toca ds04_qrels.csv")
    args = parser.parse_args()

    if args.self_test:
        self_test()
        return

    if not args.qrels.exists():
        raise SystemExit(
            f"No existe {args.qrels}. Ese archivo se genera tras la adjudicación humana "
            f"(MATCHING-SPEC.md §4.6), que todavía no ocurrió -- ver §4.4.1 (bloqueado por "
            f"disponibilidad del adjudicador). Usa --self-test para verificar el pipeline "
            f"mientras tanto."
        )

    lawyers = json.loads(CORPUS_PATH.read_text(encoding="utf-8"))
    qrels = load_qrels(args.qrels)
    if qrels.provisional:
        print(
            "⚠ ADVERTENCIA: los qrels cargados están marcados como provisionales. "
            "MATCHING-SPEC.md §4.6: 'No reportes métricas basadas en juicios provisionales "
            "como si fueran validadas.' Este reporte se genera igual (para inspección), pero "
            "no debe citarse como resultado de Fase 3.",
            file=sys.stderr,
        )

    report = run(QUERIES, lawyers, qrels)
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(json.dumps(report, indent=2, ensure_ascii=False), encoding="utf-8")
    print(f"Escrito: {args.out.resolve()}")
    if report["provisional"]:
        print("⚠ metrics.json generado a partir de qrels PROVISIONALES -- no reportar como Fase 3 final.")


if __name__ == "__main__":
    main()
