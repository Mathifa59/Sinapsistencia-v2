-- ============================================================================
-- V16: H-02 -- historial reproducible de ranking de matching + vinculo entre
-- la solicitud de contacto y la recomendacion concreta que la origino.
--
-- Antes, GET /api/matching/lawyers RECALCULABA el ranking en cada carga (sin
-- guardar nada) y POST /api/matching/lawyers guardaba filas sueltas de
-- match_recommendations con IDs temporales "rec-..." nunca devueltos al
-- cliente -- no habia forma de reproducir "que vio el medico cuando eligio
-- a este abogado". Esta migracion agrega SOLO el esquema; el codigo Java que
-- adquiere/completa ejecuciones es un commit aparte sobre este mismo esquema.
--
-- recommendation_runs es la fotografia de UNA ejecucion del matching (inputs,
-- corpus, pesos, resultado) -- nunca se actualiza tras completarse. Todo
-- NULL-able salvo lo minimo para no exigir backfill de filas historicas: una
-- recomendacion sin run_id queda identificada como legacy, nunca se le
-- fabrica una ejecucion retroactiva.
-- ============================================================================

CREATE TABLE recommendation_runs (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    doctor_id          UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    case_id            UUID NOT NULL REFERENCES cases(id) ON DELETE CASCADE,
    idempotency_key    VARCHAR(100) NOT NULL,
    request_hash       VARCHAR(64) NOT NULL,
    status             VARCHAR(20) NOT NULL,
    origin             VARCHAR(20),
    model_version      VARCHAR(100),
    pipeline_version   VARCHAR(100) NOT NULL,
    top_k              INTEGER NOT NULL DEFAULT 10,
    input_hash         VARCHAR(64),
    corpus_hash        VARCHAR(64),
    input_snapshot     JSONB,
    corpus_snapshot    JSONB,
    weights            JSONB,
    model_info         JSONB,
    candidate_count    INTEGER,
    result_count       INTEGER,
    error_code         VARCHAR(100),
    error_message      TEXT,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at       TIMESTAMPTZ,

    CONSTRAINT chk_recommendation_runs_status
        CHECK (status IN ('processing', 'completed', 'failed')),
    CONSTRAINT chk_recommendation_runs_origin
        CHECK (origin IS NULL OR origin IN ('ml', 'fallback')),
    CONSTRAINT chk_recommendation_runs_top_k_positive
        CHECK (top_k > 0),
    CONSTRAINT chk_recommendation_runs_candidate_count_non_negative
        CHECK (candidate_count IS NULL OR candidate_count >= 0),
    CONSTRAINT chk_recommendation_runs_result_count_non_negative
        CHECK (result_count IS NULL OR result_count >= 0),
    CONSTRAINT uq_recommendation_runs_doctor_idempotency_key
        UNIQUE (doctor_id, idempotency_key)
);

-- Historial por caso (GET /api/matching/recommendation-runs?caseId=...) con
-- el mismo orden deterministico created_at DESC, id DESC que H-05 uso para
-- ml_classifications.
CREATE INDEX idx_recommendation_runs_case_id_created_at_id
    ON recommendation_runs (case_id, created_at DESC, id DESC);

-- Recuperacion de ejecuciones "processing" abandonadas (timeout de proceso).
CREATE INDEX idx_recommendation_runs_status_updated_at
    ON recommendation_runs (status, updated_at);

COMMENT ON TABLE recommendation_runs IS
    'H-02: una fila por ejecucion de matching. completed = fotografia inmutable (inputs, corpus, pesos, resultado); nunca se actualiza tras completarse. Permite reproducir exactamente que vio el medico al elegir un abogado.';
COMMENT ON COLUMN recommendation_runs.idempotency_key IS
    'Clave generada por el cliente para esta accion de generacion; unica por medico. Repetirla con el mismo request_hash devuelve el mismo run.';
COMMENT ON COLUMN recommendation_runs.request_hash IS
    'Huella del COMANDO (caseId + parametros explicitos del cliente) -- compara reintentos de la misma clave, no cambia si cambia el perfil de un abogado despues.';
COMMENT ON COLUMN recommendation_runs.input_hash IS
    'Huella de las entradas efectivamente usadas (perfil del medico + caso). Detecta antiguedad del run frente al estado actual, no invalida un run completado.';
COMMENT ON COLUMN recommendation_runs.corpus_hash IS
    'Huella del corpus de abogados efectivamente usado en esta ejecucion.';
