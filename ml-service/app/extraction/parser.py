"""Obtiene los campos del formulario de caso a partir de las líneas de texto del informe.

Reglas deterministas y auditables (sin modelo entrenado, sin aleatoriedad). Cada campo
sale con su origen:
  - "detected": el informe lo dice de forma explícita (p. ej. "Especialidad: Cirugía General").
  - "inferred": se deduce por palabras clave; el médico debe confirmarlo.
  - "missing":  el informe no lo permite; el médico lo completa en el formulario.

Si un dato no está claro, se deja en "missing": nunca se adivina un valor que alimenta
al clasificador de riesgo (consentimiento, documentación, quejas, complejidad).
La urgencia percibida NO se extrae jamás: es el juicio propio del médico.
"""

import re
import unicodedata
from datetime import date

from app.extraction import config as cfg
from app.extraction.pii import redact
from app.risk.baselines import SPECIALTY_BASELINE

DETECTED, INFERRED, MISSING = "detected", "inferred", "missing"

FIELD_NAMES = (
    "title", "description", "medicalSpecialty", "eventType", "procedureComplexity",
    "eventDate", "ageReference", "documentationComplete", "informedConsent",
    "hasPriorComplaints", "notes", "relevantFactors",
)


# ── Normalización ────────────────────────────────────────────────────────────

def fold(text: str) -> str:
    """Minúsculas y sin tildes (el OCR suele perderlas y el médico las escribe o no)."""
    decomposed = unicodedata.normalize("NFKD", text)
    return "".join(c for c in decomposed if not unicodedata.combining(c)).lower()


def squash(text: str) -> str:
    """Solo letras y dígitos: tolera "Fechadelevento" y "Fecha del evento" por igual."""
    return re.sub(r"[^a-z0-9]", "", fold(text))


def _has_term(folded_text: str, term: str) -> bool:
    """Coincidencia al inicio de palabra: "uci" no debe encontrarse dentro de "reducir"."""
    return re.search(r"(?<![a-z0-9])" + re.escape(term), folded_text) is not None


def _count_terms(folded_text: str, terms: tuple[str, ...]) -> int:
    return sum(len(re.findall(r"(?<![a-z0-9])" + re.escape(t), folded_text)) for t in terms)


_LABEL_TO_FIELD = {squash(label): field for field, labels in cfg.LABELS.items() for label in labels}
_CANONICAL_SPECIALTY = {squash(name): name for name in SPECIALTY_BASELINE}
_CANONICAL_EVENT = {squash(name): name for name in cfg.EVENT_TYPE_KEYWORDS}


def _split_label(line: str) -> tuple[str, str] | None:
    """Si la línea es "Etiqueta: valor" con una etiqueta conocida, devuelve (campo, valor)."""
    if ":" not in line:
        return None
    raw_label, _, value = line.partition(":")
    field = _LABEL_TO_FIELD.get(squash(raw_label))
    if field is None or len(raw_label) > 45:
        return None
    return field, value.strip()


# ── Valores booleanos ────────────────────────────────────────────────────────

def _interpret_bool(value: str) -> bool | None:
    """True/False si el valor es inequívoco; None si no se puede decidir."""
    words = re.findall(r"[a-z]+", fold(value))
    if not words:
        return None
    if any(w in cfg.NEGATIVE_WORDS for w in words):
        return False
    if any(w in cfg.POSITIVE_WORDS for w in words):
        return True
    return None


_NEGATION_BEFORE = re.compile(r"(?:\b(?:sin|no|nunca)\b|ausencia de|falta de|carece de)[^.;]{0,35}$")
_NEGATION_AFTER = re.compile(r"^[^.;]{0,25}(?:no firm|no consta|ausente|pendiente|sin firmar|no se obtuvo)")
_AFFIRMATIVE = re.compile(r"firmad|obtuv|otorg|suscrit|registrad|consta")


