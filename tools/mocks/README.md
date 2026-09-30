# Mocks de desarrollo

Simulan los servicios externos para desarrollar/demostrar sin infraestructura real:

- **mock-ml.js** — Servicio ML (FastAPI) en `:8000`: `/health`, `/api/v1/model/info`,
  `/api/v1/risk-assessment` (responde según `expected_level` del body: bajo/medio/alto/critico)
  y `/api/v1/recommendations` (devuelve al abogado demo con factores XAI).

Uso: `node tools/mocks/mock-ml.js`.
Para probar resiliencia, basta matarlo: el backend debe seguir operando
(health → offline, risk → 503, matching → fallback por especialidad).

Resend (correo) no tiene mock local: es un servicio SaaS fijo (`api.resend.com`), no
autohospedable como n8n. Para probar el fallback de correo basta con dejar
`RESEND_API_KEY` vacío (comportamiento por defecto en dev) — los envíos se omiten con
`log.warn` y forgot-password devuelve el token en la respuesta.
