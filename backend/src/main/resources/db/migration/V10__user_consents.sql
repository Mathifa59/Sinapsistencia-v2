-- ============================================================================
-- V10: Registro de consentimiento de tratamiento de datos (Ley N.º 29733).
--
-- Cada aceptación de política queda registrada con tipo, versión y fecha:
-- evidencia auditable de que el titular consintió el tratamiento de sus datos
-- personales al registrarse (art. 5 — principio de consentimiento).
-- ============================================================================

CREATE TABLE user_consents (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id        UUID         NOT NULL REFERENCES profiles (id) ON DELETE CASCADE,
    consent_type   VARCHAR(50)  NOT NULL,
    policy_version VARCHAR(20)  NOT NULL,
    accepted_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_user_consents_user_id ON user_consents (user_id);

COMMENT ON TABLE user_consents IS 'Consentimientos registrados por usuario (Ley 29733): tipo de consentimiento, versión de la política aceptada y fecha.';
