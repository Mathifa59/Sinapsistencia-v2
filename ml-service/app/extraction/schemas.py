"""Contrato de POST /api/v1/extract-case-report."""

from typing import Any, Literal, Optional

from pydantic import BaseModel, Field

FieldSource = Literal["detected", "inferred", "missing"]


class ExtractedField(BaseModel):
    """Valor leído del informe y de dónde sale: explícito, deducido o ausente."""

    value: Optional[Any] = None
    source: FieldSource = "missing"


class OcrInfo(BaseModel):
    engine: str
    lines: int
    # Estadística cruda del OCR (confianza media por línea); NO es la exactitud de la extracción.
    mean_line_confidence: float


class CaseReportExtraction(BaseModel):
    extractor_version: str
    fields: dict[str, ExtractedField]
    # Tipos de dato personal omitidos del texto (p. ej. "DNI"); nunca sus valores.
    pii_redacted: list[str] = Field(default_factory=list)
    ocr: OcrInfo
    # Recordatorio ético HU-43: la extracción es una ayuda y el médico confirma cada campo.
    advisory: str
