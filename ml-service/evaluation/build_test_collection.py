"""Construye DS-04: 20 consultas + pool de candidatos (docs/MATCHING-SPEC.md v2 §4).

Ejecuta las 8 variantes de ablacion (§5) sobre cada consulta, toma la union de
sus top-5, y reporta el pool real (candidatos unicos por consulta y total con
el 10% de duplicados simulado) contra el tope de 250 pares (§4.4).

NO genera el instrumento XLSX todavia -- eso queda para cuando se apruebe el
diseno de las consultas y el conteo del pool (Fase 2, primer checkpoint).

Reproducibilidad: RANDOM_STATE = 42 en todo lo estocastico (CLAUDE.md §7).

Uso:
    cd ml-service
    pip install -r evaluation/requirements-eval.txt
    python evaluation/build_test_collection.py
"""

from __future__ import annotations

import json
import math
import re
import sys
import unicodedata
from collections import Counter
from itertools import product
from pathlib import Path
from random import Random

if sys.stdout.encoding and sys.stdout.encoding.lower() != "utf-8":
    sys.stdout.reconfigure(encoding="utf-8")

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from sklearn.feature_extraction.text import TfidfVectorizer  # noqa: E402
from sklearn.metrics.pairwise import cosine_similarity  # noqa: E402
from rank_bm25 import BM25Okapi  # noqa: E402

from app.matching.model import SPANISH_STOPWORDS, _normalize  # noqa: E402

RANDOM_STATE = 42
CORPUS_PATH = Path(__file__).resolve().parents[1] / "data" / "reference" / "ds03_lawyers.json"
QUERIES_OUT_PATH = Path(__file__).resolve().parents[1] / "data" / "reference" / "ds04_queries.json"
# Decision final (post-verificacion contra corpus real, ver MATCHING-SPEC.md
# §4.2/§4.4): top-10 (protocolo v desactualizado) y top-5 (spec v2 original)
# excedian 250 pares incluso sin `random`. Unica combinacion que cabe: top-3
# con `random` excluida del pooling.
POOL_DEPTH = 3  # profundidad final -- ver §4.2 del spec
INCLUDE_RANDOM_IN_POOL = False  # `random` se calcula analiticamente, no se poolea (ver mas abajo)
MAX_PAIRS = 250  # tope duro del instrumento, incluidos duplicados (§4.4)
DUPLICATE_RATE = 0.10  # §4.5

URGENCY_LEVELS = ("baja", "media", "alta", "critica")
COMPLEXITY_LEVELS = ("baja", "media", "alta")

# ═══════════════════════════════════════════════════════════════════════════
# 1) LAS 20 CONSULTAS
#    6 de especialidades "alta" cobertura, 9 "media", 5 "escasa" (>= 3 exigidas
#    por el spec). Solo 7/20 (35%) nombran la especialidad literal en el texto
#    -- el resto la deja implicita en terminologia clinica -- por la misma
#    razon que en las bios: _doctor_text() ya concatena medical_specialty como
#    texto plano, nombrarla siempre en case_description duplicaria la señal.
# ═══════════════════════════════════════════════════════════════════════════

