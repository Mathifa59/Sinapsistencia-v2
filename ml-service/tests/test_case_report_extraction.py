"""
Extracción de campos desde la imagen de un informe de caso (extract-v1).

Cubre lo determinista (parser y omisión de datos personales) con texto, y el OCR real
sobre un informe SIMULADO (tests/fixtures/informe_simulado.png, sin datos de nadie).
Llama a las funciones directamente (sin TestClient/httpx), como el resto de pruebas.

Estas pruebas verifican el comportamiento sobre ese informe y sobre textos construidos a
mano; NO miden la exactitud de la extracción en informes reales (no hay un conjunto
etiquetado), y no deben citarse como tal.
"""

import io
from datetime import date
from pathlib import Path

import pytest
from fastapi import HTTPException, Response
from starlette.datastructures import Headers, UploadFile

from app.extraction.parser import DETECTED, INFERRED, MISSING, parse_report
from app.extraction.pii import redact

FIXTURE = Path(__file__).parent / "fixtures" / "informe_simulado.png"
HOY = date(2026, 11, 19)


def _campos(lineas: list[str]) -> dict:
    return parse_report(lineas, today=HOY)["fields"]


# Texto tal como lo entrega el OCR del informe simulado: sin tildes y con espacios comidos.
LINEAS_OCR = [
    "INFORME DE CASO CLINICO-LEGAL (SIMULADO)",
    "Titulo: Hemorragia y paro cardiorrespiratorio en cirugia de hernia",
    "Especialidad: Cirugia General",
    "Tipo de evento: Cirugia",
    "Fechadelevento:15/09/2026",
    "Edad de referencia: 54 anos",
    "Procedimiento: Hernioplastia inguinal laparoscopica, cirugia de alta complejidad",
    "Descripcion:",
    "Durante la reparacion de una hernia inguinal se produjo una hemorragia intraoperatoria",
    "que derivo en un paro cardiorrespiratorio. El paciente fue reanimado, pero presento",
    "una encefalopatia hipoxico isquemica posterior. La familia indica que presentara una demanda.",
    "Consentimientoinformado:firmado",
    "Historia clinica: completa",
    "Quejas previas: ninguna",
    "Observaciones: poca experiencia del equipo en este tipo de cirugia.",
]


def test_informe_con_etiquetas_se_lee_completo():
    f = _campos(LINEAS_OCR)
    assert f["title"] == {"value": "Hemorragia y paro cardiorrespiratorio en cirugia de hernia", "source": DETECTED}
    assert f["medicalSpecialty"] == {"value": "Cirugía General", "source": DETECTED}
    assert f["eventType"] == {"value": "Cirugía", "source": DETECTED}
    assert f["eventDate"] == {"value": "2026-09-15", "source": DETECTED}
    assert f["ageReference"] == {"value": 54, "source": DETECTED}
    assert f["procedureComplexity"]["value"] == "alta"
    assert f["informedConsent"] == {"value": True, "source": DETECTED}
    assert f["documentationComplete"] == {"value": True, "source": DETECTED}
    assert f["hasPriorComplaints"] == {"value": False, "source": DETECTED}
    assert "encefalopatia" in f["description"]["value"]
    assert f["description"]["source"] == DETECTED
    assert "poca experiencia" in f["notes"]["value"]


def test_la_urgencia_percibida_nunca_se_extrae():
    assert "perceivedUrgency" not in _campos(LINEAS_OCR)


def test_informe_en_texto_libre_infiere_y_marca_como_inferido():
    f = _campos([
        "Paciente de 47 años sometido a cesárea de emergencia.",
        "Se produjo una hemorragia posparto con paro cardiorrespiratorio.",
        "No se obtuvo consentimiento informado por la urgencia.",
        "Sin quejas previas registradas.",
    ])
    assert f["medicalSpecialty"] == {"value": "Ginecología y Obstetricia", "source": INFERRED}
    assert f["ageReference"] == {"value": 47, "source": INFERRED}
    assert f["informedConsent"] == {"value": False, "source": INFERRED}
    assert f["hasPriorComplaints"] == {"value": False, "source": INFERRED}
    assert f["procedureComplexity"] == {"value": "alta", "source": INFERRED}
    # La documentación no se menciona: no se adivina.
    assert f["documentationComplete"] == {"value": None, "source": MISSING}


def test_ante_la_duda_los_campos_del_clasificador_quedan_en_missing():
    f = _campos(["El médico atendió una consulta de rutina sin mayores novedades en el servicio."])
    for campo in ("informedConsent", "documentationComplete", "hasPriorComplaints"):
        assert f[campo] == {"value": None, "source": MISSING}


