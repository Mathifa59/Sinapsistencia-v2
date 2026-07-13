"""
Genera un dataset SINTÉTICO de evaluación de riesgo médico-legal para entrenar
el Random Forest (HU-29/HU-30).

⚖️  Ley 29733: NO se usan datos reales de pacientes. Cada fila es un caso
simulado. El "riesgo verdadero" (etiqueta) se obtiene de un modelo generador
basado en factores de la literatura de responsabilidad médica, con:

  1. Riesgo base por especialidad (frecuencia relativa de reclamos).
  2. Efectos aditivos de complejidad, prioridad, documentación, consentimiento,
     antecedentes de quejas y latencia desde el evento.
  3. **Interacciones no lineales** (efectos compuestos): p. ej. ausencia de
     consentimiento EN un procedimiento de alta complejidad agrava el riesgo
     más que la suma de ambos por separado. Esto es clave: un modelo lineal no
     las captura, pero el Random Forest sí — justifica la elección del modelo.
  4. Ruido gaussiano heterocedástico (mayor incertidumbre en la zona media),
     para que el modelo aproxime una función y no memorice una fórmula exacta.

El script permite **balancear las clases** por rechazo (rejection sampling)
para que las cuatro categorías queden mejor representadas (mejora recall/f1 de
las clases minoritarias como 'critico').

Uso:
    python generate_risk_dataset.py --rows 40000 --balance --seed 42
"""

import argparse
import csv
import math
import random
from collections import Counter
from pathlib import Path

# Debe coincidir EXACTO con MEDICAL_SPECIALTIES del frontend
# (frontend/src/app/shared/constants.ts): es el valor del campo "specialty".
SPECIALTIES = [
    "Medicina General", "Cirugía General", "Cardiología", "Neurología", "Oncología",
    "Pediatría", "Ginecología y Obstetricia", "Traumatología", "Oftalmología",
    "Dermatología", "Psiquiatría", "Urología", "Gastroenterología", "Endocrinología",
    "Reumatología", "Neumología", "Nefrología", "Infectología", "Hematología",
    "Anestesiología",
]

# Riesgo base por especialidad: FUENTE UNICA en app/risk/baselines.py
import sys as _sys
_sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from app.risk.baselines import SPECIALTY_BASELINE  # noqa: E402

# Frecuencia de muestreo por especialidad: las de mayor volumen asistencial
# aparecen más (distribución realista, no uniforme).
SPECIALTY_FREQ = {
    "Medicina General": 5.0, "Pediatría": 3.0, "Cirugía General": 3.0,
    "Ginecología y Obstetricia": 2.5, "Traumatología": 2.5, "Cardiología": 2.0,
    "Dermatología": 2.0, "Gastroenterología": 1.6, "Neumología": 1.5,
    "Oftalmología": 1.5, "Endocrinología": 1.4, "Urología": 1.4, "Psiquiatría": 1.4,
    "Neurología": 1.3, "Oncología": 1.2, "Reumatología": 1.1, "Nefrología": 1.1,
    "Infectología": 1.1, "Hematología": 1.0, "Anestesiología": 1.0,
}

COMPLEXITY_WEIGHT = {"baja": 0.0, "media": 0.10, "alta": 0.22}
PRIORITY_WEIGHT = {"baja": 0.0, "media": 0.06, "alta": 0.16, "critica": 0.28}

RISK_LEVELS = ["bajo", "moderado", "alto", "critico"]


def risk_level_from_score(score: float) -> str:
    if score < 0.30:
        return "bajo"
    if score < 0.55:
        return "moderado"
    if score < 0.80:
        return "alto"
    return "critico"


def _sample_specialty(rng: random.Random) -> str:
    weights = [SPECIALTY_FREQ[s] for s in SPECIALTIES]
    return rng.choices(SPECIALTIES, weights=weights, k=1)[0]