QUERIES = [
    {
        "query_id": "q01", "medical_specialty": "Cirugía General", "event_type": "Complicación quirúrgica",
        "perceived_urgency": "alta", "procedure_complexity": "alta", "names_specialty": False,
        "case_description": (
            "Paciente intervenido por colecistectomía que evolucionó con una lesión de la vía "
            "biliar detectada recién en el control postoperatorio, lo que obligó a una segunda "
            "intervención de urgencia. La familia ha solicitado una explicación formal por "
            "escrito y menciona la posibilidad de iniciar un reclamo. La documentación del "
            "procedimiento original está completa, pero no hay registro claro de la "
            "conversación previa sobre este riesgo específico. Se busca orientación sobre "
            "exposición legal y sobre cómo proceder con la respuesta a la familia."
        ),
    },
    {
        "query_id": "q02", "medical_specialty": "Ginecología y Obstetricia", "event_type": "Hemorragia post-parto",
        "perceived_urgency": "critica", "procedure_complexity": "alta", "names_specialty": False,
        "case_description": (
            "Gestante con parto instrumentado que presentó hemorragia post-parto severa, "
            "requirió transfusión de múltiples unidades y permaneció 48 horas en cuidados "
            "intensivos. El esposo ha exigido una reunión con la dirección médica y ha "
            "mencionado a un abogado externo que ya está revisando el caso. El consentimiento "
            "para el parto instrumentado está firmado, pero no detalla el riesgo de hemorragia "
            "mayor. Se requiere asesoría urgente sobre cómo documentar lo ocurrido y qué "
            "comunicar a la familia antes de esa reunión."
        ),
    },
    {
        "query_id": "q03", "medical_specialty": "Traumatología", "event_type": "Consolidación viciosa",
        "perceived_urgency": "media", "procedure_complexity": "media", "names_specialty": True,
        "case_description": (
            "Paciente con fractura de tobillo tratada de forma conservadora que terminó "
            "consolidando en mala posición, lo que ha requerido una cirugía correctiva meses "
            "después. El paciente sostiene que nunca se le explicó la alternativa quirúrgica "
            "inicial y que de haberlo sabido la habría preferido. Se trata de un caso de "
            "traumatología con documentación parcial: existe la radiografía inicial pero no una "
            "nota explícita sobre las opciones discutidas con el paciente en esa primera "
            "consulta. Se solicita evaluación del nivel de exposición legal."
        ),
    },
    {
        "query_id": "q04", "medical_specialty": "Medicina General", "event_type": "Falta de seguimiento",
        "perceived_urgency": "baja", "procedure_complexity": "baja", "names_specialty": False,
        "case_description": (
            "Consulta ambulatoria de rutina en la que el paciente refiere no haber recibido la "
            "llamada de seguimiento acordada tras un resultado de laboratorio limítrofe. No "
            "hubo daño ni complicación clínica, pero el paciente se ha quejado formalmente por "
            "la falta de comunicación y pide una explicación por escrito. La historia clínica "
            "registra el resultado y la indicación de seguimiento, pero no hay evidencia de que "
            "la llamada se haya realizado. Se pide orientación sobre cómo responder al reclamo "
            "sin reconocer una falta que no está clara."
        ),
    },
    {
        "query_id": "q05", "medical_specialty": "Pediatría", "event_type": "Error de medicación",
        "perceived_urgency": "media", "procedure_complexity": "media", "names_specialty": True,
        "case_description": (
            "Niño de corta edad que recibió una dosis de un medicamento pediátrico distinta a "
            "la indicada por error de transcripción en la orden médica, aunque sin "
            "consecuencias clínicas observables tras 24 horas de vigilancia. Los padres fueron "
            "informados de inmediato y solicitaron una reunión con el servicio de pediatría "
            "para entender qué falló. Existe un registro del error en el sistema interno de "
            "notificación de eventos, pero los padres no han visto ese documento. Se requiere "
            "orientación sobre qué información compartir y cómo formalizar la respuesta."
        ),
    },
    {
        "query_id": "q06", "medical_specialty": "Cirugía General", "event_type": "Resultado estético",
        "perceived_urgency": "baja", "procedure_complexity": "baja", "names_specialty": False,
        "case_description": (
            "Paciente disconforme con el resultado estético de una cicatriz tras una "
            "intervención abdominal programada, sin ninguna complicación clínica asociada. Ha "
            "enviado un correo solicitando una compensación económica y amenaza con dejar "
            "reseñas negativas si no recibe respuesta en la semana. El consentimiento firmado "
            "menciona de forma genérica la posibilidad de cicatrización visible, pero no "
            "incluye fotografías de referencia ni una conversación documentada sobre "
            "expectativas estéticas. Se pide una lectura rápida del riesgo real detrás de este "
            "reclamo."
        ),
    },
    {
        "query_id": "q07", "medical_specialty": "Cardiología", "event_type": "Complicación de procedimiento",
        "perceived_urgency": "alta", "procedure_complexity": "alta", "names_specialty": False,
        "case_description": (
            "Paciente sometido a un cateterismo que presentó una disección coronaria durante "
            "el procedimiento, resuelta con un stent adicional no previsto originalmente. La "
            "recuperación fue favorable, pero la familia cuestiona por qué no se les informó "
            "de inmediato sobre la complicación y se enteraron recién al día siguiente por "
            "otro miembro del equipo. El consentimiento cubre el procedimiento base pero no "
            "detalla explícitamente el riesgo de disección. Se solicita asesoría sobre la "
            "exposición derivada del retraso en la comunicación, más que del evento clínico en "
            "sí."
        ),
    },
    {
        "query_id": "q08", "medical_specialty": "Neurología", "event_type": "Diagnóstico tardío",
        "perceived_urgency": "media", "procedure_complexity": "alta", "names_specialty": True,
        "case_description": (
            "Paciente evaluado por un cuadro de cefalea intensa que fue dado de alta desde el "
            "servicio de neurología con diagnóstico de migraña, y que dos días después "
            "reingresó por un evento vascular mayor con secuelas motoras persistentes. La "
            "familia sostiene que los síntomas de alarma ya estaban presentes en la primera "
            "consulta y que no se solicitó una imagen a tiempo. La historia clínica de la "
            "primera visita es breve y no documenta explícitamente por qué se descartó un "
            "origen vascular. Se requiere una lectura del riesgo legal del caso."
        ),
    },
    {
        "query_id": "q09", "medical_specialty": "Oncología", "event_type": "Demora diagnóstica",
        "perceived_urgency": "alta", "procedure_complexity": "media", "names_specialty": False,
        "case_description": (
            "Paciente con un hallazgo radiológico sospechoso que permaneció sin biopsia "
            "confirmatoria durante casi dos meses por una serie de citas reprogramadas y "
            "pérdida de una orden médica en el sistema. El diagnóstico finalmente confirmado "
            "corresponde a un estadio más avanzado que el que hubiera correspondido de haberse "
            "actuado antes. El paciente y su familia han solicitado formalmente el expediente "
            "completo y mencionan estar evaluando asesoría legal externa. Se pide apoyo para "
            "reconstruir la cronología exacta de las reprogramaciones antes de responder a esa "
            "solicitud."
        ),
    },
    {
        "query_id": "q10", "medical_specialty": "Anestesiología", "event_type": "Evento adverso anestésico",
        "perceived_urgency": "critica", "procedure_complexity": "alta", "names_specialty": True,
        "case_description": (
            "Paciente que presentó un paro cardiorrespiratorio durante la inducción anestésica "
            "de un procedimiento programado, con reanimación exitosa pero con un periodo de "
            "hipoxia que generó preocupación por posibles secuelas cognitivas, aún en "
            "evaluación. Se trata de un evento adverso de anestesiología poco frecuente que ya "
            "fue reportado internamente. La familia exige una explicación inmediata y ha "
            "contratado asesoría legal independiente. El registro anestésico documenta los "
            "signos vitales minuto a minuto, pero el consentimiento no menciona "
            "específicamente el riesgo de complicación cardiorrespiratoria grave."
        ),
    },
    {
        "query_id": "q11", "medical_specialty": "Urología", "event_type": "Infección post-procedimiento",
        "perceived_urgency": "media", "procedure_complexity": "baja", "names_specialty": False,
        "case_description": (
            "Paciente sometido a un procedimiento urológico ambulatorio que desarrolló una "
            "infección urinaria complicada en los días posteriores, atribuible según el "
            "paciente a una falla en las indicaciones postoperatorias que recibió al alta. No "
            "hubo hospitalización adicional, pero sí un tratamiento antibiótico prolongado y "
            "varias consultas de urgencia no programadas. El paciente sostiene que las "
            "indicaciones escritas eran genéricas y no mencionaban señales de alarma "
            "específicas para este procedimiento. Se solicita una valoración de la solidez de "
            "ese reclamo."
        ),
    },
    {
        "query_id": "q12", "medical_specialty": "Gastroenterología", "event_type": "Perforación endoscópica",
        "perceived_urgency": "baja", "procedure_complexity": "media", "names_specialty": False,
        "case_description": (
            "Paciente que sufrió una perforación durante una colonoscopía diagnóstica, "
            "resuelta quirúrgicamente sin secuelas mayores tras una estadía hospitalaria "
            "breve. El paciente entiende que es una complicación reconocida del "
            "procedimiento, pero cuestiona que no se le haya explicado con suficiente "
            "claridad antes de firmar el consentimiento, que menciona el riesgo de forma muy "
            "general. No ha habido amenaza explícita de demanda, pero sí ha solicitado copia "
            "completa del expediente y una reunión con el especialista. Se pide una opinión "
            "preliminar sobre el nivel de riesgo."
        ),
    },
    {
        "query_id": "q13", "medical_specialty": "Psiquiatría", "event_type": "Alta prematura",
        "perceived_urgency": "media", "procedure_complexity": "baja", "names_specialty": True,
        "case_description": (
            "Paciente dado de alta del servicio de psiquiatría tras una hospitalización breve, "
            "que días después tuvo una crisis que requirió una nueva internación de "
            "emergencia. La familia cuestiona que el alta se haya dado sin una evaluación de "
            "riesgo suficientemente documentada, y ha solicitado por escrito una revisión del "
            "criterio clínico utilizado. La historia clínica registra la evaluación de alta, "
            "pero de forma breve y sin una escala de riesgo estandarizada aplicada. Se "
            "requiere orientación sobre cómo abordar esta solicitud sin reconocer una falla "
            "que no está establecida."
        ),
    },
    {
        "query_id": "q14", "medical_specialty": "Cardiología", "event_type": "Consentimiento incompleto",
        "perceived_urgency": "baja", "procedure_complexity": "media", "names_specialty": False,
        "case_description": (
            "Paciente al que se le indicó un nuevo tratamiento farmacológico para una "
            "arritmia sin que, según refiere, se le explicaran con claridad los efectos "
            "secundarios más relevantes ni las alternativas disponibles. No ha habido ninguna "
            "complicación clínica, pero el paciente se siente insatisfecho con el nivel de "
            "información recibido y ha pedido una copia de su historia clínica para revisarla "
            "con un tercero. El registro de la consulta es breve y no detalla explícitamente "
            "la conversación sobre riesgos del nuevo medicamento. Se pide una lectura "
            "preventiva del caso."
        ),
    },
    {
        "query_id": "q15", "medical_specialty": "Traumatología", "event_type": "Demora en atención",
        "perceived_urgency": "alta", "procedure_complexity": "media", "names_specialty": False,
        "case_description": (
            "Paciente politraumatizado por una caída que permaneció varias horas en el área de "
            "emergencias antes de recibir atención especializada, en un día de alta demanda "
            "del servicio. La familia reclama que esa demora agravó una lesión que inicialmente "
            "parecía menor y que terminó requiriendo cirugía. El registro de triaje documenta "
            "la hora de ingreso, pero no hay una nota clara que explique el motivo específico "
            "de la demora en la atención especializada. Se solicita una evaluación de la "
            "exposición legal derivada de este caso."
        ),
    },
    {
        "query_id": "q16", "medical_specialty": "Dermatología", "event_type": "Reacción adversa",
        "perceived_urgency": "baja", "procedure_complexity": "baja", "names_specialty": False,
        "case_description": (
            "Paciente que se sometió a un procedimiento estético ambulatorio menor y "
            "desarrolló una reacción cutánea localizada que se resolvió sin secuelas tras un "
            "tratamiento tópico. No hubo ninguna hospitalización ni complicación grave, pero "
            "el paciente ha expresado su malestar por no haber sido advertido de este riesgo "
            "específico antes del procedimiento, y ha pedido que se revise su expediente. El "
            "consentimiento firmado es un formato genérico que no detalla reacciones cutáneas "
            "particulares. Se pide una opinión rápida sobre si amerita alguna acción adicional."
        ),
    },
    {
        "query_id": "q17", "medical_specialty": "Endocrinología", "event_type": "Manejo tardío",
        "perceived_urgency": "media", "procedure_complexity": "media", "names_specialty": True,
        "case_description": (
            "Paciente con diabetes de larga data que desarrolló una complicación relacionada "
            "con un ajuste tardío de su tratamiento, tras varias consultas de endocrinología "
            "en las que, según refiere, no se revisaron a fondo sus controles metabólicos "
            "recientes. La familia ha solicitado una explicación sobre por qué no se "
            "intensificó el manejo antes, y menciona que estaría dispuesta a buscar una "
            "segunda opinión legal si no queda satisfecha. La historia clínica muestra los "
            "controles registrados, pero no un análisis explícito de la tendencia entre "
            "consultas. Se solicita una lectura del riesgo del caso."
        ),
    },
    {
        "query_id": "q18", "medical_specialty": "Nefrología", "event_type": "Infección asociada a catéter",
        "perceived_urgency": "alta", "procedure_complexity": "alta", "names_specialty": False,
        "case_description": (
            "Paciente en tratamiento de diálisis que desarrolló una infección asociada al "
            "catéter que derivó en una hospitalización prolongada y un cambio de acceso "
            "vascular de urgencia. La familia sostiene que los signos iniciales de la "
            "infección no fueron tomados con suficiente seriedad en las primeras consultas de "
            "control. Existe un registro de esas consultas, pero las notas son breves y no "
            "documentan explícitamente por qué se descartó un origen infeccioso en ese "
            "momento. Dada la complejidad del caso y la gravedad del desenlace, se solicita "
            "una evaluación legal detallada."
        ),
    },
    {
        "query_id": "q19", "medical_specialty": "Reumatología", "event_type": "Retraso diagnóstico",
        "perceived_urgency": "baja", "procedure_complexity": "media", "names_specialty": True,
        "case_description": (
            "Paciente con síntomas articulares persistentes que fue derivado a reumatología "
            "después de varios meses de consultas en otras especialidades sin un diagnóstico "
            "claro, y que finalmente fue diagnosticado con una enfermedad autoinmune en una "
            "etapa ya establecida. El paciente cuestiona si ese tiempo pudo haberse acortado "
            "y solicita una revisión de su historial completo. No hay evidencia de un error "
            "puntual, sino de una serie de consultas en las que el cuadro no terminaba de "
            "encajar en un diagnóstico. Se pide una opinión sobre si existe algún fundamento "
            "para un reclamo formal."
        ),
    },
    {
        "query_id": "q20", "medical_specialty": "Hematología", "event_type": "Interconsulta pendiente",
        "perceived_urgency": "media", "procedure_complexity": "media", "names_specialty": False,
        "case_description": (
            "Paciente con resultados de laboratorio compatibles con un trastorno hematológico "
            "que tardaron en derivarse a la especialidad correspondiente debido a una "
            "interconsulta que quedó pendiente en el sistema durante varias semanas. El "
            "diagnóstico finalmente se confirmó y el tratamiento se inició, pero con un "
            "retraso que el paciente considera evitable. No ha habido una complicación "
            "clínica grave hasta el momento, pero el paciente ha solicitado una explicación "
            "formal sobre lo ocurrido con la interconsulta. Se pide una valoración de la "
            "exposición legal antes de responder por escrito."
        ),
    },
]