def test_consentimiento_mencionado_sin_decir_si_se_firmo_no_se_adivina():
    f = _campos(["Se revisó el consentimiento informado en la historia del caso para el análisis legal."])
    assert f["informedConsent"]["source"] == MISSING


def test_edad_no_se_confunde_con_anos_de_experiencia():
    f = _campos(["El cirujano tiene 20 años de experiencia. El paciente de 61 años fue operado."])
    assert f["ageReference"]["value"] == 61


def test_fecha_futura_o_invalida_se_descarta():
    assert _campos(["Fecha del evento: 15/09/2030"])["eventDate"]["source"] == MISSING
    assert _campos(["Fecha del evento: 31/02/2026"])["eventDate"]["source"] == MISSING


def test_fecha_en_palabras():
    f = _campos(["Los hechos ocurrieron el 3 de setiembre de 2026 durante una cirugía."])
    assert f["eventDate"] == {"value": "2026-09-03", "source": INFERRED}


def test_palabra_clave_corta_no_coincide_dentro_de_otra_palabra():
    # "uci" no debe encontrarse dentro de "reducir"/"producir".
    f = _campos(["Se buscó reducir el tiempo de espera y producir un informe de rutina."])
    assert f["procedureComplexity"] == {"value": None, "source": MISSING}


def test_complejidad_baja_por_palabras_clave():
    f = _campos(["Procedimiento: limpieza de oído en consulta ambulatoria."])
    assert f["procedureComplexity"] == {"value": "baja", "source": INFERRED}


# ── Datos personales (Ley 29733) ─────────────────────────────────────────────

def test_redact_omite_dni_telefono_correo_y_nombre():
    texto = "Paciente Juan Pérez Soto, DNI 12345678, cel 987654321, juan@example.com."
    limpio, tipos = redact(texto)
    for dato in ("Juan", "12345678", "987654321", "juan@example.com"):
        assert dato not in limpio
    assert set(tipos) == {"DNI", "Teléfono", "Correo electrónico", "Nombre propio"}


def test_el_resultado_nunca_contiene_los_datos_omitidos():
    resultado = parse_report([
        "Descripcion: Paciente Ana Torres Lima (DNI 45678912) con cirugía de hernia y hemorragia.",
        "Observaciones: contactar al 998877665.",
    ], today=HOY)
    volcado = str(resultado["fields"])
    for dato in ("Ana", "Torres", "45678912", "998877665"):
        assert dato not in volcado
    assert {"DNI", "Teléfono", "Nombre propio"} <= set(resultado["piiRedacted"])
    # Se informa el TIPO de dato omitido, nunca su valor.
    assert "45678912" not in str(resultado["piiRedacted"])


# ── OCR real sobre el informe simulado + endpoint ────────────────────────────

def _subida(contenido: bytes, tipo: str = "image/png") -> UploadFile:
    return UploadFile(file=io.BytesIO(contenido), filename="informe.png", headers=Headers({"content-type": tipo}))


@pytest.fixture(scope="module")
def ocr_disponible():
    pytest.importorskip("rapidocr_onnxruntime")


def test_endpoint_lee_el_informe_simulado(ocr_disponible):
    from app.main import extract_case_report

    respuesta = extract_case_report(Response(), _subida(FIXTURE.read_bytes()))
    campos = respuesta.fields
    assert respuesta.extractor_version == "extract-v1"
    assert campos["medicalSpecialty"].value == "Cirugía General"
    assert campos["eventType"].value == "Cirugía"
    assert campos["ageReference"].value == 54
    assert campos["informedConsent"].value is True
    assert campos["documentationComplete"].value is True
    assert campos["hasPriorComplaints"].value is False
    assert "hernia" in campos["description"].value.lower()
    assert respuesta.pii_redacted == []
    assert "revisión humana" in respuesta.advisory


def test_endpoint_rechaza_formato_no_admitido():
    from app.main import extract_case_report

    with pytest.raises(HTTPException) as err:
        extract_case_report(Response(), _subida(b"%PDF-1.4", "application/pdf"))
    assert err.value.status_code == 400


def test_endpoint_rechaza_archivo_que_no_es_imagen():
    from app.main import extract_case_report

    with pytest.raises(HTTPException) as err:
        extract_case_report(Response(), _subida(b"esto no es una imagen", "image/png"))
    assert err.value.status_code == 400


def test_endpoint_informa_cuando_no_hay_texto_legible(ocr_disponible):
    from PIL import Image

    from app.main import extract_case_report

    buffer = io.BytesIO()
    Image.new("RGB", (400, 300), "white").save(buffer, format="PNG")
    with pytest.raises(HTTPException) as err:
        extract_case_report(Response(), _subida(buffer.getvalue()))
    assert err.value.status_code == 422
