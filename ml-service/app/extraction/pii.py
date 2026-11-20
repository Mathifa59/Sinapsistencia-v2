"""Omisión de datos personales en el texto extraído (Ley 29733).

El informe que sube el médico puede traer datos identificables. Esta capa los
reemplaza por una marca ANTES de que el texto salga del servicio: ni el texto
OCR completo ni los valores omitidos se devuelven, registran ni guardan.
La detección es heurística (igual que la del formulario): reduce el riesgo, no lo
elimina, y por eso el médico siempre revisa los campos antes de crear el caso.
"""

import re

from app.extraction.config import PII_PATTERNS, PII_PLACEHOLDER

_COMPILED = [(label, re.compile(pattern)) for label, pattern in PII_PATTERNS]


def redact(text: str) -> tuple[str, list[str]]:
    """Devuelve (texto sin datos personales, etiquetas de lo que se omitió).

    Las etiquetas indican el TIPO de dato omitido (p. ej. "DNI"), nunca su valor.
    """
    found: list[str] = []
    result = text
    for label, pattern in _COMPILED:
        result, count = pattern.subn(PII_PLACEHOLDER[label], result)
        if count and label not in found:
            found.append(label)
    return result, found
