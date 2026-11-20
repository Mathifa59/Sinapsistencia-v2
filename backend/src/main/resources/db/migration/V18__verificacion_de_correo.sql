-- ============================================================================
-- V18: confirmación del correo electrónico y recuperación de contraseña
-- vinculada a ese correo.
--
-- Hasta V17 cualquier correo con formato válido podía registrarse y recibir un
-- token de recuperación sin que se probara que el titular controla el buzón.
-- Desde V18:
--   * profiles.email_verified_at -- momento en que el titular confirmó el correo
--     con el enlace enviado al registrarse. NULL = correo sin confirmar: no se
--     puede iniciar sesión ni recibir un token de recuperación de contraseña.
--   * email_verification_tokens -- tokens de un solo uso con caducidad. Solo se
--     guarda el hash SHA-256: una filtración de la tabla no permite confirmar
--     correos ajenos.
--   * password_reset_tokens.profile_id -- el token de recuperación queda atado
--     al perfil (y por tanto al correo confirmado) que lo solicitó, no solo a un
--     texto de correo.
--
-- Las cuentas que ya existen (semillas demo y usuarios previos) se consideran
-- confirmadas: de otro modo quedarían bloqueadas al desplegar esta migración.
-- ============================================================================

ALTER TABLE profiles
    ADD COLUMN email_verified_at TIMESTAMPTZ;

UPDATE profiles
   SET email_verified_at = created_at
 WHERE email_verified_at IS NULL;

CREATE TABLE email_verification_tokens (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    profile_id  UUID        NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    token_hash  VARCHAR(64) NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ NOT NULL,
    used_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_email_verification_tokens_profile
    ON email_verification_tokens (profile_id, created_at DESC);

ALTER TABLE password_reset_tokens
    ADD COLUMN profile_id UUID REFERENCES profiles(id) ON DELETE CASCADE;

CREATE INDEX idx_password_reset_tokens_profile
    ON password_reset_tokens (profile_id);