def _consent_from_text(folded: str) -> bool | None:
    match = re.search(r"consentimiento\s*informado", folded)
    if not match:
        return None
    before = folded[max(0, match.start() - 45):match.start()]
    after = folded[match.end():match.end() + 60]
    if _NEGATION_BEFORE.search(before) or _NEGATION_AFTER.search(after):
        return False
    if _AFFIRMATIVE.search(before + " " + after):
        return True
    return None


def _documentation_from_text(folded: str) -> bool | None:
    if re.search(r"(?:historia clinica|documentacion|expediente)\s*(?:clinica\s*)?incomplet", folded) or re.search(
        r"faltan?\s+(?:documentos|examenes|registros)|sin\s+historia\s+clinica", folded
    ):
        return False
    if re.search(r"(?:historia clinica|documentacion|expediente)\s*(?:clinica\s*)?complet", folded):
        return True
    return None


def _complaints_from_text(folded: str) -> bool | None:
    if re.search(
        r"sin\s+(?:quejas|reclamos|denuncias|antecedentes\s+de\s+(?:quejas|reclamos))|no\s+registra\s+(?:quejas|reclamos)"
        r"|ninguna\s+(?:queja|denuncia)",
        folded,
    ):
        return False
    if re.search(r"(?:quejas?|reclamos?|denuncias?)\s+previ[ao]s?|antecedentes?\s+de\s+(?:queja|reclamo|denuncia)", folded):
        return True
    return None


# ── Fechas y edad ────────────────────────────────────────────────────────────

_MONTHS_RE = "|".join(cfg.MONTHS)
_DATE_PATTERNS = (
    (re.compile(r"(?<!\d)(\d{1,2})\s*[/\-.]\s*(\d{1,2})\s*[/\-.]\s*(\d{4})(?!\d)"), "dmy"),
    (re.compile(r"(?<!\d)(\d{4})\s*-\s*(\d{1,2})\s*-\s*(\d{1,2})(?!\d)"), "ymd"),
    (re.compile(rf"(?<!\d)(\d{{1,2}})\s*de\s*({_MONTHS_RE})\s*(?:de|del)?\s*(\d{{4}})"), "dmonthy"),
)
MIN_EVENT_YEAR = 1990


def _parse_date(text: str, today: date | None = None) -> str | None:
    today = today or date.today()
    folded = fold(text)
    for pattern, kind in _DATE_PATTERNS:
        for m in pattern.finditer(folded):
            try:
                if kind == "dmy":
                    d, mo, y = int(m.group(1)), int(m.group(2)), int(m.group(3))
                elif kind == "ymd":
                    y, mo, d = int(m.group(1)), int(m.group(2)), int(m.group(3))
                else:
                    d, mo, y = int(m.group(1)), cfg.MONTHS[m.group(2)], int(m.group(3))
                parsed = date(y, mo, d)
            except ValueError:
                continue
            if MIN_EVENT_YEAR <= parsed.year and parsed <= today:
                return parsed.isoformat()
    return None


_EXPERIENCE_AFTER = r"(?!\s*de\s*(?:experiencia|servicio|ejercicio|carrera|antiguedad|trayectoria))"
_AGE_PATTERNS = (
    re.compile(r"(?:paciente|edad|masculino|femenino|varon|mujer|hombre)\D{0,25}?(\d{1,3})\s*anos" + _EXPERIENCE_AFTER),
    re.compile(r"(\d{1,3})\s*anos\s*de\s*edad"),
    re.compile(r"(?<![\d/])(\d{1,3})\s*anos" + _EXPERIENCE_AFTER),
)


def _age_from_text(folded: str) -> int | None:
    for pattern in _AGE_PATTERNS:
        for m in pattern.finditer(folded):
            age = int(m.group(1))
            if cfg.MIN_AGE <= age <= cfg.MAX_AGE:
                return age
    return None


# ── Clasificación por palabras clave ─────────────────────────────────────────

