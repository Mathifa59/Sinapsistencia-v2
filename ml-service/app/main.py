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
  POST /api/v1/extract-case-report   (imagen de un informe -> campos del formulario de caso)
"""

import threading

from fastapi import FastAPI, File, HTTPException, Response, UploadFile

from app.extraction.config import EXTRACTOR_VERSION, MAX_IMAGE_BYTES, MIN_TEXT_CHARS, OCR_ENGINE
from app.extraction.ocr import InvalidImage, OcrUnavailable, read_lines
from app.extraction.ocr import warm_up as warm_up_ocr
from app.extraction.parser import parse_report
from app.extraction.schemas import CaseReportExtraction
from app.matching.model import W_CONTENT, W_PERFORMANCE, get_matching_model
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
    # El OCR del extractor de informes se calienta en segundo plano: no retrasa el arranque.
    threading.Thread(target=warm_up_ocr, daemon=True).start()
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
    # H-02: distinguir None (el backend no envio el campo -> corpus estatico legacy)
    # de [] (el backend SI lo envio, pero hay 0 abogados disponibles -> 0 candidatos
    # vivos, nunca el corpus estatico de perfiles ajenos a la BD).
    live_corpus = [l.model_dump() for l in req.lawyers] if req.lawyers is not None else None
    recs = model.recommend(req.doctor_profile, req.top_k, lawyers=live_corpus)
    return RecommendationsResponse(
        recommendations=recs,
        model_info={
            "model": "tfidf-cosine+perf-v2",
            "corpus": "live" if live_corpus is not None else "static-fallback",
            "corpus_size": len(live_corpus) if live_corpus is not None else len(model.lawyers),
            # H-03 (RF-03.4): pesos y version del pipeline viajan con cada
            # ejecucion -- no asumir 70/30 fijo al interpretar ejecuciones futuras.
            "weights": {"content": W_CONTENT, "performance": W_PERFORMANCE},
        },
    )


@app.post("/api/v1/extract-case-report", response_model=CaseReportExtraction)
def extract_case_report(response: Response, file: UploadFile = File(...)):
    """Lee la imagen de un informe con OCR local y propone los campos del formulario de caso.

    La imagen se procesa en memoria: no se guarda ni se registra su contenido. Los datos
    personales detectados se omiten del texto devuelto (Ley 29733).
    """
    response.headers["Cache-Control"] = "no-store"
    data = file.file.read(MAX_IMAGE_BYTES + 1)
    try:
        lines = read_lines(data, file.content_type)
    except InvalidImage as exc:
        raise HTTPException(status_code=400, detail=str(exc))
    except OcrUnavailable:
        raise HTTPException(
            status_code=503,
            detail="El reconocimiento de imágenes no está disponible. Completa el formulario manualmente.",
        )

    if sum(len(line.text) for line in lines) < MIN_TEXT_CHARS:
        raise HTTPException(
            status_code=422,
            detail="No se pudo leer texto en la imagen. Usa una foto nítida, de frente y bien iluminada.",
        )

    parsed = parse_report([line.text for line in lines])
    mean_confidence = sum(line.confidence for line in lines) / len(lines)
    return CaseReportExtraction(
        extractor_version=EXTRACTOR_VERSION,
        fields=parsed["fields"],
        pii_redacted=parsed["piiRedacted"],
        ocr={"engine": OCR_ENGINE, "lines": len(lines), "mean_line_confidence": round(mean_confidence, 3)},
        advisory=(
            "Los campos se propusieron automáticamente a partir de la imagen: revísalos y corrígelos. "
            "La extracción es un apoyo, no una decisión; la revisión humana es siempre necesaria."
        ),
    )