# Especialidades "escasas" en el corpus DS-03 (1-2 abogados) -- para marcar
# cuales de las 20 consultas son el "caso adverso" exigido por el spec (>=3).
SCARCE_AREAS = {
    "Dermatología", "Endocrinología", "Oftalmología", "Neumología",
    "Nefrología", "Reumatología", "Infectología", "Hematología",
}


# ═══════════════════════════════════════════════════════════════════════════
# 2) LAS 8 VARIANTES DE ABLACIÓN (§5) -- solo para construir el pool a top-5.
#    El cálculo de métricas (Precision@3, MRR, nDCG, bootstrap, etc.) es
#    Fase 3 (run_ablation.py), no esto.
# ═══════════════════════════════════════════════════════════════════════════

_WORD_RE = re.compile(r"[a-záéíóúñ]+", re.IGNORECASE)


def _tokenize(text: str) -> list[str]:
    normalized = _normalize(text)
    tokens = _WORD_RE.findall(normalized)
    stop = set(SPANISH_STOPWORDS)
    return [t for t in tokens if t not in stop and len(t) > 1]


def _lawyer_text_full(l: dict) -> str:
    return " ".join(l["specialties"]) + " " + " ".join(l["medical_areas"]) + " " + l["biography"]


def _doctor_text_full(q: dict) -> str:
    # Aproxima _doctor_text() de produccion (specialty + sub_specialties +
    # hospital + case_text); sin perfil de medico real, specialty + descripcion.
    return q["medical_specialty"] + " " + q["case_description"]


