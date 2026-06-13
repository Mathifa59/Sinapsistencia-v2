"""
Carga el RandomForest entrenado (training/train_risk_model.py) y expone
`assess()`, que reproduce el contrato de /api/v1/risk-assessment.

Explicabilidad (HU-31/32, "es apoyo no decision" HU-43): para cada caso se
arman `risk_factors` combinando la importancia GLOBAL de cada feature (del
RF, via feature_importances_) con su valor normalizado en el caso concreto,
de forma analoga a una explicacion tipo SHAP simplificada.
"""

import uuid
from pathlib import Path
from typing import Optional

import joblib
import numpy as np
import pandas as pd

from app.schemas import RiskAssessmentRequest, RiskAssessmentResponse, RiskFactor

MODEL_PATH = Path(__file__).resolve().parents[2] / "models" / "risk_model.joblib"

CATEGORICAL_NOMINAL = ["specialty"]
ORDINAL_COLS = ["procedure_complexity", "priority"]
BOOLEAN_COLS = ["documentation_complete", "informed_consent", "has_prior_complaints"]
NUMERIC_COLS = ["time_since_incident_days"]

COMPLEXITY_RANK = {"baja": 0.0, "media": 0.5, "alta": 1.0}
PRIORITY_RANK = {"baja": 0.0, "media": 1 / 3, "alta": 2 / 3, "critica": 1.0}

# Debe mantenerse sincronizado con SPECIALTY_BASELINE en
# training/generate_risk_dataset.py (mismas claves/valores).
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

RECOMMENDATIONS_BY_FACTOR = {
    "documentation": "Completar la historia clínica y la documentación de soporte del caso.",
    "informed_consent": "Verificar y archivar el consentimiento informado firmado.",
    "prior_complaints": "Revisar el historial de quejas previas antes de continuar.",
    "procedure_complexity": "Solicitar una segunda opinión para procedimientos de alta complejidad.",
    "priority": "Escalar la consulta dado su nivel de prioridad.",
    "time_factor": "Documentar cuanto antes los hechos: el tiempo transcurrido puede afectar la defensa legal.",
    "specialty_risk": "Considerar protocolos reforzados propios de la especialidad.",
}


