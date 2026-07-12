# Seguridad y Cumplimiento — Sinapsistencia

Sustento de las medidas de seguridad y de protección de datos personales de la
plataforma, mapeadas a la **Ley N.º 29733** (Protección de Datos Personales,
Perú). Redactado para defensa de tesis: qué está implementado, dónde vive en el
código y qué queda declarado como trabajo futuro.

---

## 1. Autenticación y sesión

| Medida | Implementación |
|--------|----------------|
| Token de sesión | **JWT firmado (HS384)** con expiración de 24 h (`JWT_EXPIRATION`) |
| Transporte del token | Cookie **httpOnly** (`access_token`): inaccesible para JavaScript → mitiga XSS. `SameSite=Lax` mitiga CSRF. `Secure` en producción (HTTPS) |
| Contraseñas | **BCrypt** (hash adaptativo con salt, nunca en claro). Mínimo 8 caracteres |
| Recuperación | Token aleatorio de un solo uso (SecureRandom, 24 bytes), caduca en 1 h, viaja **solo por correo** (n8n); la API no lo expone cuando el correo está configurado |
| Secretos | Vía variables de entorno (`JWT_SECRET`, `DB_PASSWORD`, `CLOUDINARY_URL`); nunca en el repositorio. Rotación: cambiar la variable en Railway y redesplegar (invalida sesiones activas) |

## 2. Protección contra fuerza bruta (rate limiting)

`LoginAttemptService` — ventana deslizante en memoria:

- Máximo **5 intentos fallidos por cuenta en 15 minutos**.
- Superado el límite, el login responde **HTTP 429** con el tiempo de espera
  restante; un login exitoso resetea el contador.
- La clave es el email normalizado (protege la *cuenta*, que es el objetivo del
  ataque de credenciales); límites por IP corresponden a la capa de red/proxy.
- En memoria por diseño (una instancia en Railway); con réplicas se movería a
  Redis — decisión documentada, no omisión.

## 3. Autorización (ownership)

- Roles `doctor` / `lawyer` / `admin` con reglas de propiedad en **cada** servicio:
  el médico solo ve sus casos/documentos; el abogado, los casos que tiene
  asignados; admin todo (verificado por `OwnershipIntegrationTest`).
- Endpoints administrativos protegidos con `@PreAuthorize("hasRole('ADMIN')")`.

## 4. Ley N.º 29733 — mapeo de cumplimiento

| Principio de la ley | Medida en la plataforma |
|---------------------|-------------------------|
| **Consentimiento** (art. 5) | Checkbox obligatorio al registrarse + **registro auditable** en `user_consents` (tipo, versión de política y fecha) — V10 |
| **Finalidad** | Los datos del profesional se usan solo para intermediación y gestión de casos (declarado en el registro) |
| **Proporcionalidad / minimización** | Los casos NO registran datos del paciente: se usa el **contexto simulado** (código de referencia, edad referencial, área) |
| **Prevención activa** | **Detector heurístico de PII** en los campos de texto libre al crear un caso: DNI (8 dígitos), teléfonos (+51/9xxxxxxxx), correos y nombres propios tras "paciente/Sr./Sra.". Si detecta algo, exige anonimizar o confirmar explícitamente que no son datos reales |
| **Trazabilidad** | Auditoría completa (`audit_logs`, ver §5) y consentimientos versionados |
| **Seguridad** (art. 16) | Medidas de §1–§3 + cifrado en tránsito (HTTPS en Vercel/Railway) y en reposo (Postgres gestionado) |
| **Retención / supresión** | *Trabajo futuro declarado*: política de retención con plazos por tipo de dato y derecho de supresión (ARCO). El soft-delete de solicitudes (`cancelado`) y la desactivación de cuentas (`is_active`) son la base |

## 5. Auditoría — argumento central de la plataforma 🏛️

Para una plataforma **médico-legal**, la trazabilidad no es un extra: es el
producto. Todo lo relevante queda registrado y es consultable por el admin:

- **`audit_logs`** — cada acción sensible (login, logout, creación/edición de
  casos, solicitudes de contacto, cambios de usuarios) se registra vía AOP
  (`@Auditable` + `AuditAspect`) con usuario, acción, recurso y fecha, sin
  ensuciar la lógica de negocio.
- **`case_events`** — línea de tiempo inmutable por caso (quién hizo qué y cuándo),
  incluida la clasificación automática del ML (transparencia algorítmica).
- **Firmas de documentos** — hash **SHA-256 real** del contenido de la versión
  firmada: cualquier alteración posterior es detectable (integridad probatoria).
- **`user_consents`** — evidencia de consentimiento con versión de política.
- **`match_recommendations`** — cada recomendación del ML queda persistida con
  sus factores explicativos (XAI): se puede reconstruir *por qué* el sistema
  recomendó a un abogado en una fecha dada.

> **Frase para la sustentación:** "En Sinapsistencia toda decisión —humana o
> algorítmica— deja evidencia auditable: quién, qué, cuándo y por qué."

## 6. Transparencia algorítmica

- Nota ética HU-43 visible junto a **cada** salida del ML (apoyo, no decisión).
- El análisis de riesgo muestra las variables usadas y el desglose por factor.
- El matching muestra la fórmula del score compuesto y las razones de cada
  recomendación.

## 7. Trabajo futuro declarado

1. Política formal de **retención y supresión** de datos (derechos ARCO).
2. Rate limiting **por IP** en la capa de proxy + CAPTCHA tras umbral.
3. Rotación **automática** de secretos y 2FA para cuentas admin.
4. Cifrado a nivel de campo para datos especialmente sensibles.
5. Mover el rate limiting a Redis al escalar horizontalmente.
