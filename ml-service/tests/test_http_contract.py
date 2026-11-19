"""
Contrato HTTP que consume MlProxyService (Spring): rutas, códigos y forma de las
respuestas, ejercitados con un cliente de prueba real (arranca la aplicación y
dispara la carga de modelos del evento de inicio).
"""

from fastapi.testclient import TestClient

from app.main import app

CASO = {
    "case_id": "c-1",
    "specialty": "Cardiología",
    "procedure_complexity": "alta",
    "priority": "alta",
    "documentation_complete": False,
    "informed_consent": True,
    "has_prior_complaints": False,
    "time_since_incident_days": 30,
}


def test_las_rutas_del_contrato_responden():
    with TestClient(app) as c:  # el `with` ejecuta el evento de inicio (carga de modelos)
        assert c.get("/health").json()["status"] == "ok"
        info = c.get("/api/v1/model/info").json()
        assert info["status"] == "loaded" and info["risk_model"] == "random_forest"

        riesgo = c.post("/api/v1/risk-assessment", json=CASO)
        assert riesgo.status_code == 200
        cuerpo = riesgo.json()
        assert cuerpo["risk_level"] in {"bajo", "moderado", "alto", "critico"}
        assert 0.0 <= cuerpo["risk_score"] <= 1.0
        assert len(cuerpo["risk_factors"]) == 7

        rec = c.post("/api/v1/recommendations", json={
            "doctor_id": "d1",
            "doctor_profile": {"specialty": "Psiquiatría", "case_text": ""},
            "top_k": 3,
        })
        assert rec.status_code == 200
        r = rec.json()
        assert len(r["recommendations"]) <= 3
        assert r["model_info"]["corpus"] == "static-fallback"


def test_valores_fuera_de_catalogo_se_rechazan_con_422():
    with TestClient(app) as c:
        malo = dict(CASO, priority="urgentisima")
        assert c.post("/api/v1/risk-assessment", json=malo).status_code == 422
        sin_especialidad = {k: v for k, v in CASO.items() if k != "specialty"}
        assert c.post("/api/v1/risk-assessment", json=sin_especialidad).status_code == 422


def test_el_texto_libre_no_cambia_el_contrato_de_riesgo():
    """Un cliente antiguo que aún envía `description` no rompe y el campo se descarta."""
    with TestClient(app) as c:
        res = c.post("/api/v1/risk-assessment", json=dict(CASO, description="texto libre del caso"))
        assert res.status_code == 200
        assert "description" not in res.json()