def _performance_score(l: dict) -> float:
    rating_norm = min(float(l["rating"]) / 5.0, 1.0)
    cases = max(int(l["resolved_cases"]), 0)
    cases_norm = min(math.log1p(cases) / math.log1p(60), 1.0)
    years = max(int(l["years_experience"]), 0)
    experience_norm = min(years / 20, 1.0)
    return 0.50 * rating_norm + 0.30 * cases_norm + 0.20 * experience_norm


def rank_random(query: dict, lawyers: list[dict], query_idx: int, depth: int = POOL_DEPTH) -> list[str]:
    """Se conserva la funcion (util como referencia/sanity-check), pero ya NO
    se incluye en pool_for_query por defecto -- ver expected_random_precision."""
    rng = Random(RANDOM_STATE + query_idx)  # semilla distinta pero reproducible por consulta
    ids = [l["lawyer_id"] for l in lawyers]
    rng.shuffle(ids)
    return ids[:depth]


def expected_random_precision(k_relevant: int, n_available: int) -> float:
    """Precision@k esperada de un ranking aleatorio, calculada analiticamente
    a partir de los qrels (Fase 3, tras la adjudicacion) -- NO por pooling.

    Si hay `k_relevant` abogados relevantes (qrel > 0) entre los `n_available`
    que pasan el filtro duro de disponibilidad para una consulta, la
    probabilidad de que una posicion cualquiera de un orden aleatorio sea
    relevante es k_relevant/n_available; por linealidad de la esperanza, ese
    mismo valor es la Precision@k esperada para cualquier profundidad k.
    Exacta, insesgada, no consume juicios humanos.

    Uso previsto: run_ablation.py (Fase 3), una vez existan ds04_qrels.csv.
    Aqui solo se documenta la formula -- no hay qrels todavia.
    """
    if n_available == 0:
        return 0.0
    return k_relevant / n_available


