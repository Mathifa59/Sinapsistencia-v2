# Mocks de desarrollo

Simulan los servicios externos para desarrollar/demostrar sin infraestructura real:

- **mock-ml.js** — Servicio ML (FastAPI) en `:8000`: `/health`, `/api/v1/model/info`,
  `/api/v1/risk-assessment` (responde según `expected_level` del body: bajo/medio/alto/critico)
  y `/api/v1/recommendations` (devuelve al abogado demo con factores XAI).
- **mock-n8n.js** — n8n en `:5678`: registra los webhooks `/webhook/risk-alert` recibidos
  en `C:/Temp/n8n-received.log`.

Uso: `node tools/mocks/mock-ml.js` y `node tools/mocks/mock-n8n.js`.
Para probar resiliencia, basta matarlos: el backend debe seguir operando
(health → offline, risk → 503, matching → fallback por especialidad).
