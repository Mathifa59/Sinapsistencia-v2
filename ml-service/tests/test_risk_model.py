"""
Pruebas del clasificador de riesgo (RandomForest rf-v2) y de su explicación.

Se cargan los modelos reales de `models/`, no dobles: lo que se comprueba es el
contrato (7 variables de entrada, 4 niveles de salida, puntaje en [0, 1]), la
reproducibilidad (misma entrada, misma salida) y que cada factor del resultado
sea coherente con el caso. No se afirma nada sobre la precisión del modelo:
eso lo mide training/ con sus propias métricas.
"""

import pytest
from fastapi import HTTPException

import app.main as main
from app.risk.baselines import SPECIALTY_BASELINE
from app.risk.model import (
    COMPLEXITY_RANK,
    PRIORITY_RANK,
    RECOMMENDATIONS_BY_FACTOR,
    RiskModel,
    get_risk_model,
)
from app.schemas import RiskAssessmentRequest, RiskFactor

NIVELES = {"bajo", "moderado", "alto", "critico"}
FACTORES = {
    "specialty_risk", "procedure_complexity", "priority", "documentation",
    "informed_consent", "prior_complaints", "time_factor",
}


@pytest.fixture(scope="module")
def modelo() -> RiskModel:
    return get_risk_model()


def _req(**kw) -> RiskAssessmentRequest:
    base = dict(case_id="caso-prueba", specialty="Cardiología")
    base.update(kw)
    return RiskAssessmentRequest(**base)


def test_la_respuesta_cumple_el_contrato(modelo):
    res = modelo.assess(_req())
    assert res.risk_level in NIVELES
    assert 0.0 <= res.risk_score <= 1.0
    assert {f.name for f in res.risk_factors} == FACTORES
    assert res.model_version
    assert res.case_id == "caso-prueba"


def test_sin_case_id_se_genera_uno(modelo):
    res = modelo.assess(RiskAssessmentRequest(specialty="Cardiología"))
    assert res.case_id.startswith("caso-")


def test_misma_entrada_misma_salida(modelo):
    a = modelo.assess(_req(priority="alta", has_prior_complaints=True))
    b = modelo.assess(_req(priority="alta", has_prior_complaints=True))
    assert a.model_dump() == b.model_dump()


@pytest.mark.parametrize("especialidad", sorted(SPECIALTY_BASELINE))
def test_las_20_especialidades_se_evaluan(modelo, especialidad):
    res = modelo.assess(_req(specialty=especialidad))
    assert res.risk_level in NIVELES
    assert res.specialty_risk_baseline == SPECIALTY_BASELINE[especialidad]


def test_especialidad_desconocida_usa_riesgo_base_neutro(modelo):
    res = modelo.assess(_req(specialty="Especialidad inexistente"))
    assert res.specialty_risk_baseline == 0.30


@pytest.mark.parametrize("prioridad", PRIORITY_RANK)
@pytest.mark.parametrize("complejidad", COMPLEXITY_RANK)
def test_todas_las_combinaciones_ordinales_son_validas(modelo, prioridad, complejidad):
    res = modelo.assess(_req(priority=prioridad, procedure_complexity=complejidad))
    assert res.risk_level in NIVELES


def test_tiempo_ausente_equivale_a_cero_dias(modelo):
    sin = modelo.assess(_req(time_since_incident_days=None))
    cero = modelo.assess(_req(time_since_incident_days=0))
    assert sin.model_dump() == cero.model_dump()


def test_el_factor_de_tiempo_se_satura_en_un_anio(modelo):
    un_anio = modelo.assess(_req(time_since_incident_days=365))
    cinco = modelo.assess(_req(time_since_incident_days=365 * 5))
    f = lambda r: next(x for x in r.risk_factors if x.name == "time_factor")
    assert f(un_anio).value == 1.0
    assert f(cinco).value == 1.0


def test_los_factores_reflejan_los_datos_del_caso(modelo):
    res = modelo.assess(_req(
        documentation_complete=False, informed_consent=False, has_prior_complaints=True,
        procedure_complexity="alta", priority="critica",
    ))
    por_nombre = {f.name: f for f in res.risk_factors}
    assert por_nombre["documentation"].value == 1.0
    assert por_nombre["informed_consent"].value == 1.0
    assert por_nombre["prior_complaints"].value == 1.0
    assert por_nombre["procedure_complexity"].value == COMPLEXITY_RANK["alta"]
    assert por_nombre["priority"].value == PRIORITY_RANK["critica"]
    for f in res.risk_factors:
        assert f.contribution == pytest.approx(f.weight * f.value, abs=1e-3)


def test_un_caso_completo_no_marca_documentacion_ni_consentimiento(modelo):
    res = modelo.assess(_req(documentation_complete=True, informed_consent=True, has_prior_complaints=False))
    por_nombre = {f.name: f for f in res.risk_factors}
    assert por_nombre["documentation"].value == 0.0
    assert por_nombre["informed_consent"].value == 0.0
    assert por_nombre["prior_complaints"].value == 0.0


def test_las_recomendaciones_salen_de_factores_con_riesgo_presente(modelo):
    res = modelo.assess(_req(documentation_complete=False, informed_consent=False))
    assert 1 <= len(res.recommendations) <= 3
    for r in res.recommendations:
        assert r in RECOMMENDATIONS_BY_FACTOR.values()


def test_las_recomendaciones_se_ordenan_por_contribucion(modelo):
    f = lambda n, c: RiskFactor(name=n, weight=0.1, value=1.0, contribution=c, description="")
    factores = [f("documentation", 0.05), f("informed_consent", 0.30), f("prior_complaints", 0.10)]
    recs = modelo._recommendations(factores, "moderado")
    assert recs[0] == RECOMMENDATIONS_BY_FACTOR["informed_consent"]
    assert len(recs) == 3


def test_riesgo_alto_sin_factores_presentes_sugiere_revision_legal(modelo):
    assert modelo._recommendations([], "alto") == ["Revisar el caso con el equipo legal a la brevedad."]
    assert modelo._recommendations([], "critico") == ["Revisar el caso con el equipo legal a la brevedad."]
    assert modelo._recommendations([], "bajo") == []


def test_la_importancia_global_suma_uno(modelo):
    assert sum(modelo.importance.values()) == pytest.approx(1.0, abs=1e-6)
    assert set(modelo.importance) == {
        "specialty", "procedure_complexity", "priority", "documentation_complete",
        "informed_consent", "has_prior_complaints", "time_since_incident_days",
    }


def test_endpoint_de_riesgo_devuelve_la_evaluacion():
    res = main.risk_assessment(_req())
    assert res.risk_level in NIVELES


def test_endpoint_de_riesgo_traduce_un_fallo_interno_en_500(monkeypatch):
    class Roto:
        def assess(self, _):
            raise RuntimeError("modelo no disponible")

    monkeypatch.setattr(main, "get_risk_model", lambda: Roto())
    with pytest.raises(HTTPException) as e:
        main.risk_assessment(_req())
    assert e.value.status_code == 500
    assert "modelo no disponible" in e.value.detail


def test_health_y_model_info_declaran_el_modelo():
    assert main.health()["status"] == "ok"
    info = main.model_info()
    assert info["status"] == "loaded"
    assert info["risk_model"] == "random_forest"
    assert info["content_model"] == "tfidf-cosine"
    assert info["model_version"]
