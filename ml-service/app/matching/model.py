"""
Matching medico-abogado por contenido (HU-31/32): TF-IDF + similitud coseno
entre el perfil del medico (especialidad, sub-especialidades, hospital) y el
corpus de perfiles de abogados (especialidades legales, areas medicas, bio).

El corpus vive en lawyers_corpus.json. Sus `lawyer_id` deben coincidir con
`profiles.id` / `lawyer_profiles.user_id` en la base de datos para que
MatchingService pueda resolver el perfil completo del abogado.
"""

import json
import unicodedata
from pathlib import Path
from typing import Optional

from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.metrics.pairwise import cosine_similarity

from app.schemas import DoctorProfile, FeatureImportance, LawyerRecommendation

CORPUS_PATH = Path(__file__).parent / "lawyers_corpus.json"
MODEL_VERSION = "tfidf-cosine-v1"

# Stopwords minimas en espanol (sklearn no incluye una lista nativa) para que
# articulos/preposiciones no dominen la explicacion de feature_importance.
SPANISH_STOPWORDS = [
    "de", "la", "el", "en", "y", "a", "los", "las", "un", "una", "con",
    "para", "por", "su", "sus", "del", "al", "que", "se", "es",
]


def _normalize(text: str) -> str:
    text = unicodedata.normalize("NFKD", text.lower())
    return "".join(c for c in text if not unicodedata.combining(c))


def _lawyer_text(lawyer: dict) -> str:
    return " ".join([
        " ".join(lawyer.get("specialties", [])),
        " ".join(lawyer.get("medical_areas", [])),
        lawyer.get("bio", ""),
    ])


def _doctor_text(profile: DoctorProfile) -> str:
    return " ".join([
        profile.specialty or "",
        " ".join(profile.sub_specialties or []),
        profile.hospital or "",
    ])


class MatchingModel:
    def __init__(self, corpus_path: Path = CORPUS_PATH):
        self.lawyers: list[dict] = json.loads(corpus_path.read_text(encoding="utf-8"))
        corpus_texts = [_normalize(_lawyer_text(l)) for l in self.lawyers]

        self.vectorizer = TfidfVectorizer(stop_words=SPANISH_STOPWORDS)
        self.lawyer_matrix = self.vectorizer.fit_transform(corpus_texts)
        self.feature_names = self.vectorizer.get_feature_names_out()

    def _matched_specialties(self, profile: DoctorProfile, lawyer: dict) -> list[str]:
        doctor_areas = {_normalize(s) for s in [profile.specialty or "", *(profile.sub_specialties or [])]}
        matched = []
        for area in lawyer.get("medical_areas", []):
            if _normalize(area) in doctor_areas:
                matched.append(area)
        return matched

    def _top_terms(self, doctor_vec, lawyer_vec, top_n: int = 3) -> list[FeatureImportance]:
        # Contribucion termino-a-termino al producto punto (proxy de "por que" hicieron match).
        doctor_arr = doctor_vec.toarray()[0]
        lawyer_arr = lawyer_vec.toarray()[0]
        contributions = doctor_arr * lawyer_arr
        top_idx = contributions.argsort()[::-1][:top_n]

        terms = []
        for idx in top_idx:
            if contributions[idx] <= 0:
                continue
            terms.append(FeatureImportance(
                feature=self.feature_names[idx],
                importance=round(float(contributions[idx]), 4),
                description=f"Coincidencia en el termino '{self.feature_names[idx]}'",
            ))
        return terms

    def _reasons(self, lawyer: dict, matched: list[str]) -> list[str]:
        reasons = []
        if matched:
            reasons.append(f"Especialista en tu área médica ({', '.join(matched)})")
        reasons.append(f"{lawyer['years_experience']} años de experiencia")
        reasons.append(f"Valoración {lawyer['rating']:.1f}/5")
        if lawyer.get("resolved_cases"):
            reasons.append(f"{lawyer['resolved_cases']} casos resueltos")
        return reasons

    def recommend(self, profile: DoctorProfile, top_k: int = 10) -> list[LawyerRecommendation]:
        doctor_text = _normalize(_doctor_text(profile))
        doctor_vec = self.vectorizer.transform([doctor_text])

        similarities = cosine_similarity(doctor_vec, self.lawyer_matrix)[0]

        ranked = sorted(
            zip(self.lawyers, similarities, range(len(self.lawyers))),
            key=lambda x: x[1],
            reverse=True,
        )[:top_k]

        recommendations = []
        for lawyer, sim, idx in ranked:
            matched = self._matched_specialties(profile, lawyer)
            lawyer_vec = self.lawyer_matrix[idx]
            recommendations.append(LawyerRecommendation(
                lawyer_id=lawyer["lawyer_id"],
                score=round(float(sim), 4),
                content_score=round(float(sim), 4),
                collaborative_score=0.0,
                matched_specialties=matched,
                model_used=MODEL_VERSION,
                feature_importance=self._top_terms(doctor_vec, lawyer_vec),
                reasons=self._reasons(lawyer, matched),
            ))
        return recommendations


_instance: Optional[MatchingModel] = None


def get_matching_model() -> MatchingModel:
    global _instance
    if _instance is None:
        _instance = MatchingModel()
    return _instance
