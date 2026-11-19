"""
Pruebas del emparejamiento médico–abogado (TF-IDF + coseno + desempeño).

Comprueban las propiedades que la memoria afirma del modelo: el score es la
combinación 0,70 contenido + 0,30 desempeño, el desempeño está acotado y
satura, el orden es reproducible y las razones y los términos que explican
cada recomendación salen del propio cálculo. No miden relevancia (no hay
juicios humanos todavía): solo que el modelo hace lo que dice hacer.
"""

import pytest

import app.main as main
from app.matching.model import (
    CASES_SATURATION,
    EXPERIENCE_SATURATION_YEARS,
    MODEL_VERSION,
    W_CASES,
    W_CONTENT,
    W_EXPERIENCE,
    W_PERFORMANCE,
    W_RATING,
    MatchingModel,
    _normalize,
    _performance_score,
)
from app.schemas import DoctorProfile, LawyerCorpusItem, RecommendationsRequest


def _abogado(i, *, areas=("Psiquiatría",), rating=4.0, casos=20, anios=10, bio="responsabilidad médica"):
    return {
        "lawyer_id": f"00000000-0000-0000-0000-{i:012d}",
        "name": f"Abg. {i}",
        "specialties": ["Derecho Médico"],
        "medical_areas": list(areas),
        "bio": bio,
        "rating": rating,
        "resolved_cases": casos,
        "years_experience": anios,
    }


MEDICO = DoctorProfile(
    name="Dra. Prueba", specialty="Psiquiatría", hospital="Clínica Demo",
    case_text="queja por responsabilidad médica en psiquiatría",
)


@pytest.fixture(scope="module")
def modelo() -> MatchingModel:
    return MatchingModel()


def test_los_pesos_declarados_suman_uno():
    assert W_CONTENT + W_PERFORMANCE == pytest.approx(1.0)
    assert W_RATING + W_CASES + W_EXPERIENCE == pytest.approx(1.0)
    assert (W_CONTENT, W_PERFORMANCE) == (0.70, 0.30)


def test_normalize_quita_tildes_y_mayusculas():
    assert _normalize("Psiquiatría Ginecología") == "psiquiatria ginecologia"


def test_el_desempeno_esta_acotado_entre_cero_y_uno():
    assert _performance_score({}) == 0.0
    maximo = _performance_score({"rating": 5, "resolved_cases": 10**6, "years_experience": 80})
    assert maximo == pytest.approx(1.0)
    assert 0.0 <= _performance_score({"rating": 9, "resolved_cases": -4, "years_experience": -1}) <= 1.0


def test_el_desempeno_satura_en_casos_y_experiencia():
    base = {"rating": 4.0, "resolved_cases": CASES_SATURATION, "years_experience": EXPERIENCE_SATURATION_YEARS}
    mas = {"rating": 4.0, "resolved_cases": CASES_SATURATION * 10, "years_experience": EXPERIENCE_SATURATION_YEARS * 2}
    assert _performance_score(base) == pytest.approx(_performance_score(mas))


def test_el_desempeno_crece_con_la_valoracion():
    bajo = _performance_score({"rating": 2.0, "resolved_cases": 10, "years_experience": 5})
    alto = _performance_score({"rating": 4.5, "resolved_cases": 10, "years_experience": 5})
    assert alto > bajo


def test_el_score_es_la_combinacion_declarada(modelo):
    recs = modelo.recommend(MEDICO, 5, lawyers=[_abogado(1), _abogado(2, rating=3.0), _abogado(3, areas=("Cardiología",))])
    assert recs
    for r in recs:
        esperado = W_CONTENT * r.content_score + W_PERFORMANCE * r.performance_score
        assert r.score == pytest.approx(esperado, abs=2e-4)
        assert r.model_used == MODEL_VERSION
        assert r.collaborative_score == 0.0


def test_el_ranking_viene_ordenado_y_respeta_top_k(modelo):
    corpus = [_abogado(i, rating=1 + i % 5, casos=i * 3) for i in range(1, 9)]
    recs = modelo.recommend(MEDICO, 3, lawyers=corpus)
    assert len(recs) == 3
    scores = [r.score for r in recs]
    assert scores == sorted(scores, reverse=True)