class RiskModel:
    def __init__(self, model_path: Path = MODEL_PATH):
        artifact = joblib.load(model_path)
        self.preprocessor = artifact["preprocessor"]
        self.clf = artifact["clf"]
        self.reg = artifact["reg"]
        self.feature_names: list[str] = artifact["feature_names"]
        self.level_order: list[str] = artifact["level_order"]
        self.model_version: str = artifact["model_version"]

        # Importancia global por feature (agrega las columnas one-hot de "specialty"
        # en una sola entrada "specialty" para no exponer 20 columnas dummy).
        importances = self.clf.feature_importances_
        self._raw_importance = dict(zip(self.feature_names, importances))
        specialty_importance = sum(
            v for k, v in self._raw_importance.items() if k.startswith("specialty_")
        )
        self.importance = {"specialty": specialty_importance}
        for col in ORDINAL_COLS + BOOLEAN_COLS + NUMERIC_COLS:
            self.importance[col] = self._raw_importance.get(col, 0.0)

    def _to_frame(self, req: RiskAssessmentRequest) -> pd.DataFrame:
        return pd.DataFrame([{
            "specialty": req.specialty,
            "procedure_complexity": req.procedure_complexity,
            "priority": req.priority,
            "documentation_complete": int(req.documentation_complete),
            "informed_consent": int(req.informed_consent),
            "has_prior_complaints": int(req.has_prior_complaints),
            "time_since_incident_days": req.time_since_incident_days or 0,
        }])

    def _build_factors(self, req: RiskAssessmentRequest, baseline: float) -> list[RiskFactor]:
        time_days = req.time_since_incident_days or 0
        time_norm = min(time_days / 365, 1.0)

        factors = [
            RiskFactor(
                name="specialty_risk",
                weight=round(self.importance["specialty"], 4),
                value=round(baseline, 4),
                contribution=round(self.importance["specialty"] * baseline, 4),
                description=f"Riesgo base de la especialidad ({req.specialty}).",
            ),
            RiskFactor(
                name="procedure_complexity",
                weight=round(self.importance["procedure_complexity"], 4),
                value=COMPLEXITY_RANK[req.procedure_complexity],
                contribution=round(
                    self.importance["procedure_complexity"] * COMPLEXITY_RANK[req.procedure_complexity], 4
                ),
                description="Complejidad del procedimiento reportada.",
            ),
            RiskFactor(
                name="priority",
                weight=round(self.importance["priority"], 4),
                value=PRIORITY_RANK[req.priority],
                contribution=round(self.importance["priority"] * PRIORITY_RANK[req.priority], 4),
                description="Prioridad asignada a la consulta.",
            ),
            RiskFactor(
                name="documentation",
                weight=round(self.importance["documentation_complete"], 4),
                value=0.0 if req.documentation_complete else 1.0,
                contribution=round(
                    self.importance["documentation_complete"] * (0.0 if req.documentation_complete else 1.0), 4
                ),
                description="Documentación clínica incompleta" if not req.documentation_complete
                else "Documentación clínica completa",
            ),
            RiskFactor(
                name="informed_consent",
                weight=round(self.importance["informed_consent"], 4),
                value=0.0 if req.informed_consent else 1.0,
                contribution=round(
                    self.importance["informed_consent"] * (0.0 if req.informed_consent else 1.0), 4
                ),
                description="Consentimiento informado presente" if req.informed_consent
                else "Consentimiento informado ausente",
            ),
            RiskFactor(
                name="prior_complaints",
                weight=round(self.importance["has_prior_complaints"], 4),
                value=1.0 if req.has_prior_complaints else 0.0,
                contribution=round(
                    self.importance["has_prior_complaints"] * (1.0 if req.has_prior_complaints else 0.0), 4
                ),
                description="Existen quejas previas registradas" if req.has_prior_complaints
                else "Sin quejas previas registradas",
            ),
            RiskFactor(
                name="time_factor",
                weight=round(self.importance["time_since_incident_days"], 4),
                value=round(time_norm, 4),
                contribution=round(self.importance["time_since_incident_days"] * time_norm, 4),
                description="Tiempo transcurrido desde el incidente.",
            ),
        ]
        return factors

    def _recommendations(self, factors: list[RiskFactor], risk_level: str) -> list[str]:
        # Ordena por contribución descendente y toma los 3 factores más relevantes
        # con riesgo presente (value > 0) para generar recomendaciones priorizadas.
        relevant = [f for f in factors if f.value > 0 and f.name in RECOMMENDATIONS_BY_FACTOR]
        relevant.sort(key=lambda f: f.contribution, reverse=True)
        recs = [RECOMMENDATIONS_BY_FACTOR[f.name] for f in relevant[:3]]
        if risk_level in ("alto", "critico") and not recs:
            recs.append("Revisar el caso con el equipo legal a la brevedad.")
        return recs

    def assess(self, req: RiskAssessmentRequest) -> RiskAssessmentResponse:
        X = self._to_frame(req)
        X_t = self.preprocessor.transform(X)

        risk_level = self.clf.predict(X_t)[0]
        risk_score = float(np.clip(self.reg.predict(X_t)[0], 0.0, 1.0))

        baseline = SPECIALTY_BASELINE.get(req.specialty, 0.30)
        factors = self._build_factors(req, baseline)
        recommendations = self._recommendations(factors, risk_level)

        return RiskAssessmentResponse(
            case_id=req.case_id or f"caso-{uuid.uuid4().hex[:8]}",
            risk_score=round(risk_score, 4),
            risk_level=risk_level,
            risk_factors=factors,
            recommendations=recommendations,
            specialty_risk_baseline=baseline,
            model_version=self.model_version,
        )


_instance: Optional[RiskModel] = None


def get_risk_model() -> RiskModel:
    global _instance
    if _instance is None:
        _instance = RiskModel()
    return _instance