COMMENT ON COLUMN recommendation_runs.origin IS
    'ml = respondio el modelo compuesto; fallback = matching determinista por especialidad (ML no disponible). NULL mientras status != completed.';

-- ----------------------------------------------------------------------------
-- match_recommendations: vinculo a la ejecucion que la genero + fotografia
-- del perfil de abogado mostrado (lawyer_snapshot) para que un cambio de
-- perfil posterior no reescriba lo que el medico vio (RF-02.6).
-- ----------------------------------------------------------------------------

ALTER TABLE match_recommendations
    ADD COLUMN run_id                 UUID REFERENCES recommendation_runs(id) ON DELETE CASCADE,
    ADD COLUMN rank                   INTEGER,
    ADD COLUMN score_raw              NUMERIC(7,6),
    ADD COLUMN content_score_raw      NUMERIC(7,6),
    ADD COLUMN performance_score_raw  NUMERIC(7,6),
    ADD COLUMN lawyer_snapshot        JSONB,
    ADD COLUMN matched_specialties    TEXT[];

ALTER TABLE match_recommendations
    ADD CONSTRAINT chk_match_recommendations_rank_positive
        CHECK (rank IS NULL OR rank > 0),
    ADD CONSTRAINT chk_match_recommendations_score_raw_range
        CHECK (score_raw IS NULL OR (score_raw >= 0 AND score_raw <= 1)),
    ADD CONSTRAINT chk_match_recommendations_content_score_raw_range
        CHECK (content_score_raw IS NULL OR (content_score_raw >= 0 AND content_score_raw <= 1)),
    ADD CONSTRAINT chk_match_recommendations_performance_score_raw_range
        CHECK (performance_score_raw IS NULL OR (performance_score_raw >= 0 AND performance_score_raw <= 1));

CREATE INDEX idx_match_recommendations_run_id_rank
    ON match_recommendations (run_id, rank);

-- Unicidad de posicion/abogado DENTRO de una ejecucion -- parcial porque las
-- filas legacy (run_id NULL) no participan de esta regla.
CREATE UNIQUE INDEX uq_match_recommendations_run_rank
    ON match_recommendations (run_id, rank) WHERE run_id IS NOT NULL;
CREATE UNIQUE INDEX uq_match_recommendations_run_lawyer
    ON match_recommendations (run_id, lawyer_id) WHERE run_id IS NOT NULL;

COMMENT ON COLUMN match_recommendations.run_id IS
    'Ejecucion que genero esta recomendacion. NULL = fila legacy anterior a H-02, sin ejecucion asociada (no se le fabrica una retroactivamente).';
COMMENT ON COLUMN match_recommendations.score_raw IS
    'Score compuesto normalizado [0,1] tal como lo emite el modelo, antes de redondear a porcentaje. La columna legacy score sigue en escala 0-100 (NUMERIC(5,2)) por compatibilidad -- no se reinterpreta.';
COMMENT ON COLUMN match_recommendations.lawyer_snapshot IS
    'Fotografia del LawyerCardDto mostrado al generar la recomendacion -- un cambio posterior de perfil/disponibilidad no reescribe lo que el medico vio.';

-- ----------------------------------------------------------------------------
-- contact_requests: vinculo opcional a la recomendacion concreta que origino
-- la solicitud, y el origen de la seleccion (ranking vs directorio).
-- ----------------------------------------------------------------------------

ALTER TABLE contact_requests
    ADD COLUMN recommendation_id UUID REFERENCES match_recommendations(id) ON DELETE SET NULL,
    ADD COLUMN selection_source  VARCHAR(30);

ALTER TABLE contact_requests
    ADD CONSTRAINT chk_contact_requests_selection_source
        CHECK (selection_source IS NULL OR selection_source IN ('recommendation', 'directory', 'legacy_untracked'));

CREATE INDEX idx_contact_requests_recommendation_id
    ON contact_requests (recommendation_id);

COMMENT ON COLUMN contact_requests.recommendation_id IS
    'Recomendacion concreta (tarjeta) desde la que se solicito contacto, cuando selection_source = recommendation. mlScore ya existente se copia de score_raw * 100 en la creacion -- no se duplica esa columna.';
COMMENT ON COLUMN contact_requests.selection_source IS
    'recommendation = desde una tarjeta del ranking; directory = seleccion explicita desde el directorio sin ranking; legacy_untracked = fila anterior a H-02, origen desconocido (no se le atribuye un origen que no se registro).';
