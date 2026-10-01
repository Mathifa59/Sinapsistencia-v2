"""
H-02: MatchingModel.recommend debe distinguir lawyers=None (corpus estatico
de fallback) de lawyers=[] (0 candidatos vivos reales). Antes, `if lawyers:`
trataba una lista vacia como ausente y reciclaba el corpus estatico de
perfiles ajenos a la BD -- un bug de "0 disponibles se ve como el corpus de
siempre" que esta prueba fija como regresion.
"""

from app.matching.model import MatchingModel
from app.schemas import DoctorProfile

LIVE_LAWYER = {
    "lawyer_id": "11111111-1111-1111-1111-111111111111",
    "name": "Abg. Prueba",
    "specialties": ["Derecho Médico"],
    "medical_areas": ["Psiquiatría"],
    "bio": "Especialista en psiquiatría y responsabilidad médica.",
    "rating": 4.5,
    "resolved_cases": 20,
    "years_experience": 10,
}

DOCTOR_PROFILE = DoctorProfile(
    name="Dr. Prueba",
    specialty="Psiquiatría",
    sub_specialties=[],
    hospital="Clínica Demo",
    years_experience=5,
    case_text="Consulta de prueba sobre responsabilidad médica en psiquiatría",
)


def _model() -> MatchingModel:
    # Instancia propia (no el singleton get_matching_model()) para no depender
    # de estado global entre pruebas.
    return MatchingModel()


def test_lawyers_none_usa_corpus_estatico_de_fallback():
    model = _model()
    recs = model.recommend(DOCTOR_PROFILE, top_k=10, lawyers=None)
    assert len(recs) == min(10, len(model.lawyers))
    assert len(model.lawyers) > 0


def test_lawyers_vacio_no_recicla_el_corpus_estatico():
    model = _model()
    recs = model.recommend(DOCTOR_PROFILE, top_k=10, lawyers=[])
    assert recs == []


def test_lawyers_vivo_no_vacio_usa_ese_corpus():
    model = _model()
    recs = model.recommend(DOCTOR_PROFILE, top_k=10, lawyers=[LIVE_LAWYER])
    assert len(recs) == 1
    assert recs[0].lawyer_id == LIVE_LAWYER["lawyer_id"]
