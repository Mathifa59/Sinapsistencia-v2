"""
Genera un dataset sintetico de evaluacion de riesgo medico-legal para
entrenar el Random Forest (HU-29/HU-30).

No usa datos reales de pacientes (Ley 29733): cada fila es un caso
simulado con un nivel de riesgo "verdadero" calculado a partir de reglas
de negocio + ruido aleatorio, que es lo que el modelo aprende a aproximar.

Uso:
    python generate_risk_dataset.py [--rows 6000] [--seed 42] [--out ../data/risk_dataset.csv]
"""

import argparse
import csv
import random
from pathlib import Path

# Debe coincidir EXACTO con MEDICAL_SPECIALTIES del frontend
# (frontend/src/app/shared/constants.ts), ya que es el valor que llega
# en el campo "specialty" del payload de /api/v1/risk-assessment.
SPECIALTIES = [
    "Medicina General", "Cirugía General", "Cardiología", "Neurología", "Oncología",
    "Pediatría", "Ginecología y Obstetricia", "Traumatología", "Oftalmología",
    "Dermatología", "Psiquiatría", "Urología", "Gastroenterología", "Endocrinología",
    "Reumatología", "Neumología", "Nefrología", "Infectología", "Hematología",
    "Anestesiología",
]

# Riesgo base por especialidad: procedimientos invasivos / alta criticidad
# tienden a generar mas reclamos medico-legales (literatura citada en el charter).
SPECIALTY_BASELINE = {
    "Cirugía General": 0.45,
    "Ginecología y Obstetricia": 0.50,
    "Anestesiología": 0.48,
    "Traumatología": 0.40,
    "Neurología": 0.42,
    "Cardiología": 0.38,
    "Oncología": 0.40,
    "Urología": 0.35,
    "Gastroenterología": 0.30,
    "Neumología": 0.28,
    "Nefrología": 0.30,
    "Infectología": 0.27,
    "Hematología": 0.28,
    "Reumatología": 0.22,
    "Endocrinología": 0.24,
    "Oftalmología": 0.25,
    "Psiquiatría": 0.30,
    "Pediatría": 0.33,
    "Dermatología": 0.18,
    "Medicina General": 0.20,
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


def generate_row(rng: random.Random) -> dict:
    specialty = rng.choice(SPECIALTIES)
    complexity = rng.choice(["baja", "media", "alta"])
    priority = rng.choice(["baja", "media", "alta", "critica"])
    documentation_complete = rng.random() > 0.30
    informed_consent = rng.random() > 0.25
    has_prior_complaints = rng.random() > 0.80
    time_since_incident_days = rng.randint(0, 365)

    score = SPECIALTY_BASELINE[specialty]
    score += COMPLEXITY_WEIGHT[complexity]
    score += PRIORITY_WEIGHT[priority]
    if not documentation_complete:
        score += 0.15
    if not informed_consent:
        score += 0.20
    if has_prior_complaints:
        score += 0.15
    score += (time_since_incident_days / 365) * 0.05

    # Ruido para que el modelo no memorice una formula lineal exacta.
    score += rng.gauss(0, 0.05)
    score = max(0.0, min(1.0, score))

    return {
        "specialty": specialty,
        "procedure_complexity": complexity,
        "priority": priority,
        "documentation_complete": documentation_complete,
        "informed_consent": informed_consent,
        "has_prior_complaints": has_prior_complaints,
        "time_since_incident_days": time_since_incident_days,
        "risk_score": round(score, 4),
        "risk_level": risk_level_from_score(score),
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--rows", type=int, default=6000)
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--out", type=str, default="../data/risk_dataset.csv")
    args = parser.parse_args()

    rng = random.Random(args.seed)
    out_path = Path(__file__).parent / args.out
    out_path.parent.mkdir(parents=True, exist_ok=True)

    fieldnames = [
        "specialty", "procedure_complexity", "priority",
        "documentation_complete", "informed_consent", "has_prior_complaints",
        "time_since_incident_days", "risk_score", "risk_level",
    ]

    with out_path.open("w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        for _ in range(args.rows):
            writer.writerow(generate_row(rng))

    print(f"Generadas {args.rows} filas en {out_path.resolve()}")


if __name__ == "__main__":
    main()
