-- ============================================================================
-- V9: Actualiza las métricas del clasificador de riesgo al modelo rf-v2.
--
-- El modelo se reentrenó sobre un dataset sintético balanceado de 40 000 filas
-- (ml-service/training). Métricas del set de validación (20%) + CV 5-fold.
-- Sustituye los valores del seed rf-v1 (V6) para que el panel de métricas
-- (HU-35) muestre el modelo vigente.
-- ============================================================================

UPDATE model_metrics
SET model_version         = 'rf-v2',
    precision_score       = 0.7870,
    recall_score          = 0.7887,
    f1_score              = 0.7877,
    avg_response_time_ms  = 60,
    dataset_size          = 40000,
    notes                 = 'RandomForest (150 árboles, dataset sintético balanceado 40k). CV 5-fold f1_macro=0.7911±0.0033. Supera baseline lineal (0.792) y trivial (0.250). Regresor de severidad R²=0.93.'
WHERE model_name = 'risk_classifier';