def rank_area_match(query: dict, lawyers: list[dict], depth: int = POOL_DEPTH) -> list[str]:
    target = _normalize(query["medical_specialty"])
    scored = []
    for l in lawyers:
        match = any(_normalize(a) == target for a in l["medical_areas"])
        # desempate deterministico: rating desc, casos desc, id asc
        scored.append((1 if match else 0, float(l["rating"]), int(l["resolved_cases"]), l["lawyer_id"]))
    scored.sort(key=lambda x: (-x[0], -x[1], -x[2], x[3]))
    return [s[3] for s in scored[:depth]]


def _fit_tfidf(corpus_texts: list[str]) -> TfidfVectorizer:
    vec = TfidfVectorizer(stop_words=SPANISH_STOPWORDS)
    vec.fit(corpus_texts)
    return vec


def rank_tfidf(query_text: str, lawyer_texts: list[str], lawyer_ids: list[str], depth: int = POOL_DEPTH) -> list[str]:
    vec = _fit_tfidf([_normalize(t) for t in lawyer_texts])
    matrix = vec.transform([_normalize(t) for t in lawyer_texts])
    qvec = vec.transform([_normalize(query_text)])
    sims = cosine_similarity(qvec, matrix)[0]
    ranked = sorted(zip(lawyer_ids, sims), key=lambda x: -x[1])
    return [lid for lid, _ in ranked[:depth]]


