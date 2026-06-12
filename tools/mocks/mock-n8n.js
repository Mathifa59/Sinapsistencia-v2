// Mock de n8n: registra los webhooks recibidos en C:/Temp/n8n-received.log
const http = require("http");
const fs = require("fs");

const LOG = "C:/Temp/n8n-received.log";
fs.writeFileSync(LOG, "");

http.createServer((req, res) => {
  let body = "";
  req.on("data", (c) => (body += c));
  req.on("end", () => {
    if (req.url === "/webhook/risk-alert" && req.method === "POST") {
      const payload = JSON.parse(body || "{}");
      fs.appendFileSync(LOG, JSON.stringify({ at: new Date().toISOString(), riskLevel: payload.riskLevel, doctorName: payload.doctorName, caseId: payload.caseId }) + "\n");
      res.end(JSON.stringify({ ok: true }));
    } else {
      res.statusCode = 404;
      res.end();
    }
  });
}).listen(5678, () => console.log("mock n8n en :5678"));