def _best_by_keywords(folded: str, table: dict[str, tuple[str, ...]]) -> str | None:
    best_name, best_hits = None, 0
    for name, terms in table.items():  # el orden del diccionario desempata de forma estable
        hits = _count_terms(folded, terms)
        if hits > best_hits:
            best_name, best_hits = name, hits
    return best_name


def _canonical(value: str, canonical: dict[str, str], keywords: dict[str, tuple[str, ...]]) -> str | None:
    squashed = squash(value)
    if squashed in canonical:
        return canonical[squashed]
    for key, name in canonical.items():
        if key and key in squashed:
            return name
    return _best_by_keywords(fold(value), keywords)


def _complexity(labeled_value: str | None, procedure: str | None, folded_all: str) -> tuple[str | None, str]:
    if labeled_value:
        word = next((w for w in ("alta", "media", "baja") if _has_term(fold(labeled_value), w)), None)
        if word:
            return word, DETECTED
    scope = fold(procedure) + " " + folded_all if procedure else folded_all
    high = _count_terms(scope, cfg.COMPLEXITY_HIGH)
    low = _count_terms(scope, cfg.COMPLEXITY_LOW)
    if high and not low:
        return "alta", INFERRED
    if low and not high:
        return "baja", INFERRED
    if high and low:
        return "media", INFERRED
    return None, MISSING


# ── Resultado ────────────────────────────────────────────────────────────────

def _field(value, source: str) -> dict:
    return {"value": value, "source": source if value not in (None, "", []) else MISSING}


def _clean(text: str, limit: int) -> str:
    cleaned = re.sub(r"\s+", " ", text).strip(" \t-–—:;")
    return cleaned[:limit].rstrip()


