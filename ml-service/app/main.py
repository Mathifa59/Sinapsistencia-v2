"""
Servicio ML de Sinapsistencia (FastAPI).

Modelos:
  - Riesgo (HU-29/30): RandomForest (clasificacion risk_level + regresion risk_score).
  - Matching (HU-31/32): TF-IDF + similitud coseno entre perfil de medico y abogados.

Contrato (consumido por MlProxyService en Spring, paths sin cambios):
  GET  /health
  GET  /api/v1/model/info
  POST /api/v1/risk-assessment
  POST /api/v1/recommendations
"""

from fastapi import FastAPI, HTTPException

from app.matching.model import get_matching_model
from app.risk.model import get_risk_model
from app.schemas import (
    RecommendationsRequest,
    RecommendationsResponse,
    RiskAssessmentRequest,
    RiskAssessmentResponse,
)

SERVICE_VERSION = "1.2.0"

app = FastAPI(title="Sinapsistencia ML Service", version=SERVICE_VERSION)


@app.on_event("startup")
def _load_models() -> None:
    # Carga ambos modelos al iniciar para fallar rapido si falta risk_model.joblib.
    get_risk_model()
    get_matching_model()


@app.get("/health")
def health():
    return {"status": "ok", "service": "sinapsistencia-ml", "version": SERVICE_VERSION}


@app.get("/api/v1/model/info")
def model_info():
    risk_model = get_risk_model()
    return {
        "status": "loaded",
        "model_version": risk_model.model_version,
        "content_model": "tfidf-cosine",
        "collaborative_model": "none",
        "risk_model": "random_forest",
    }


@app.post("/api/v1/risk-assessment", response_model=RiskAssessmentResponse)
def risk_assessment(req: RiskAssessmentRequest):
    try:
        return get_risk_model().assess(req)
    except Exception as exc:  # noqa: BLE001
        raise HTTPException(status_code=500, detail=f"Error en la evaluación de riesgo: {exc}")


@app.post("/api/v1/recommendations", response_model=RecommendationsResponse)
def recommendations(req: RecommendationsRequest):
    model = get_matching_model()
    recs = model.recommend(req.doctor_profile, req.top_k)
    return RecommendationsResponse(
        recommendations=recs,
        model_info={"model": "tfidf-cosine-v1", "trained_at": "build-time"},
    )
