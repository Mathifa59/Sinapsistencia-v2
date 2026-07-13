"""Riesgo base por especialidad — FUENTE UNICA DE VERDAD.

Compartido por el generador del dataset (training/generate_risk_dataset.py) y el
servicio de inferencia (app/risk/model.py) para que nunca se desincronicen.
Procedimientos invasivos / alta criticidad concentran mas reclamos medico-legales
(referencias en el charter del proyecto).
"""

SPECIALTY_BASELINE = {
    "Ginecología y Obstetricia": 0.50,
    "Anestesiología": 0.48,
    "Cirugía General": 0.45,
    "Neurología": 0.42,
    "Traumatología": 0.40,
    "Oncología": 0.40,
    "Cardiología": 0.38,
    "Urología": 0.35,
    "Pediatría": 0.33,
    "Gastroenterología": 0.30,
    "Nefrología": 0.30,
    "Psiquiatría": 0.30,
    "Neumología": 0.28,
    "Hematología": 0.28,
    "Infectología": 0.27,
    "Oftalmología": 0.25,
    "Endocrinología": 0.24,
    "Reumatología": 0.22,
    "Medicina General": 0.20,
    "Dermatología": 0.18,
}