def rank_performance_only(lawyers: list[dict], depth: int = POOL_DEPTH) -> list[str]:
    scored = [(l["lawyer_id"], _performance_score(l)) for l in lawyers]
    scored.sort(key=lambda x: -x[1])
    return [lid for lid, _ in scored[:depth]]


def rank_composite(query_text: str, lawyers: list[dict], alpha: float, depth: int = POOL_DEPTH) -> list[str]:
    lawyer_texts = [_lawyer_text_full(l) for l in lawyers]
    lawyer_ids = [l["lawyer_id"] for l in lawyers]
    vec = _fit_tfidf([_normalize(t) for t in lawyer_texts])
    matrix = vec.transform([_normalize(t) for t in lawyer_texts])
    qvec = vec.transform([_normalize(query_text)])
    sims = cosine_similarity(qvec, matrix)[0]
    scored = []
    for l, sim in zip(lawyers, sims):
        perf = _performance_score(l)
        scored.append((l["lawyer_id"], alpha * float(sim) + (1 - alpha) * perf))
    scored.sort(key=lambda x: -x[1])
    return [lid for lid, _ in scored[:depth]]


def rank_bm25(query_text: str, lawyers: list[dict], depth: int = POOL_DEPTH) -> list[str]:
    lawyer_ids = [l["lawyer_id"] for l in lawyers]
    corpus_tokens = [_tokenize(_lawyer_text_full(l)) for l in lawyers]
    bm25 = BM25Okapi(corpus_tokens)
    query_tokens = _tokenize(query_text)
    scores = bm25.get_scores(query_tokens)
    ranked = sorted(zip(lawyer_ids, scores), key=lambda x: -x[1])
    return [lid for lid, _ in ranked[:depth]]


def pool_for_query(
    query: dict, lawyers: list[dict], query_idx: int, depth: int = POOL_DEPTH,
    include_random: bool = INCLUDE_RANDOM_IN_POOL,
) -> dict[str, list[str]]:
    """Retorna {variante: [lawyer_ids top-N]} -- NO aplica blinding, es para
    construir/inspeccionar el pool, no el instrumento final."""
    query_text_full = _doctor_text_full(query)
    lawyer_texts_full = [_lawyer_text_full(l) for l in lawyers]
    lawyer_ids = [l["lawyer_id"] for l in lawyers]
    bio_texts = [l["biography"] for l in lawyers]

    result = {
        "area-match": rank_area_match(query, lawyers, depth=depth),
        "tfidf-full": rank_tfidf(query_text_full, lawyer_texts_full, lawyer_ids, depth=depth),
        "bio-only": rank_tfidf(query["case_description"], bio_texts, lawyer_ids, depth=depth),
        "performance-only": rank_performance_only(lawyers, depth=depth),
        "composite-070": rank_composite(query_text_full, lawyers, alpha=0.70, depth=depth),
        # composite-sweep: para efectos de POOLING (no de Fase 3) se usa un
        # alpha distinto de 0.70 (que ya cubre composite-070) para no ser
        # redundante -- se elige el punto medio del barrido, alpha=0.50.
        # Fase 3 sí ejecuta el barrido completo [0.0, 1.0] paso 0.1 para las
        # metricas de esa fase; esto es solo para no inflar el pool con 11
        # listas casi identicas a composite-070.
        "composite-sweep@0.5": rank_composite(query_text_full, lawyers, alpha=0.50, depth=depth),
        "bm25": rank_bm25(query_text_full, lawyers, depth=depth),
    }
    if include_random:
        result["random"] = rank_random(query, lawyers, query_idx, depth=depth)
    return result


