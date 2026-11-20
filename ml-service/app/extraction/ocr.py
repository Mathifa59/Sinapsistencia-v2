"""Reconocimiento de texto local (OCR) sobre la imagen del informe.

Se usa RapidOCR (ONNX) porque corre dentro del propio servicio: la imagen no sale
a ningún tercero (Ley 29733) y no requiere instalar binarios del sistema. La imagen
se procesa en memoria y no se guarda.

Degradación declarada: si la librería no está instalada o falla, el endpoint
responde 503 y el formulario de caso sigue funcionando de forma manual.
"""

import io
import threading
from dataclasses import dataclass

from app.extraction.config import (
    ALLOWED_CONTENT_TYPES,
    MAX_IMAGE_BYTES,
    MAX_IMAGE_PIXELS,
    MAX_IMAGE_SIDE,
    MIN_LINE_CONFIDENCE,
    OCR_DET_LIMIT_SIDE_LEN,
    OCR_DET_LIMIT_TYPE,
    OCR_USE_ANGLE_CLASSIFIER,
)


class OcrUnavailable(RuntimeError):
    """El motor OCR no está instalado o no pudo iniciarse."""


class InvalidImage(ValueError):
    """La imagen no cumple tipo, tamaño o legibilidad mínimos."""


@dataclass(frozen=True)
class OcrLine:
    text: str
    confidence: float
    top: float  # coordenada y de la línea (para ordenar de arriba hacia abajo)


_engine = None
_lock = threading.Lock()


def _get_engine():
    """Carga perezosa y única del motor (los modelos ONNX pesan ~15 MB)."""
    global _engine
    if _engine is None:
        with _lock:
            if _engine is None:
                try:
                    from rapidocr_onnxruntime import RapidOCR
                except ImportError as exc:  # pragma: no cover - depende del entorno
                    raise OcrUnavailable("rapidocr-onnxruntime no está instalado") from exc
                _engine = RapidOCR(
                    det_limit_side_len=OCR_DET_LIMIT_SIDE_LEN,
                    det_limit_type=OCR_DET_LIMIT_TYPE,
                )
    return _engine


def is_available() -> bool:
    try:
        _get_engine()
        return True
    except OcrUnavailable:
        return False


def warm_up() -> None:
    """Carga el motor y hace una inferencia vacía: la primera lectura real tardaba ~13 s más.

    Se llama en segundo plano al iniciar; si el OCR no está instalado no hace nada.
    """
    try:
        import numpy as np

        _get_engine()(np.full((64, 256, 3), 255, dtype="uint8"), use_cls=OCR_USE_ANGLE_CLASSIFIER)
    except Exception:  # noqa: BLE001 - el calentamiento nunca debe impedir que el servicio arranque
        pass


def _decode(image_bytes: bytes, content_type: str | None):
    if content_type and content_type.lower() not in ALLOWED_CONTENT_TYPES:
        raise InvalidImage("Formato no admitido: usa una imagen PNG, JPG, WEBP o BMP.")
    if len(image_bytes) > MAX_IMAGE_BYTES:
        raise InvalidImage(f"La imagen supera el máximo de {MAX_IMAGE_BYTES // (1024 * 1024)} MB.")

    from PIL import Image  # Pillow llega como dependencia de rapidocr-onnxruntime

    Image.MAX_IMAGE_PIXELS = MAX_IMAGE_PIXELS
    try:
        image = Image.open(io.BytesIO(image_bytes))
        image.load()
    except Exception as exc:  # noqa: BLE001 - cualquier fallo de decodificación es imagen inválida
        raise InvalidImage("No se pudo leer la imagen (archivo dañado o formato no admitido).") from exc

    image = image.convert("RGB")
    longest = max(image.size)
    if longest > MAX_IMAGE_SIDE:
        scale = MAX_IMAGE_SIDE / longest
        image = image.resize((round(image.width * scale), round(image.height * scale)))

    import numpy as np

    return np.array(image)


def read_lines(image_bytes: bytes, content_type: str | None = None) -> list[OcrLine]:
    """Devuelve las líneas de texto reconocidas, ordenadas de arriba hacia abajo."""
    array = _decode(image_bytes, content_type)
    engine = _get_engine()
    try:
        result, _ = engine(array, use_cls=OCR_USE_ANGLE_CLASSIFIER)
    except Exception as exc:  # noqa: BLE001
        raise OcrUnavailable("El motor OCR falló al procesar la imagen") from exc

    lines = [
        OcrLine(text=text.strip(), confidence=float(conf), top=float(box[0][1]))
        for box, text, conf in (result or [])
        if text and text.strip() and float(conf) >= MIN_LINE_CONFIDENCE
    ]
    lines.sort(key=lambda line: line.top)
    return lines
