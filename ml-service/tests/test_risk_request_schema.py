"""El RF solo consume las 7 variables estructuradas: el contrato de entrada no
admite texto libre (minimizacion de datos, Ley 29733)."""

from app.schemas import RiskAssessmentRequest

CAMPOS_ESPERADOS = {
    "case_id",
    "specialty",
    "procedure_complexity",
    "priority",
    "documentation_complete",
    "informed_consent",
    "has_prior_complaints",
    "time_since_incident_days",
}


def test_el_contrato_de_riesgo_no_tiene_campo_de_texto_libre():
    assert set(RiskAssessmentRequest.model_fields) == CAMPOS_ESPERADOS


def test_un_cliente_antiguo_que_aun_envia_description_no_rompe_y_se_descarta():
    req = RiskAssessmentRequest(specialty="Cardiología", description="texto libre del caso")
    assert not hasattr(req, "description")
    assert set(req.model_dump()) == CAMPOS_ESPERADOS
