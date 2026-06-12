-- ============================================================================
-- Sinapsistencia — V1: Esquema base (alineación académica activada)
--
-- Modelo: migración del esquema Supabase original CON los cambios de contenido
-- del Charter/Backlog académico (§12 del blueprint):
--   * Flujo de 6 estados de consulta (HU-16):
--     pendiente → clasificada → asignada → en_revision → respondida → cerrada
--   * Datos de paciente NO identificables (Ley 29733, HU-12): se eliminan
--     `patients` y `clinical_episodes`; en su lugar `case_context` guarda un
--     contexto clínico-legal SIMULADO (edad referencial, área médica, resumen).
--   * Tablas para HUs de ML: clasificación (HU-29), respuesta legal (HU-21/22),
--     línea de tiempo (HU-26/27), métricas del modelo (HU-35), explicación XAI
--     del matching (HU-31/32).
--
-- Enums: se modelan como VARCHAR + CHECK (no tipos enum nativos) porque JPA los
-- mapea con AttributeConverter a strings exactos en español; CHECK garantiza el
-- dominio a nivel BD sin fricción de casteo en JDBC.
-- ============================================================================

CREATE EXTENSION IF NOT EXISTS vector;

-- ============================================================================
-- 1. profiles — raíz de identidad (reemplaza Supabase Auth + tabla profiles)
-- ============================================================================
CREATE TABLE profiles (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email         VARCHAR(255) NOT NULL UNIQUE,
    name          VARCHAR(255) NOT NULL,
    role          VARCHAR(20)  NOT NULL,
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
    avatar_url    TEXT,
    password_hash VARCHAR(100) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_profiles_role CHECK (role IN ('doctor', 'lawyer', 'admin'))
);

COMMENT ON TABLE profiles IS 'Usuarios del sistema (médicos, abogados, administradores). Sustituye a Supabase Auth.';