def test_top_k_mayor_que_el_corpus_devuelve_todo(modelo):
    assert len(modelo.recommend(MEDICO, 50, lawyers=[_abogado(1), _abogado(2)])) == 2


def test_mismo_perfil_mismo_resultado(modelo):
    corpus = [_abogado(i, rating=2 + i % 3) for i in range(1, 6)]
    a = [r.model_dump() for r in modelo.recommend(MEDICO, 5, lawyers=corpus)]
    b = [r.model_dump() for r in modelo.recommend(MEDICO, 5, lawyers=corpus)]
    assert a == b


def test_a_igual_contenido_gana_el_mejor_desempeno(modelo):
    flojo = _abogado(1, rating=2.0, casos=2, anios=1)
    solido = _abogado(2, rating=4.9, casos=50, anios=18)
    recs = modelo.recommend(MEDICO, 2, lawyers=[flojo, solido])
    assert recs[0].lawyer_id == solido["lawyer_id"]
    assert recs[0].content_score == pytest.approx(recs[1].content_score)


def test_las_areas_coincidentes_se_reportan_sin_importar_tildes(modelo):
    medico = DoctorProfile(specialty="Psiquiatria", case_text="")
    recs = modelo.recommend(medico, 2, lawyers=[_abogado(1, areas=("Psiquiatría", "Pediatría"))])
    assert recs[0].matched_specialties == ["Psiquiatría"]
    assert recs[0].reasons[0].startswith("Especialista en tu área médica")


def test_sin_coincidencia_de_area_no_se_afirma_especialidad(modelo):
    recs = modelo.recommend(MEDICO, 2, lawyers=[_abogado(1, areas=("Cardiología",))])
    assert recs[0].matched_specialties == []
    assert not any(r.startswith("Especialista en tu área") for r in recs[0].reasons)


def test_las_razones_salen_de_los_datos_del_abogado(modelo):
    rec = modelo.recommend(MEDICO, 1, lawyers=[_abogado(1, rating=4.5, casos=33, anios=12)])[0]
    assert "12 años de experiencia" in rec.reasons
    assert "Valoración 4.5/5" in rec.reasons
    assert "33 casos resueltos" in rec.reasons


def test_sin_casos_resueltos_no_se_menciona_esa_razon(modelo):
    rec = modelo.recommend(MEDICO, 1, lawyers=[_abogado(1, casos=0)])[0]
    assert not any("casos resueltos" in r for r in rec.reasons)


def test_los_terminos_que_explican_el_match_tienen_importancia_positiva(modelo):
    rec = modelo.recommend(MEDICO, 1, lawyers=[_abogado(1)])[0]
    assert rec.feature_importance
    assert all(t.importance > 0 for t in rec.feature_importance)
    assert len(rec.feature_importance) <= 3


def test_un_medico_sin_texto_ni_especialidad_no_rompe(modelo):
    recs = modelo.recommend(DoctorProfile(), 3, lawyers=[_abogado(1), _abogado(2)])
    assert len(recs) == 2
    assert all(r.content_score == 0.0 for r in recs)


def test_el_corpus_de_respaldo_responde_sin_enviar_abogados(modelo):
    recs = modelo.recommend(MEDICO, 5)
    assert 0 < len(recs) <= 5
    ids = {l["lawyer_id"] for l in modelo.lawyers}
    assert all(r.lawyer_id in ids for r in recs)


def test_el_endpoint_informa_pesos_y_version_en_cada_ejecucion():
    req = RecommendationsRequest(
        doctor_id="d1", doctor_profile=MEDICO, top_k=2,
        lawyers=[LawyerCorpusItem(**{k: v for k, v in _abogado(1).items()})],
    )
    res = main.recommendations(req)
    assert res.model_info["model"] == MODEL_VERSION
    assert res.model_info["weights"] == {"content": W_CONTENT, "performance": W_PERFORMANCE}
    assert res.model_info["corpus"] == "live"
    assert res.model_info["corpus_size"] == 1
