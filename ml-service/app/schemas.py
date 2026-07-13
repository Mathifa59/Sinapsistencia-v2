"""Esquemas Pydantic — espejan el contrato consumido por MlProxyService (Spring)."""

from typing import Literal, Optional

from pydantic import BaseModel, Field

RiskLevel = Literal["bajo", "moderado", "alto", "critico"]
Complexity = Literal["baja", "media", "alta"]
Priority = Literal["baja", "media", "alta", "critica"]


class RiskAssessmentRequest(BaseModel):
    case_id: Optional[str] = None
    specialty: str
    procedure_complexity: Complexity = "media"
    priority: Priority = "media"
    documentation_complete: bool = True
    informed_consent: bool = True
    has_prior_complaints: bool = False
    time_since_incident_days: Optional[int] = None
    description: Optional[str] = ""


class RiskFactor(BaseModel):
    name: str
    weight: float
    value: float
    contribution: float
    description: str


class RiskAssessmentResponse(BaseModel):
    case_id: str
    risk_score: float
    risk_level: RiskLevel
    risk_factors: list[RiskFactor]
    recommendations: list[str]
    specialty_risk_baseline: float
    model_version: str


class DoctorProfile(BaseModel):
    name: Optional[str] = None
    specialty: Optional[str] = None
    sub_specialties: list[str] = Field(default_factory=list)
    hospital: Optional[str] = None
    years_experience: Optional[int] = None
    # Texto libre del caso (titulo + descripcion + tipo de evento): entra al
    # vector TF-IDF para que el matching sea semantico, no solo por especialidad.
    case_text: Optional[str] = ""


class LawyerCorpusItem(BaseModel):
    """Abogado del corpus VIVO: el backend envia la lista real de la BD en cada
    matching (incluye abogados registrados por la app y ratings actuales)."""
    lawyer_id: str
    name: Optional[str] = ""
    specialties: list[str] = Field(default_factory=list)
    medical_areas: list[str] = Field(default_factory=list)
    bio: Optional[str] = ""
    rating: float = 0.0
    resolved_cases: int = 0
    years_experience: int = 0


class RecommendationsRequest(BaseModel):
    doctor_id: str
    doctor_profile: DoctorProfile
    top_k: int = 10
    # Corpus vivo (opcional): si no viene, se usa lawyers_corpus.json como fallback.
    lawyers: Optional[list[LawyerCorpusItem]] = None


class FeatureImportance(BaseModel):
    feature: str
    importance: float
    description: str


class LawyerRecommendation(BaseModel):
    lawyer_id: str
    score: float
    content_score: float
    performance_score: float = 0.0
    collaborative_score: float
    matched_specialties: list[str]
    model_used: str
    feature_importance: list[FeatureImportance]
    reasons: list[str]


class RecommendationsResponse(BaseModel):
    recommendations: list[LawyerRecommendation]
    model_info: dict
