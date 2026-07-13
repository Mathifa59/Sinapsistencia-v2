-- ============================================================================
-- V11: Unificación del pipeline de clasificación ML (HU-29/30/31).
--
-- 1. Los factores de riesgo del caso (documentación, consentimiento, quejas
--    previas) pasan a ser atributos del caso: alimentan al Random Forest en el
--    backend al momento de crear.
-- 2. ml_classifications persiste el RESULTADO REAL del RF (score, nivel y
--    desglose de factores en JSON): una sola fuente de verdad — lo que se
--    muestra al crear es lo mismo que muestra el detalle del caso.
-- 3. Actualiza la métrica del matching al modelo compuesto vigente.
-- ============================================================================

ALTER TABLE cases
    ADD COLUMN documentation_complete BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN informed_consent       BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN has_prior_complaints   BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE ml_classifications
    ADD COLUMN risk_score   NUMERIC(5, 4),
    ADD COLUMN risk_level   VARCHAR(20),
    ADD COLUMN risk_factors TEXT;

COMMENT ON COLUMN ml_classifications.risk_factors IS 'Desglose por factor del Random Forest (JSON): name, weight, value, contribution, description.';

UPDATE model_metrics
SET model_version = 'tfidf-cosine+perf-v2',
    notes         = 'Score compuesto: TF-IDF + similitud coseno (70%) + desempeño verificable (30%: rating, casos resueltos, experiencia). Corpus vivo desde la BD.'
WHERE model_name = 'matching_tfidf';
