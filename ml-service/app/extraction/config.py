"""Configuración del extractor de informes de caso (imagen -> campos del formulario).

Umbrales, límites y diccionarios viven aquí (sin números mágicos en el resto del
módulo). Todo es determinista: no hay aleatoriedad ni modelos entrenados; el
reconocimiento de texto usa un OCR local (sin enviar la imagen a terceros, Ley 29733)
y los campos se obtienen con reglas auditables.
"""

EXTRACTOR_VERSION = "extract-v1"
OCR_ENGINE = "rapidocr-onnxruntime"

# ── Límites de la imagen recibida ────────────────────────────────────────────
MAX_IMAGE_BYTES = 8 * 1024 * 1024
MAX_IMAGE_PIXELS = 40_000_000  # protege contra imágenes-bomba de descompresión
MAX_IMAGE_SIDE = 3000  # lado mayor tras reducir (acelera el OCR sin perder legibilidad)
ALLOWED_CONTENT_TYPES = {"image/png", "image/jpeg", "image/webp", "image/bmp"}

# ── OCR ──────────────────────────────────────────────────────────────────────
# Medido sobre el informe simulado de tests/fixtures: con el clasificador de ángulo
# activado se perdían líneas que empezaban en minúscula; sin él se leen todas.
OCR_USE_ANGLE_CLASSIFIER = False
OCR_DET_LIMIT_SIDE_LEN = 1600
OCR_DET_LIMIT_TYPE = "max"
MIN_LINE_CONFIDENCE = 0.50  # descarta líneas con reconocimiento muy dudoso

# ── Salida ───────────────────────────────────────────────────────────────────
MIN_TEXT_CHARS = 20  # por debajo de esto se considera que la imagen no tiene texto legible
MAX_TITLE_CHARS = 120
MAX_DESCRIPTION_CHARS = 1500
MIN_DESCRIPTION_CHARS = 10  # igual que la validación del formulario
MAX_NOTES_CHARS = 500
MIN_AGE, MAX_AGE = 0, 120

# ── Etiquetas reconocidas (comparadas sin tildes, minúsculas y sin espacios) ─
# El OCR a veces junta palabras ("Fechadelevento:"), por eso se compara "aplastado".
LABELS = {
    "title": ("titulo", "asunto", "titulodelcaso"),
    "medicalSpecialty": ("especialidad", "especialidadmedica", "areamedica", "servicio"),
    "eventType": ("tipodeevento", "tipodeincidente"),
    "eventDate": ("fechadelevento", "fechadelincidente", "fechadelhecho", "fechadeocurrencia"),
    "ageReference": ("edad", "edadreferencial", "edaddereferencia", "edaddelpaciente"),
    "procedure": ("procedimiento", "intervencion", "procedimientorealizado"),
    "complexity": ("complejidad", "complejidaddelprocedimiento"),
    "description": ("descripcion", "descripciondelcaso", "resumen", "relato", "hechos", "resumendelcaso"),
    "informedConsent": ("consentimientoinformado", "consentimiento"),
    "documentationComplete": ("historiaclinica", "documentacion", "documentacionclinica"),
    "hasPriorComplaints": ("quejasprevias", "reclamosprevios", "denunciasprevias", "antecedentesdequejas"),
    "notes": ("observaciones", "notas", "notasadicionales", "otros"),
}

# Palabras de un valor booleano (ya sin tildes). Las negativas se evalúan primero.
NEGATIVE_WORDS = (
    "no", "ninguna", "ninguno", "ausente", "incompleta", "incompleto", "sin", "falta",
    "faltan", "nofirmado", "nofirmo", "noconsta", "pendiente",
)
POSITIVE_WORDS = (
    "si", "firmado", "firmada", "completa", "completo", "presente", "adjunto", "existe",
    "existen", "registrada", "registrado", "obtenido", "obtenido",
)

