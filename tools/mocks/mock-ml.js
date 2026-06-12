// Mock del servicio ML (FastAPI) para verificar el proxy de Spring.
const http = require("http");

http.createServer((req, res) => {
  let body = "";
  req.on("data", (c) => (body += c));
  req.on("end", () => {
    res.setHeader("Content-Type", "application/json");

    if (req.url === "/health") {
      res.end(JSON.stringify({ status: "ok", service: "sinapsistencia-ml", version: "1.2.0" }));
    } else if (req.url === "/api/v1/model/info") {
      res.end(JSON.stringify({
        status: "loaded", model_version: "v2.1",
        content_model: "tfidf-cosine", collaborative_model: "als", risk_model: "xgboost",
      }));
    } else if (req.url === "/api/v1/risk-assessment" && req.method === "POST") {
      const input = JSON.parse(body || "{}");
      const level = input.expected_level || "bajo";
      const score = { bajo: 0.2, medio: 0.5, alto: 0.78, critico: 0.95 }[level] ?? 0.2;
      res.end(JSON.stringify({
        case_id: input.case_id || "caso-test",
        risk_score: score,
        risk_level: level,
        risk_factors: [
          { name: "documentacion_incompleta", weight: 0.4, value: 1, contribution: 0.31, description: "Documentación clínica incompleta" },
          { name: "consentimiento", weight: 0.3, value: 0, contribution: 0.12, description: "Consentimiento informado presente" },
        ],
        recommendations: ["Completar la historia clínica", "Revisar el consentimiento"],
        specialty_risk_baseline: 0.35,
        model_version: "v2.1",
      }));
    } else if (req.url === "/api/v1/recommendations" && req.method === "POST") {
      res.end(JSON.stringify({
        recommendations: [{
          lawyer_id: "d0000000-0000-0000-0000-000000000002",
          score: 0.92, content_score: 0.88, collaborative_score: 0.75,
          matched_specialties: ["Cirugía General"],
          model_used: "hybrid-v2",
          feature_importance: [
            { feature: "especialidad_match", importance: 0.45, description: "Coincidencia de especialidad médica" },
            { feature: "experiencia", importance: 0.30, description: "Años de experiencia en derecho médico" },
            { feature: "rating", importance: 0.25, description: "Valoración de casos previos" },
          ],
          reasons: ["Especialista en tu área médica", "15 años de experiencia", "Valoración 4.8/5"],
        }],
        model_info: { model: "hybrid-v2", trained_at: "2026-05-01" },
      }));
    } else {
      res.statusCode = 404;
      res.end(JSON.stringify({ detail: "not found" }));
    }
  });
}).listen(8000, () => console.log("mock ML en :8000"));
