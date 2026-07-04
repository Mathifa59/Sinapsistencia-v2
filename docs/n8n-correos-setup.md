# Correos transaccionales con n8n + Gmail

Sinapsistencia dispara sus correos (recuperar contraseña, bienvenida, solicitudes
de contacto) hacia **n8n**, que los entrega vía **Gmail**. El backend arma el
asunto y el HTML; n8n solo los recibe y los envía. La comunicación es
_fire-and-forget_: si n8n está caído o no configurado, la app sigue funcionando.

```
Spring Boot  ──POST /webhook/sinapsistencia-mail──▶  n8n  ──▶  Gmail  ──▶  bandeja del usuario
 (MailNotifier)          { to, subject, html, type, ... }        (Send)
```

---

## 1. Crear el espacio en n8n Cloud

1. Regístrate en https://n8n.io → **Get started free** (14 días de trial, luego
   plan free suficiente para esto).
2. Elige el nombre de tu workspace. Tu URL pública será
   `https://TU-WORKSPACE.app.n8n.cloud`.
3. Esa base (`https://TU-WORKSPACE.app.n8n.cloud`) es la que irá en
   `N8N_WEBHOOK_URL` (paso 4).

> **¿Producción + flujo visual?** Sí, ambas. La URL es pública (Railway la
> alcanza) y cada envío queda registrado en la pestaña **Executions** con el
> canvas y el detalle de cada nodo.

---

## 2. Conectar Gmail en n8n Cloud (un clic)

En n8n Cloud **no necesitas Google Cloud Console**. Al agregar el nodo Gmail:

1. En el nodo **Gmail**, campo *Credential to connect with* → **Create new**.
2. Elige **"Sign in with Google"** (n8n Cloud usa su propio OAuth gestionado).
3. Autoriza con la cuenta de Gmail que **enviará** los correos. Listo.