# ── Especialidad: palabras clave (sin tildes) -> nombre canónico ─────────────
# Los nombres canónicos son las claves de app.risk.baselines.SPECIALTY_BASELINE.
SPECIALTY_KEYWORDS = {
    "Cirugía General": ("cirugia", "hernia", "colecist", "apendic", "laparosc", "quirurg", "operatori"),
    "Ginecología y Obstetricia": ("ginecolog", "obstetric", "cesarea", "parto", "gestan", "embaraz", "puerper"),
    "Anestesiología": ("anestesi", "intubacion", "bloqueo epidural"),
    "Neurología": ("neurolog", "neurocirug", "encefal", "acv", "ictus", "epilepsi"),
    "Traumatología": ("traumatolog", "fractura", "ortoped", "luxacion", "protesis de cadera"),
    "Oncología": ("oncolog", "cancer", "quimioterap", "tumor", "radioterap"),
    "Cardiología": ("cardiolog", "infarto", "arritmia", "marcapaso", "insuficiencia cardiaca"),
    "Urología": ("urolog", "prostat", "renal litiasis", "vejiga"),
    "Pediatría": ("pediatr", "neonat", "lactante", "recien nacido"),
    "Gastroenterología": ("gastroenter", "endoscop", "colonoscop", "hepat"),
    "Nefrología": ("nefrolog", "dialisis", "hemodialisis"),
    "Psiquiatría": ("psiquiatr", "suicid", "psicosis", "depresion mayor"),
    "Neumología": ("neumolog", "asma", "epoc", "neumonia"),
    "Hematología": ("hematolog", "anemia", "transfusion", "leucemia"),
    "Infectología": ("infectolog", "sepsis", "vih", "tuberculosis"),
    "Oftalmología": ("oftalmolog", "catarata", "retina", "glaucoma"),
    "Endocrinología": ("endocrinolog", "diabet", "tiroid"),
    "Reumatología": ("reumatolog", "artritis", "lupus"),
    "Medicina General": ("medicina general", "consulta general", "atencion primaria"),
    "Dermatología": ("dermatolog", "piel", "melanoma", "dermatitis"),
}

# ── Tipo de evento (etiqueta canónica -> palabras clave sin tildes) ──────────
EVENT_TYPE_KEYWORDS = {
    "Cirugía": ("cirugia", "operacion", "intervencion quirurgica", "quirurg", "laparosc"),
    "Emergencia": ("emergencia", "urgencias", "shock"),
    "Parto": ("parto", "cesarea"),
    "Diagnóstico": ("diagnostic", "examen", "biopsia", "resultado de laboratorio", "imagen"),
    "Consulta": ("consulta", "ambulator", "control"),
    "Procedimiento": ("procedimiento", "endoscop", "puncion"),
}

# ── Complejidad del procedimiento (sin tildes) ───────────────────────────────
COMPLEXITY_HIGH = (
    "alta complejidad", "cirugia mayor", "laparosc", "neurocirug", "trasplante", "cardiaca",
    "cesarea", "uci", "cuidados intensivos", "paro cardio", "hemorragia", "anestesia general",
    "intubacion", "craneo", "politraumat",
)
COMPLEXITY_LOW = (
    "baja complejidad", "cirugia menor", "limpieza", "control de rutina", "chequeo",
    "curacion", "sutura simple", "consulta ambulatoria", "extraccion simple",
)

# ── Factores relevantes sugeridos (palabra clave sin tildes -> etiqueta) ─────
FACTOR_KEYWORDS = {
    "poca experiencia": "Poca experiencia del equipo en este tipo de procedimiento",
    "hemorragia": "Hemorragia intraoperatoria",
    "paro cardio": "Paro cardiorrespiratorio",
    "reanim": "Maniobras de reanimación",
    "demanda": "La familia anuncia una demanda",
    "denuncia": "Anuncio de denuncia",
    "secuela": "Secuelas para el paciente",
    "emergencia": "Atención de emergencia",
}

MONTHS = {
    "enero": 1, "febrero": 2, "marzo": 3, "abril": 4, "mayo": 5, "junio": 6, "julio": 7,
    "agosto": 8, "setiembre": 9, "septiembre": 9, "octubre": 10, "noviembre": 11, "diciembre": 12,
}

# ── Datos personales (Ley 29733): se reemplazan por una marca antes de devolver ──
# Espejo de los patrones del formulario (case-form-modal.component.ts) más variantes
# propias de un informe: número tras "DNI", historia clínica y títulos honoríficos.
PII_PATTERNS = (
    ("DNI", r"(?i)\bdni\b[\s:.\-nº°]*\d{6,9}|\b\d{8}\b"),
    ("Teléfono", r"\+51\s?\d{8,9}|\b9\d{8}\b"),
    ("Correo electrónico", r"[\w.+-]+@[\w-]+\.[\w.]+"),
    ("Historia clínica", r"(?i)\b(?:h\.?\s?c\.?|historia\s+cl[ií]nica)\s*(?:n[º°o.]*|#)\s*[:\-]?\s*\d{3,}"),
    (
        "Nombre propio",
        r"\b(?:[Pp]aciente|[Ss]r\.?|[Ss]ra\.?|[Ss]rta\.?|[Ss]e[ñn]or(?:a)?|[Dd]on|[Dd]oña)\s*[:\-]?\s+"
        r"[A-ZÁÉÍÓÚÑ][a-záéíóúñ]+(?:\s+[A-ZÁÉÍÓÚÑ][a-záéíóúñ]+){1,3}",
    ),
)
PII_PLACEHOLDER = {
    "DNI": "[DNI omitido]",
    "Teléfono": "[teléfono omitido]",
    "Correo electrónico": "[correo omitido]",
    "Historia clínica": "[historia clínica omitida]",
    "Nombre propio": "paciente [nombre omitido]",
}