def parse_report(lines: list[str], today: date | None = None) -> dict:
    """Convierte las líneas del informe en {campo: {value, source}} + datos omitidos.

    Devuelve además `piiRedacted`: tipos de dato personal que se omitieron (nunca sus valores).
    """
    labeled: dict[str, str] = {}
    free_lines: list[str] = []
    description_lines: list[str] = []
    in_description = False
    heading_skipped = False

    for line in lines:
        split = _split_label(line)
        if split:
            field, value = split
            in_description = field == "description"
            if field == "description":
                if value:
                    description_lines.append(value)
            elif field not in labeled or not labeled[field]:
                labeled[field] = value
            continue
        if not heading_skipped and ":" not in line and re.match(r"(?i)\s*(informe|reporte)\b", fold(line)):
            heading_skipped = True
            continue
        (description_lines if in_description else free_lines).append(line)

    full_text = " ".join(lines)
    folded_all = fold(full_text)

    description_text = " ".join(description_lines) if description_lines else " ".join(free_lines)
    description_source = DETECTED if description_lines else INFERRED

    # Título
    if labeled.get("title"):
        title, title_src = _clean(labeled["title"], cfg.MAX_TITLE_CHARS), DETECTED
    elif free_lines and len(free_lines[0]) <= cfg.MAX_TITLE_CHARS and not description_lines:
        title, title_src = _clean(free_lines[0], cfg.MAX_TITLE_CHARS), INFERRED
    elif description_text:
        first_sentence = re.split(r"(?<=[.!?])\s+", description_text.strip(), maxsplit=1)[0]
        title, title_src = _clean(first_sentence, cfg.MAX_TITLE_CHARS), INFERRED
    else:
        title, title_src = None, MISSING

    # Especialidad
    if labeled.get("medicalSpecialty"):
        specialty = _canonical(labeled["medicalSpecialty"], _CANONICAL_SPECIALTY, cfg.SPECIALTY_KEYWORDS)
        specialty_src = DETECTED if specialty else MISSING
    else:
        specialty, specialty_src = _best_by_keywords(folded_all, cfg.SPECIALTY_KEYWORDS), INFERRED

    # Tipo de evento
    if labeled.get("eventType"):
        event_type = _canonical(labeled["eventType"], _CANONICAL_EVENT, cfg.EVENT_TYPE_KEYWORDS) or _clean(
            labeled["eventType"], 60)
        event_src = DETECTED
    else:
        event_type, event_src = _best_by_keywords(folded_all, cfg.EVENT_TYPE_KEYWORDS), INFERRED

    # Fecha del evento
    event_date, date_src = (_parse_date(labeled["eventDate"], today), DETECTED) if labeled.get("eventDate") else (None, MISSING)
    if event_date is None:
        event_date, date_src = _parse_date(full_text, today), INFERRED

    # Edad de referencia
    age, age_src = None, MISSING
    if labeled.get("ageReference"):
        m = re.search(r"\d{1,3}", labeled["ageReference"])
        if m and cfg.MIN_AGE <= int(m.group()) <= cfg.MAX_AGE:
            age, age_src = int(m.group()), DETECTED
    if age is None:
        age, age_src = _age_from_text(folded_all), INFERRED

    # Complejidad
    complexity, complexity_src = _complexity(labeled.get("complexity"), labeled.get("procedure"), folded_all)

    # Booleanos que alimentan al clasificador: ante la duda, "missing".
    consent, consent_src = None, MISSING
    if labeled.get("informedConsent") is not None:
        consent, consent_src = _interpret_bool(labeled["informedConsent"]), DETECTED
    if consent is None:
        consent, consent_src = _consent_from_text(folded_all), INFERRED

    docs, docs_src = None, MISSING
    if labeled.get("documentationComplete") is not None:
        docs, docs_src = _interpret_bool(labeled["documentationComplete"]), DETECTED
    if docs is None:
        docs, docs_src = _documentation_from_text(folded_all), INFERRED

    complaints, complaints_src = None, MISSING
    if labeled.get("hasPriorComplaints") is not None:
        value = labeled["hasPriorComplaints"]
        interpreted = _interpret_bool(value)
        # "ninguna" -> sin quejas (False de _interpret_bool significa "no hay"); un texto con
        # cifras o "sí" indica que hay quejas.
        if interpreted is False:
            complaints, complaints_src = False, DETECTED
        elif interpreted is True or re.search(r"\d", value):
            complaints, complaints_src = True, DETECTED
    if complaints is None:
        complaints, complaints_src = _complaints_from_text(folded_all), INFERRED

    # Observaciones y factores
    notes = _clean(labeled["notes"], cfg.MAX_NOTES_CHARS) if labeled.get("notes") else None
    factors = [label for term, label in cfg.FACTOR_KEYWORDS.items() if _has_term(folded_all, term)][:6]

    # Omisión de datos personales en todo texto que sale del servicio.
    pii_found: list[str] = []

    def safe(text: str | None) -> str | None:
        if text is None:
            return None
        cleaned, found = redact(text)
        for label in found:
            if label not in pii_found:
                pii_found.append(label)
        return cleaned

    description_value = _clean(description_text, cfg.MAX_DESCRIPTION_CHARS)
    if len(description_value) < cfg.MIN_DESCRIPTION_CHARS:
        description_value = None

    fields = {
        "title": _field(safe(title), title_src),
        "description": _field(safe(description_value), description_source),
        "medicalSpecialty": _field(specialty, specialty_src),
        "eventType": _field(safe(event_type), event_src),
        "procedureComplexity": _field(complexity, complexity_src),
        "eventDate": _field(event_date, date_src),
        "ageReference": _field(age, age_src),
        "documentationComplete": _field(docs, docs_src),
        "informedConsent": _field(consent, consent_src),
        "hasPriorComplaints": _field(complaints, complaints_src),
        "notes": _field(safe(notes), DETECTED),
        "relevantFactors": _field([safe(f) for f in factors], INFERRED),
    }
    return {"fields": fields, "piiRedacted": pii_found}