> Self-hosted (local/Railway) sí requiere crear tu propio OAuth en Google Cloud
> Console. Como usas n8n Cloud, ignora eso.
>
> Alternativa sin OAuth (cualquier hosting): nodo **Send Email (SMTP)** con una
> [contraseña de aplicación de Gmail](https://myaccount.google.com/apppasswords)
> (host `smtp.gmail.com`, puerto `465`, SSL). Solo cambia el nodo de envío.

---

## 3. Crear el workflow

Puedes **importar** el JSON del final de este documento
(*Workflows → ⋮ → Import from File/Clipboard*) o armarlo a mano con 2 nodos:

### Nodo 1 — Webhook

| Campo           | Valor                          |
|-----------------|--------------------------------|
| HTTP Method     | `POST`                         |
| Path            | `sinapsistencia-mail`          |
| Respond         | `Immediately`                  |

El backend hace POST a `{N8N_WEBHOOK_URL}/webhook/sinapsistencia-mail` con este body:

```json
{
  "type": "password_reset",
  "to": "usuario@correo.com",
  "name": "Dra. Lucía Fernández",
  "subject": "Restablece tu contraseña — Sinapsistencia",
  "html": "<!DOCTYPE html> ...correo ya maquetado...",
  "token": "…",
  "resetLink": "https://…/reset-password?email=…&token=…"
}
```

> Los campos `type`, `token`, `resetLink`, `doctorName`, etc. son extra por si
> quieres personalizar en n8n. Para enviar basta con `to`, `subject` y `html`.

### Nodo 2 — Gmail (Send / Send a message)

| Campo         | Valor (expresión n8n)                       |
|---------------|---------------------------------------------|
| Resource      | `Message`                                   |
| Operation     | `Send`                                       |
| To            | `={{ $json.body.to }}`                       |
| Subject       | `={{ $json.body.subject }}`                  |
| Email Type    | `HTML`                                       |
| Message       | `={{ $json.body.html }}`                     |

Conecta **Webhook → Gmail**. Pulsa **Save** y activa el workflow (toggle
**Active**, arriba a la derecha) para que el webhook de producción funcione.

> Nota sobre `$json.body`: con "Respond Immediately" el payload llega bajo
> `body`. Si tu versión de n8n lo entrega en la raíz, usa `{{ $json.to }}` etc.
> Verifícalo con una ejecución de prueba (paso 5).

---

## 4. Conectar el backend a n8n

Configura estas variables de entorno del servicio backend
(Railway → backend → *Variables*, o tu `.env` local):

```bash
# Base del webhook (SIN el path — el backend agrega /webhook/sinapsistencia-mail)
N8N_WEBHOOK_URL=https://tuinstancia.app.n8n.cloud

# URL pública del frontend para los enlaces dentro de los correos
APP_FRONTEND_URL=https://sinapsistencia-v2.vercel.app
```

En local: `N8N_WEBHOOK_URL=http://localhost:5678` y
`APP_FRONTEND_URL=http://localhost:4200`.

> Si `N8N_WEBHOOK_URL` queda vacío, el backend **omite** los correos y
> `forgot-password` vuelve al modo prototipo (devuelve el token en la respuesta).

---

## 5. Probar

1. En n8n abre el workflow y pulsa **Listen for test event** (o **Execute
   workflow**).
2. Dispara un correo desde la app:
   - **Recuperar contraseña:** en el login → "¿Olvidaste tu contraseña?" →
     ingresa un email registrado (ej. `lawyer.demo@sinapsistencia.pe`).
   - **Bienvenida:** registra una cuenta nueva.
   - **Solicitud de contacto:** como médico, envía una solicitud a un abogado;
     como abogado, acéptala/recházala.
3. Verás la ejecución en n8n y el correo en la bandeja de entrada.

### Prueba directa con curl (sin la app)

```bash
curl -X POST http://localhost:5678/webhook/sinapsistencia-mail \
  -H "Content-Type: application/json" \
  -d '{"to":"TU_CORREO@gmail.com","subject":"Prueba Sinapsistencia","html":"<h1>Funciona ✅</h1>","type":"test"}'
```

---

## 6. Qué correo dispara cada evento

| Evento en la app                        | `type`                      | Destinatario         |
|-----------------------------------------|-----------------------------|----------------------|
| Solicitar recuperación de contraseña    | `password_reset`            | Usuario de la cuenta |
| Registro de nueva cuenta                | `welcome`                   | Nuevo usuario        |
| Médico envía solicitud de contacto      | `contact_request_received`  | Abogado destinatario |
| Abogado acepta/rechaza la solicitud     | `contact_request_answered`  | Médico solicitante   |

Todos se disparan de forma asíncrona (`@Async`) desde `MailNotifier`
(`backend/.../notifications/MailNotifier.java`). Las plantillas HTML viven en
`MailTemplates.java`.

---

## 7. Workflow para importar

Guarda esto como `sinapsistencia-mail.json` e impórtalo en n8n. Tras importar,
**reconecta la credencial de Gmail** (n8n no importa credenciales) y activa el
workflow.

```json
{
  "name": "Sinapsistencia — Correos",
  "nodes": [
    {
      "parameters": {
        "httpMethod": "POST",
        "path": "sinapsistencia-mail",
        "responseMode": "onReceived",
        "options": {}
      },
      "id": "webhook-mail",
      "name": "Webhook Correo",
      "type": "n8n-nodes-base.webhook",
      "typeVersion": 2,
      "position": [420, 300],
      "webhookId": "sinapsistencia-mail"
    },
    {
      "parameters": {
        "resource": "message",
        "operation": "send",
        "sendTo": "={{ $json.body.to }}",
        "subject": "={{ $json.body.subject }}",
        "emailType": "html",
        "message": "={{ $json.body.html }}",
        "options": {}
      },
      "id": "gmail-send",
      "name": "Gmail — Enviar",
      "type": "n8n-nodes-base.gmail",
      "typeVersion": 2,
      "position": [700, 300],
      "credentials": {
        "gmailOAuth2": { "id": "REEMPLAZA", "name": "Gmail account" }
      }
    }
  ],
  "connections": {
    "Webhook Correo": {
      "main": [[{ "node": "Gmail — Enviar", "type": "main", "index": 0 }]]
    }
  },
  "active": false,
  "settings": {}
}
```