-- ============================================================================
-- 2. hospitals — catálogo institucional
-- ============================================================================
CREATE TABLE hospitals (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name       VARCHAR(255) NOT NULL,
    address    TEXT,
    city       VARCHAR(100) NOT NULL DEFAULT 'Lima',
    department VARCHAR(100) NOT NULL DEFAULT 'Lima',
    phone      VARCHAR(30),
    is_active  BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ============================================================================
-- 3-5. Perfiles por rol
-- ============================================================================
CREATE TABLE doctor_profiles (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          UUID NOT NULL UNIQUE REFERENCES profiles (id) ON DELETE CASCADE,
    cmp              VARCHAR(20)  NOT NULL,
    specialty        VARCHAR(150) NOT NULL,
    sub_specialties  TEXT[]       NOT NULL DEFAULT '{}',
    hospital         VARCHAR(255) NOT NULL DEFAULT '',
    hospital_id      UUID REFERENCES hospitals (id) ON DELETE SET NULL,
    years_experience INTEGER      NOT NULL DEFAULT 0,
    languages        TEXT[]       NOT NULL DEFAULT '{}',
    phone            VARCHAR(30)  NOT NULL DEFAULT '',
    bio              TEXT,
    embedding        vector,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE lawyer_profiles (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          UUID NOT NULL UNIQUE REFERENCES profiles (id) ON DELETE CASCADE,
    cab              VARCHAR(20)  NOT NULL,
    specialties      TEXT[]       NOT NULL DEFAULT '{}',
    medical_areas    TEXT[]       NOT NULL DEFAULT '{}',
    years_experience INTEGER      NOT NULL DEFAULT 0,
    rating           NUMERIC(3, 2) NOT NULL DEFAULT 0,
    resolved_cases   INTEGER      NOT NULL DEFAULT 0,
    available        BOOLEAN      NOT NULL DEFAULT TRUE,
    phone            VARCHAR(30)  NOT NULL DEFAULT '',
    bio              TEXT,
    embedding        vector,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE admin_profiles (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL UNIQUE REFERENCES profiles (id) ON DELETE CASCADE,
    department  VARCHAR(150) NOT NULL DEFAULT '',
    permissions TEXT[]       NOT NULL DEFAULT '{}',
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ============================================================================
-- 6. cases — la CONSULTA médico-legal (en UI se llama "consulta")
--    Flujo HU-16: pendiente → clasificada → asignada → en_revision →
--    respondida → cerrada. Prioridad HU-30 con justificación.
-- ============================================================================
CREATE TABLE cases (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title                  VARCHAR(255) NOT NULL,
    description            TEXT         NOT NULL,
    doctor_id              UUID         NOT NULL REFERENCES profiles (id) ON DELETE RESTRICT,
    lawyer_id              UUID REFERENCES profiles (id) ON DELETE SET NULL,
    status                 VARCHAR(20)  NOT NULL DEFAULT 'pendiente',
    priority               VARCHAR(20)  NOT NULL DEFAULT 'media',
    priority_justification TEXT,
    medical_specialty      VARCHAR(150),
    event_type             VARCHAR(100),
    perceived_urgency      VARCHAR(20),
    notes                  TEXT,
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_cases_status CHECK (status IN
        ('pendiente', 'clasificada', 'asignada', 'en_revision', 'respondida', 'cerrada')),
    CONSTRAINT chk_cases_priority CHECK (priority IN ('baja', 'media', 'alta', 'critica')),
    CONSTRAINT chk_cases_perceived_urgency CHECK (perceived_urgency IS NULL OR
        perceived_urgency IN ('baja', 'media', 'alta', 'critica'))
);

COMMENT ON TABLE cases IS 'Consulta médico-legal (HU-11). Terminología UI: "consulta". Flujo de 6 estados HU-16.';

CREATE INDEX idx_cases_doctor_id ON cases (doctor_id);
CREATE INDEX idx_cases_lawyer_id ON cases (lawyer_id);
CREATE INDEX idx_cases_status ON cases (status);
CREATE INDEX idx_cases_priority ON cases (priority);
CREATE INDEX idx_cases_medical_specialty ON cases (medical_specialty);

-- ============================================================================
-- 7. case_context — contexto clínico-legal SIMULADO (HU-12, Ley 29733)
--    Reemplaza a `patients` + `clinical_episodes`. SIN datos identificables:
--    sin DNI, sin nombre, sin fecha de nacimiento. Identificador = código/alias.
-- ============================================================================
CREATE TABLE case_context (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    case_id          UUID NOT NULL UNIQUE REFERENCES cases (id) ON DELETE CASCADE,
    context_code     VARCHAR(50)  NOT NULL,
    age_reference    INTEGER,
    medical_area     VARCHAR(150) NOT NULL,
    event_date       DATE,
    summary          TEXT         NOT NULL DEFAULT '',
    relevant_factors TEXT[]       NOT NULL DEFAULT '{}',
    is_simulated     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_case_context_age CHECK (age_reference IS NULL OR age_reference BETWEEN 0 AND 120),
    CONSTRAINT chk_case_context_simulated CHECK (is_simulated = TRUE)
);

COMMENT ON TABLE case_context IS 'Contexto clínico-legal simulado y NO identificable (Ley 29733). context_code es un alias tipo "Caso-001", nunca un dato real.';
COMMENT ON CONSTRAINT chk_case_context_simulated ON case_context IS 'Garantía a nivel BD de que solo se almacenan datos simulados.';

-- ============================================================================
-- 8-10. Documentos (repositorio documental con trazabilidad)
-- ============================================================================
CREATE TABLE documents (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title              VARCHAR(255) NOT NULL,
    type               VARCHAR(40)  NOT NULL,
    status             VARCHAR(20)  NOT NULL DEFAULT 'borrador',
    author_id          UUID         NOT NULL REFERENCES profiles (id) ON DELETE RESTRICT,
    case_id            UUID REFERENCES cases (id) ON DELETE SET NULL,
    current_version_id UUID,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_documents_type CHECK (type IN
        ('historia_clinica', 'consentimiento_informado', 'informe_medico', 'receta',
         'orden_laboratorio', 'certificado_medico', 'documento_legal', 'otro')),
    CONSTRAINT chk_documents_status CHECK (status IN
        ('borrador', 'pendiente_firma', 'firmado', 'archivado'))
);

CREATE INDEX idx_documents_author_id ON documents (author_id);
CREATE INDEX idx_documents_case_id ON documents (case_id);
CREATE INDEX idx_documents_status ON documents (status);

CREATE TABLE document_versions (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id UUID    NOT NULL REFERENCES documents (id) ON DELETE CASCADE,
    version     INTEGER NOT NULL DEFAULT 1,
    content     TEXT    NOT NULL DEFAULT '',
    file_url    TEXT,
    notes       TEXT,
    created_by  UUID    NOT NULL REFERENCES profiles (id) ON DELETE RESTRICT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_document_versions UNIQUE (document_id, version)
);

CREATE INDEX idx_document_versions_document_id ON document_versions (document_id);

-- FK circular documents ↔ document_versions: se agrega después de crear ambas
ALTER TABLE documents
    ADD CONSTRAINT fk_documents_current_version
        FOREIGN KEY (current_version_id) REFERENCES document_versions (id) ON DELETE SET NULL;

CREATE TABLE document_signatures (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id UUID        NOT NULL REFERENCES documents (id) ON DELETE CASCADE,
    signer_id   UUID        NOT NULL REFERENCES profiles (id) ON DELETE RESTRICT,
    type        VARCHAR(30) NOT NULL,
    hash        VARCHAR(128),
    is_valid    BOOLEAN     NOT NULL DEFAULT TRUE,
    signed_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_signatures_type CHECK (type IN ('digital', 'huella', 'firma_manuscrita'))
);

CREATE INDEX idx_document_signatures_document_id ON document_signatures (document_id);

-- ============================================================================
-- 11-12. Matching médico-abogado
-- ============================================================================
CREATE TABLE contact_requests (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    from_doctor_id   UUID        NOT NULL REFERENCES profiles (id) ON DELETE CASCADE,
    to_lawyer_id     UUID        NOT NULL REFERENCES profiles (id) ON DELETE CASCADE,
    case_id          UUID REFERENCES cases (id) ON DELETE SET NULL,
    case_title       VARCHAR(255),
    message          TEXT        NOT NULL,
    status           VARCHAR(20) NOT NULL DEFAULT 'pendiente',
    ml_score         NUMERIC(5, 2),
    response_message TEXT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_contact_requests_status CHECK (status IN
        ('pendiente', 'aceptado', 'rechazado', 'cancelado'))
);

CREATE INDEX idx_contact_requests_from_doctor ON contact_requests (from_doctor_id);
CREATE INDEX idx_contact_requests_to_lawyer ON contact_requests (to_lawyer_id);
CREATE INDEX idx_contact_requests_status ON contact_requests (status);

CREATE TABLE match_recommendations (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    doctor_id         UUID          NOT NULL REFERENCES profiles (id) ON DELETE CASCADE,
    lawyer_id         UUID          NOT NULL REFERENCES profiles (id) ON DELETE CASCADE,
    case_id           UUID REFERENCES cases (id) ON DELETE SET NULL,
    score             NUMERIC(5, 2) NOT NULL,
    reasons           TEXT[]        NOT NULL DEFAULT '{}',
    factors           JSONB         NOT NULL DEFAULT '[]',
    algorithm_version VARCHAR(50)   NOT NULL DEFAULT 'v1',
    is_accepted       BOOLEAN,
    feedback_at       TIMESTAMPTZ,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now()
);

COMMENT ON COLUMN match_recommendations.factors IS 'Factores de explicación XAI del matching (HU-31/32): [{factor, weight, description}].';

CREATE INDEX idx_match_recommendations_doctor ON match_recommendations (doctor_id);
CREATE INDEX idx_match_recommendations_lawyer ON match_recommendations (lawyer_id);

-- ============================================================================
-- 13. legal_responses — respuesta legal preliminar (HU-21/22)
-- ============================================================================
CREATE TABLE legal_responses (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    case_id         UUID NOT NULL REFERENCES cases (id) ON DELETE CASCADE,
    lawyer_id       UUID NOT NULL REFERENCES profiles (id) ON DELETE RESTRICT,
    content         TEXT NOT NULL,
    recommendations TEXT,
    observations    TEXT,
    is_reviewed     BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE legal_responses IS 'Orientación médico-legal preliminar del abogado (HU-21); is_reviewed = marcada como revisada por el médico (HU-22).';

CREATE INDEX idx_legal_responses_case_id ON legal_responses (case_id);

-- ============================================================================
-- 14. case_events — línea de tiempo de eventos simulados (HU-26/27)
-- ============================================================================
CREATE TABLE case_events (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    case_id      UUID         NOT NULL REFERENCES cases (id) ON DELETE CASCADE,
    event_date   DATE         NOT NULL,
    event_type   VARCHAR(100) NOT NULL,
    description  TEXT         NOT NULL DEFAULT '',
    is_simulated BOOLEAN      NOT NULL DEFAULT TRUE,
    created_by   UUID         NOT NULL REFERENCES profiles (id) ON DELETE RESTRICT,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_case_events_simulated CHECK (is_simulated = TRUE)
);

CREATE INDEX idx_case_events_case_id ON case_events (case_id);

-- ============================================================================
-- 15. ml_classifications — clasificación ML de la consulta (HU-29)
-- ============================================================================
CREATE TABLE ml_classifications (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    case_id             UUID NOT NULL REFERENCES cases (id) ON DELETE CASCADE,
    case_type           VARCHAR(100),
    urgency             VARCHAR(20),
    complexity          VARCHAR(20),
    suggested_specialty VARCHAR(150),
    confidence          NUMERIC(5, 4),
    model_version       VARCHAR(50),
    response_time_ms    INTEGER,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_ml_classifications_urgency CHECK (urgency IS NULL OR
        urgency IN ('baja', 'media', 'alta', 'critica')),
    CONSTRAINT chk_ml_classifications_complexity CHECK (complexity IS NULL OR
        complexity IN ('baja', 'media', 'alta'))
);

CREATE INDEX idx_ml_classifications_case_id ON ml_classifications (case_id);

-- ============================================================================
-- 16. model_metrics — métricas del modelo ML (HU-35, validación OE4)
-- ============================================================================
CREATE TABLE model_metrics (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    model_name              VARCHAR(100) NOT NULL,
    model_version           VARCHAR(50)  NOT NULL,
    precision_score         NUMERIC(5, 4),
    recall_score            NUMERIC(5, 4),
    f1_score                NUMERIC(5, 4),
    matching_relevance_rate NUMERIC(5, 4),
    avg_response_time_ms    INTEGER,
    dataset_size            INTEGER,
    notes                   TEXT,
    evaluated_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE model_metrics IS 'Métricas registradas del modelo ML (HU-35): precisión, recall, F1, % matching pertinente, tiempo de respuesta.';

-- ============================================================================
-- 17. audit_logs — bitácora de auditoría (HU-10/42)
-- ============================================================================
CREATE TABLE audit_logs (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID REFERENCES profiles (id) ON DELETE SET NULL,
    action      VARCHAR(50)  NOT NULL,
    resource    VARCHAR(100) NOT NULL,
    resource_id VARCHAR(100),
    details     JSONB        NOT NULL DEFAULT '{}',
    ip_address  VARCHAR(60),
    user_agent  TEXT,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_logs_user_id ON audit_logs (user_id);
CREATE INDEX idx_audit_logs_created_at ON audit_logs (created_at DESC);
