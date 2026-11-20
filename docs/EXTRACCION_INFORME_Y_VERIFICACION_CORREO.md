# Extracción de campos desde la imagen de un informe y confirmación de correo

Cambios de la rama `feat/informe-ocr-y-verificacion-correo` (2026-11-19). Nacen de la sesión de
validación con un médico, donde se pidió acortar el formulario de caso, quitar el código que se
genera solo y que el médico escriba lo mínimo.

## 1. Código de referencia fuera del formulario

El campo "Código de referencia" se quitó del formulario: el sistema ya lo asigna y el médico
podía creer que debía escribirlo. El código sigue existiendo internamente (se ve en listados y
detalle del caso). El "Área médica" del contexto simulado dejó de ser un segundo campo: es la
especialidad del caso.

## 2. Lectura del informe (OCR local) y formulario por pasos

**Flujo.** Paso 1: el médico sube la foto de un informe (opcional). Paso 2 y 3: se muestran los
campos ya llenados, cada uno con su origen, y se piden a mano solo los que el informe no permite.

| Origen | Significado |
|---|---|
| Leído del informe | El informe lo dice de forma explícita ("Especialidad: Cirugía General"). |
| Sugerido: confírmalo | Se dedujo por palabras clave (p. ej. complejidad "alta" por "paro cardiorrespiratorio"). |
| No está en el informe | El médico lo completa. |

**Dónde corre.** `POST /api/legal-cases/extract-from-report` (solo médicos) reenvía la imagen al
servicio ML (`POST /api/v1/extract-case-report`), que lee el texto con RapidOCR (ONNX) y aplica
reglas deterministas (`ml-service/app/extraction/`). Versión del extractor: `extract-v1`.

**Decisiones.**

- **OCR local, no un servicio externo.** Un informe puede traer datos identificables (Ley 29733):
  la imagen no sale a terceros, se procesa en memoria y no se guarda. Costo: ~15 MB de modelos y
  dependencias (`onnxruntime`, `opencv`) más en el servicio ML.
- **Reglas, no un modelo.** Auditables y reproducibles; no hay aleatoriedad ni entrenamiento.
- **Ante la duda, `missing`.** Consentimiento informado, documentación completa, quejas previas y
  complejidad alimentan al clasificador: nunca se adivinan. En el formulario son Sí/No explícitos,
  sin valor por omisión (antes venían marcados "Sí, Sí, No").
- **La urgencia percibida nunca se extrae.** Es el juicio del médico.
- **Las 7 variables del clasificador no cambian** (contrato 4.1): solo cambia cómo se obtienen.
- **Datos personales.** El servicio reemplaza DNI, teléfono, correo, historia clínica y nombres
  tras "paciente/Sr./Sra." por una marca antes de devolver el texto, e informa el tipo omitido
  (nunca el valor). Es heurístico: el médico revisa y el formulario conserva su propio detector.
- **Degradación.** Si el ML o el OCR no están disponibles, el endpoint responde 503 con
  "completa el formulario manualmente" y el alta de casos sigue funcionando a mano.

**Limitaciones conocidas.**

- El motor entrega el texto **sin tildes** ("cirugia", "hemorragia"); el parser las ignora, pero
  el título y la descripción propuestos las llevan sin tilde hasta que el médico los edite.
- Entre 6 y 8 s por informe tras el precalentamiento (21 s la primera lectura sin precalentar),
  medido en un equipo de desarrollo, solo CPU y con el resto de servicios activos.
- Solo imágenes (PNG, JPG, WEBP, BMP, hasta 8 MB); no PDF.
- **No hay medición de exactitud.** No existe un conjunto de informes etiquetados. Las pruebas
  (`ml-service/tests/test_case_report_extraction.py`) verifican el comportamiento sobre textos
  armados a mano y sobre un informe **simulado** (`tests/fixtures/informe_simulado.png`); no deben
  citarse como exactitud, precisión ni cobertura. Con fotos reales de papel (inclinación, sombras,
  manuscrito) el resultado puede ser peor y **no se ha probado**.

## 3. Confirmación del correo y recuperación vinculada (V18)

- Al registrarse, la cuenta queda **sin confirmar** y se envía un enlace de un solo uso (24 h) al
  correo. Sin confirmar no se puede iniciar sesión (403 con aviso y opción de reenviar el enlace).
  La comprobación va **después** de validar la contraseña: quien no la conoce no averigua si el
  correo existe.
- El token se guarda **solo como hash SHA-256** (`email_verification_tokens`). Viaja únicamente en
  el correo (o en el log del servidor con el perfil `local`, como el token de recuperación).
- `POST /api/auth/verify-email` y `POST /api/auth/resend-verification` (respuesta idéntica exista o
  no la cuenta, espera mínima de 60 s entre envíos).
- **Recuperación de contraseña vinculada al correo confirmado:** solo se emite token si el correo
  está confirmado; si no, se reenvía el enlace de confirmación (misma respuesta genérica). El token
  queda atado al perfil (`password_reset_tokens.profile_id`) y al restablecer se exige que el
  correo siga confirmado.
- **Cuentas existentes** (semillas demo y usuarios previos): V18 las marca confirmadas; de lo
  contrario quedarían bloqueadas al desplegar.
- La bienvenida se envía al confirmar, no al registrarse.

## 4. Pruebas (2026-11-19)

| Capa | Resultado |
|---|---|
| Backend (`mvnw test`, con Testcontainers) | 60 de 60 (49 previas + 5 de confirmación de correo + 6 de extracción) |
| Servicio ML (`pytest`) | 23 de 23 (7 previas + 16 del extractor) |
| Frontend (Vitest) | 14 de 14 (solo `cn.spec.ts`); `ng build` sin errores |

Verificación manual en local (perfil `local`, base `sinapsistencia_vrenato`): registro → login
bloqueado → enlace → login; subida del informe simulado → formulario precargado → caso creado y
clasificado con `rf-v2`. No son los casos CP001–CP090 ni miden cobertura. El componente del
formulario no tiene prueba automática propia.