def _true_score(row: dict, rng: random.Random) -> float:
    """Modelo generador del riesgo: aditivo + interacciones no lineales + ruido."""
    specialty = row["specialty"]
    complexity = row["procedure_complexity"]
    priority = row["priority"]

    score = SPECIALTY_BASELINE[specialty]
    score += COMPLEXITY_WEIGHT[complexity]
    score += PRIORITY_WEIGHT[priority]

    if not row["documentation_complete"]:
        score += 0.15
    if not row["informed_consent"]:
        score += 0.20
    if row["has_prior_complaints"]:
        score += 0.15

    # Latencia: el riesgo crece con el tiempo transcurrido pero se satura (log).
    days = row["time_since_incident_days"]
    score += math.log1p(days) / math.log1p(365) * 0.08

    # ── Interacciones no lineales (efectos compuestos) ──────────────────────
    # Sin consentimiento en alta complejidad: agravante mayor que la suma.
    if not row["informed_consent"] and complexity == "alta":
        score += 0.12
    # Documentación incompleta + quejas previas: patrón de alto litigio.
    if not row["documentation_complete"] and row["has_prior_complaints"]:
        score += 0.10
    # Prioridad crítica en especialidad de alto baseline: se potencia.
    if priority == "critica" and SPECIALTY_BASELINE[specialty] >= 0.40:
        score += 0.08
    # Caso "blindado": documentación + consentimiento + sin quejas atenúa.
    if row["documentation_complete"] and row["informed_consent"] and not row["has_prior_complaints"]:
        score -= 0.06

    # Ruido heterocedástico: más incertidumbre en la zona media (0.3–0.7).
    sigma = 0.045 + 0.03 * math.exp(-((score - 0.5) ** 2) / 0.05)
    score += rng.gauss(0, sigma)

    return max(0.0, min(1.0, score))


def generate_row(rng: random.Random) -> dict:
    row = {
        "specialty": _sample_specialty(rng),
        "procedure_complexity": rng.choices(["baja", "media", "alta"], weights=[3, 4, 2])[0],
        "priority": rng.choices(["baja", "media", "alta", "critica"], weights=[3, 4, 2, 1])[0],
        "documentation_complete": rng.random() > 0.28,
        "informed_consent": rng.random() > 0.22,
        "has_prior_complaints": rng.random() > 0.82,
        "time_since_incident_days": rng.randint(0, 365),
    }
    score = _true_score(row, rng)
    row["risk_score"] = round(score, 4)
    row["risk_level"] = risk_level_from_score(score)
    return row


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--rows", type=int, default=40000)
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--balance", action="store_true",
                        help="Balancea las 4 clases por rejection sampling (recomendado).")
    parser.add_argument("--out", type=str, default="../data/risk_dataset.csv")
    args = parser.parse_args()

    rng = random.Random(args.seed)
    out_path = Path(__file__).parent / args.out
    out_path.parent.mkdir(parents=True, exist_ok=True)

    rows: list[dict] = []
    if args.balance:
        # Tope por clase; se sobre-muestrea y se recorta a un cupo equitativo.
        per_class = args.rows // len(RISK_LEVELS)
        counts = Counter()
        attempts = 0
        max_attempts = args.rows * 60
        while len(rows) < args.rows and attempts < max_attempts:
            attempts += 1
            r = generate_row(rng)
            lvl = r["risk_level"]
            if counts[lvl] < per_class:
                rows.append(r)
                counts[lvl] += 1
        # Rellena el remanente sin filtro (por si alguna clase es muy rara).
        while len(rows) < args.rows:
            rows.append(generate_row(rng))
    else:
        rows = [generate_row(rng) for _ in range(args.rows)]

    fieldnames = [
        "specialty", "procedure_complexity", "priority",
        "documentation_complete", "informed_consent", "has_prior_complaints",
        "time_since_incident_days", "risk_score", "risk_level",
    ]
    with out_path.open("w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(rows)

    dist = Counter(r["risk_level"] for r in rows)
    print(f"Generadas {len(rows)} filas en {out_path.resolve()}")
    print("Distribución de clases:")
    for lvl in RISK_LEVELS:
        n = dist[lvl]
        print(f"  {lvl:9} {n:6}  ({n / len(rows) * 100:4.1f}%)")


if __name__ == "__main__":
    main()
