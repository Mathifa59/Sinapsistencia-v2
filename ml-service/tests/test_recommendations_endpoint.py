"""
H-02: el endpoint /api/v1/recommendations debe reenviar la distincion
None/[] de `req.lawyers` a MatchingModel.recommend sin colapsarla -- antes
`if req.lawyers else None` convertia una lista vacia en None antes de
llegar al modelo. Llama a la funcion de FastAPI directamente (sin
TestClient/httpx) para no agregar dependencias solo para esta prueba.
"""

from app.main import recommendations
from app.schemas import DoctorProfile, RecommendationsRequest

DOCTOR_PROFILE = DoctorProfile(
    name="Dr. Prueba",
    specialty="Psiquiatría",
    case_text="Consulta de prueba",
)


def test_lawyers_ausente_reporta_corpus_estatico():
    req = RecommendationsRequest(doctor_id="d1", doctor_profile=DOCTOR_PROFILE, top_k=5, lawyers=None)
    res = recommendations(req)
    assert res.model_info["corpus"] == "static-fallback"
    assert res.model_info["corpus_size"] > 0


def test_lawyers_vacio_reporta_corpus_vivo_con_cero_candidatos():
    req = RecommendationsRequest(doctor_id="d1", doctor_profile=DOCTOR_PROFILE, top_k=5, lawyers=[])
    res = recommendations(req)
    assert res.model_info["corpus"] == "live"
    assert res.model_info["corpus_size"] == 0
    assert res.recommendations == []