def main() -> None:
    lawyers = json.loads(CORPUS_PATH.read_text(encoding="utf-8"))
    name_by_id = {l["lawyer_id"]: l["full_name"] for l in lawyers}

    # ── Validaciones de diseño de las 20 consultas ──────────────────────
    assert len(QUERIES) == 20, f"Se esperaban 20 consultas, hay {len(QUERIES)}"
    scarce_count = sum(1 for q in QUERIES if q["medical_specialty"] in SCARCE_AREAS)
    assert scarce_count >= 3, f"Se requieren >=3 consultas de área escasa, hay {scarce_count}"

    for q in QUERIES:
        n = len(q["case_description"].split())
        assert 60 <= n <= 120, f"{q['query_id']}: {n} palabras, fuera de 60-120"

    urgency_dist = Counter(q["perceived_urgency"] for q in QUERIES)
    complexity_dist = Counter(q["procedure_complexity"] for q in QUERIES)
    explicit_count = sum(1 for q in QUERIES if q["names_specialty"])

    print("=== Validación de diseño — 20 consultas ===\n")
    print(f"Consultas de área escasa (>= 3 requeridas): {scarce_count}")
    print(f"  {[q['query_id'] + ':' + q['medical_specialty'] for q in QUERIES if q['medical_specialty'] in SCARCE_AREAS]}")
    print(f"\nDistribución de urgencia: {dict(urgency_dist)}")
    print(f"Distribución de complejidad: {dict(complexity_dist)}")
    print(f"\nEspecialidad nombrada explícitamente en el texto: {explicit_count}/20 ({explicit_count/20*100:.0f}%)")

    # ── Pool final: 7 variantes (sin random), top-3, sobre 45 abogados ─────
    print(f"\n=== Construyendo el pool (7 variantes × top-{POOL_DEPTH}, random excluida, sobre 45 abogados) ===\n")
    pools = {}
    total_unique = 0
    for idx, q in enumerate(QUERIES):
        variant_lists = pool_for_query(q, lawyers, idx)
        union = set()
        for ids in variant_lists.values():
            union.update(ids)
        pools[q["query_id"]] = {"variants": variant_lists, "union": sorted(union)}
        total_unique += len(union)
        print(f"  {q['query_id']} ({q['medical_specialty']:28s}): {len(union)} candidatos únicos")

    avg_unique = total_unique / len(QUERIES)
    duplicates = round(total_unique * DUPLICATE_RATE)
    grand_total = total_unique + duplicates

    print(f"\nTotal de pares únicos (sin duplicados): {total_unique}")
    print(f"Promedio de candidatos únicos por consulta: {avg_unique:.1f}")
    print(f"Duplicados simulados al {DUPLICATE_RATE*100:.0f}%: {duplicates}")
    print(f"TOTAL con duplicados: {grand_total}  (tope: {MAX_PAIRS})")
    print(f"{'Dentro del tope.' if grand_total <= MAX_PAIRS else '⚠ EXCEDE el tope.'}")

    # ── Composición del pool: por etiqueta legal y por área médica ─────
    corpus_legal_pct = {}
    corpus_area_pct = {}
    n_corpus = len(lawyers)
    for l in lawyers:
        for s in l["specialties"]:
            corpus_legal_pct[s] = corpus_legal_pct.get(s, 0) + 1
        for a in l["medical_areas"]:
            corpus_area_pct[a] = corpus_area_pct.get(a, 0) + 1
    corpus_legal_pct = {k: v / n_corpus * 100 for k, v in corpus_legal_pct.items()}
    corpus_area_pct = {k: v / n_corpus * 100 for k, v in corpus_area_pct.items()}

    # unión de TODOS los pools (perfiles distintos que aparecen en al menos una consulta)
    all_pool_ids = set()
    for p in pools.values():
        all_pool_ids.update(p["union"])
    lawyer_by_id = {l["lawyer_id"]: l for l in lawyers}
    n_pool = len(all_pool_ids)

    pool_legal_count = Counter()
    pool_area_count = Counter()
    for lid in all_pool_ids:
        l = lawyer_by_id[lid]
        for s in l["specialties"]:
            pool_legal_count[s] += 1
        for a in l["medical_areas"]:
            pool_area_count[a] += 1

    print(f"\n=== Composición del pool por etiqueta legal ({n_pool} perfiles únicos en algún pool) ===")
    print(f"{'etiqueta':32s} {'% corpus (45)':>14s} {'% pool':>10s} {'diferencia':>12s}")
    for label in sorted(corpus_legal_pct, key=lambda k: -corpus_legal_pct[k]):
        corpus_pct = corpus_legal_pct[label]
        pool_pct = pool_legal_count.get(label, 0) / n_pool * 100
        diff = pool_pct - corpus_pct
        flag = "  <-- sobrerrepresentada" if diff > 10 else ("  <-- subrepresentada" if diff < -10 else "")
        print(f"{label:32s} {corpus_pct:13.1f}% {pool_pct:9.1f}% {diff:+11.1f}pp{flag}")

    print(f"\n=== Composición del pool por área médica ===")
    print(f"{'área':28s} {'% corpus (45)':>14s} {'% pool':>10s} {'diferencia':>12s}")
    for area in sorted(corpus_area_pct, key=lambda k: -corpus_area_pct[k]):
        corpus_pct = corpus_area_pct[area]
        pool_pct = pool_area_count.get(area, 0) / n_pool * 100
        diff = pool_pct - corpus_pct
        flag = "  <-- sobrerrepresentada" if diff > 10 else ("  <-- subrepresentada" if diff < -10 else "")
        print(f"{area:28s} {corpus_pct:13.1f}% {pool_pct:9.1f}% {diff:+11.1f}pp{flag}")

    # ── Verificación: consultas que mencionan "consentimiento" y si los
    #    abogados con esa etiqueta legal quedan sobrerrepresentados ahí ──
    consent_queries = [q for q in QUERIES if "consentimiento" in _normalize(q["case_description"])]
    print(f"\n=== Verificación: consultas que mencionan 'consentimiento' ===")
    print(f"Cantidad: {len(consent_queries)}/20")
    print(f"  {[q['query_id'] for q in consent_queries]}")

    consent_pool_ids = set()
    for q in consent_queries:
        consent_pool_ids.update(pools[q["query_id"]]["union"])
    n_consent_pool = len(consent_pool_ids)
    consent_tagged = sum(
        1 for lid in consent_pool_ids if "Consentimiento Informado" in lawyer_by_id[lid]["specialties"]
    )
    consent_pct_in_subpool = consent_tagged / n_consent_pool * 100 if n_consent_pool else 0
    corpus_consent_pct = corpus_legal_pct.get("Consentimiento Informado", 0)
    print(f"\nPerfiles únicos en el pool de esas {len(consent_queries)} consultas: {n_consent_pool}")
    print(f"  De ellos, con etiqueta 'Consentimiento Informado': {consent_tagged} ({consent_pct_in_subpool:.1f}%)")
    print(f"  Baseline en el corpus completo (45): {corpus_consent_pct:.1f}%")
    diff = consent_pct_in_subpool - corpus_consent_pct
    if diff > 10:
        print(f"  SOBRERREPRESENTADA por {diff:+.1f}pp frente a su baseline en el corpus.")
    elif diff < -10:
        print(f"  SUBREPRESENTADA por {diff:+.1f}pp frente a su baseline en el corpus.")
    else:
        print(f"  Dentro de rango esperado ({diff:+.1f}pp) -- no hay sobrerrepresentación clara.")

    # ── Salida: ds04_queries.json (sin el pool -- eso es otro artefacto) ──
    out = [{k: v for k, v in q.items()} for q in QUERIES]
    QUERIES_OUT_PATH.parent.mkdir(parents=True, exist_ok=True)
    QUERIES_OUT_PATH.write_text(json.dumps(out, indent=2, ensure_ascii=False), encoding="utf-8")
    print(f"\nEscrito: {QUERIES_OUT_PATH.resolve()}")

    return pools, lawyers, name_by_id


if __name__ == "__main__":
    main()
