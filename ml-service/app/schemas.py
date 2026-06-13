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


class RecommendationsRequest(BaseModel):
    doctor_id: str
    doctor_profile: DoctorProfile
    top_k: int = 10


class FeatureImportance(BaseModel):
    feature: str
    importance: float
    description: str


class LawyerRecommendation(BaseModel):
    lawyer_id: str
    score: float
    content_score: float
    collaborative_score: float
    matched_specialties: list[str]
    model_used: str
    feature_importance: list[FeatureImportance]
    reasons: list[str]


class RecommendationsResponse(BaseModel):
    recommendations: list[LawyerRecommendation]
    model_info: dict
