-- Tokens de recuperación de contraseña (HU-04) y métricas ML iniciales (HU-35)
CREATE TABLE password_reset_tokens (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email      VARCHAR(255) NOT NULL,
    token      VARCHAR(64)  NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ  NOT NULL,
    used       BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_password_reset_tokens_email ON password_reset_tokens (email);

INSERT INTO model_metrics (model_name, model_version, precision_score, recall_score, f1_score,
                           matching_relevance_rate, avg_response_time_ms, dataset_size, notes)
VALUES ('risk_classifier', 'rf-v1', 0.6179, 0.5973, 0.6034, NULL, 450, 6000,
        'Random Forest: clasificación risk_level + regresión risk_score (dataset sintético)'),
       ('matching_tfidf', 'v1', NULL, NULL, NULL, 0.7200, 320, NULL,
        'TF-IDF + similitud coseno para matching médico-abogado');
