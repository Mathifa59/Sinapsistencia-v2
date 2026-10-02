-- ============================================================================
-- V17: H-06 -- outbox de notificaciones. Hoy un correo rechazado (403 dominio
-- no verificado, timeout, 5xx) desaparece sin dejar rastro: MailNotifier y
-- RiskAlertNotifier son @Async fire-and-forget, loguean con log.error y ahi
-- termina -- el medico/abogado nunca sabe que su aviso no llego, y nadie
-- puede confirmar luego si se reintento ni cuantas veces.
--
-- notification_outbox persiste CADA intento de aviso como una fila propia:
-- se crea en la transaccion de negocio (ContactRequestService/
-- CaseClassificationService, mismo hallazgo) y un worker aparte la despacha
-- DESPUES del commit -- un rollback de la operacion de negocio se lleva
-- tambien su fila de outbox, nunca queda un aviso de algo que no paso.
--
-- El Reply-To dinamico que ya existe (medico<->abogado se corresponden
-- directo, sin pasar por el admin) deja de viajar como argumento de una
-- llamada directa: pasa a ser la columna reply_to de cada fila, fijada al
-- encolar.
-- ============================================================================

CREATE TABLE notification_outbox (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_key              VARCHAR(200) NOT NULL,
    type                   VARCHAR(50) NOT NULL,
    case_id                UUID REFERENCES cases(id) ON DELETE SET NULL,
    resource_type          VARCHAR(50) NOT NULL,
    resource_id            UUID NOT NULL,
    recipient              TEXT NOT NULL,
    reply_to               TEXT,
    subject                TEXT NOT NULL,
    payload                JSONB NOT NULL,
    status                 VARCHAR(30) NOT NULL DEFAULT 'pending',
    attempt_count          INTEGER NOT NULL DEFAULT 0,
    next_attempt_at        TIMESTAMPTZ,
    locked_until           TIMESTAMPTZ,
    worker_token           UUID,
    provider_message_id    VARCHAR(200),
    provider_http_status   INTEGER,
    last_error_code        VARCHAR(100),
    last_error_message     TEXT,
    retryable              BOOLEAN NOT NULL DEFAULT false,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    accepted_at            TIMESTAMPTZ,

    CONSTRAINT uq_notification_outbox_event_key UNIQUE (event_key),
    CONSTRAINT chk_notification_outbox_status
        CHECK (status IN ('pending', 'processing', 'accepted_by_provider', 'failed', 'skipped')),
    CONSTRAINT chk_notification_outbox_attempt_count_non_negative
        CHECK (attempt_count >= 0)
);

-- Reclamo del worker: lotes pequeños listos para despachar.
CREATE INDEX idx_notification_outbox_status_next_attempt
    ON notification_outbox (status, next_attempt_at);

-- Historial por caso (GET /api/legal-cases/{id}/notifications).
CREATE INDEX idx_notification_outbox_case_id_created_at
    ON notification_outbox (case_id, created_at DESC);

-- Consulta por el recurso que origino el aviso (solicitud o clasificacion).
CREATE INDEX idx_notification_outbox_resource
    ON notification_outbox (resource_type, resource_id);

COMMENT ON TABLE notification_outbox IS
    'H-06: una fila por intento de aviso, creada en la transaccion de negocio y despachada DESPUES del commit por NotificationWorker. recipient/reply_to/subject/payload son privados -- nunca se exponen por el GET publico de estado.';
COMMENT ON COLUMN notification_outbox.event_key IS
    'Clave estable de deduplicacion: contact_request_received:<requestId> | contact_request_answered:<requestId>:<status> | risk_alert:<classificationId>. Unica -- reintentar el mismo evento nunca crea una segunda fila.';
COMMENT ON COLUMN notification_outbox.resource_type IS
    'contact_request | ml_classification -- junto a resource_id identifica el recurso de negocio asociado (una FK unica no puede apuntar a tablas distintas; la relacion se valida en servicio).';
COMMENT ON COLUMN notification_outbox.payload IS
    'Fotografia del body HTTP efectivo (incluido el remitente resuelto de configuracion) fijada en el primer intento -- un cambio posterior de MAIL_FROM no genera otro body bajo la misma clave de idempotencia del proveedor.';
COMMENT ON COLUMN notification_outbox.status IS
    'pending -> processing -> accepted_by_provider | failed | skipped. delivered/bounced no existen aqui: sin webhook validado, "aceptado por el proveedor" es la entrega mas fuerte que se puede afirmar.';
COMMENT ON COLUMN notification_outbox.worker_token IS
    'Lease del worker que reclamo esta fila (junto a locked_until) -- evita que un worker viejo, tras expirar su lease, sobrescriba la finalizacion de otro.';
COMMENT ON COLUMN notification_outbox.recipient IS
    'Cadena vacia + status=skipped cuando no hay destinatario -- nunca un destinatario inventado.';
