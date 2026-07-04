package pe.sinapsistencia.notifications;

/**
 * Plantillas HTML de los correos transaccionales que dispara {@link MailNotifier}.
 *
 * El backend arma el asunto y el cuerpo aquí (versionable y testeable); n8n solo
 * recibe {@code subject}/{@code html}/{@code to} y los entrega vía Gmail. El HTML
 * usa estilos inline + layout de tablas para máxima compatibilidad con clientes
 * de correo (Gmail, Outlook, Apple Mail).
 */
final class MailTemplates {

	private static final String BRAND = "#0f172a"; // slate-900 (paleta del tema)
	private static final String ACCENT = "#6366f1"; // indigo-500
	private static final String MUTED = "#64748b"; // slate-500
	private static final String BORDER = "#e2e8f0"; // slate-200

	private MailTemplates() {
	}

	// ── Recuperación de contraseña ─────────────────────────────────────────────

	static String passwordReset(String name, String resetLink, String token) {
		String body = """
				<p style="margin:0 0 16px;font-size:15px;line-height:1.6;color:#0f172a;">
				  Hola <strong>%s</strong>, recibimos una solicitud para restablecer la contraseña de tu cuenta en Sinapsistencia.
				</p>
				<p style="margin:0 0 24px;font-size:15px;line-height:1.6;color:#0f172a;">
				  Haz clic en el botón para elegir una nueva contraseña. El enlace caduca en <strong>1 hora</strong>.
				</p>
				%s
				<p style="margin:24px 0 8px;font-size:13px;line-height:1.6;color:%s;">
				  Si el botón no funciona, copia y pega este enlace en tu navegador:
				</p>
				<p style="margin:0 0 24px;font-size:13px;line-height:1.6;word-break:break-all;">
				  <a href="%s" style="color:%s;">%s</a>
				</p>
				<p style="margin:0;font-size:13px;line-height:1.6;color:%s;">
				  ¿No fuiste tú? Puedes ignorar este correo; tu contraseña seguirá igual. Tu código de referencia es
				  <code style="background:#f1f5f9;padding:2px 6px;border-radius:4px;font-size:12px;">%s</code>.
				</p>
				""".formatted(
				esc(name), button("Restablecer contraseña", resetLink),
				MUTED, esc(resetLink), ACCENT, esc(resetLink), MUTED, esc(token));
		return shell("Restablece tu contraseña", body);
	}

	// ── Bienvenida ─────────────────────────────────────────────────────────────

	static String welcome(String name, String roleLabel, String panelLink) {
		String body = """
				<p style="margin:0 0 16px;font-size:15px;line-height:1.6;color:#0f172a;">
				  ¡Bienvenido/a, <strong>%s</strong>! 🎉
				</p>
				<p style="margin:0 0 24px;font-size:15px;line-height:1.6;color:#0f172a;">
				  Tu cuenta de <strong>%s</strong> en Sinapsistencia ya está activa. Desde tu panel podrás
				  gestionar consultas, documentación clínico-legal y conectar con el profesional adecuado.
				</p>
				%s
				<p style="margin:24px 0 0;font-size:13px;line-height:1.6;color:%s;">
				  Si no creaste esta cuenta, escríbenos respondiendo a este correo.
				</p>
				""".formatted(esc(name), esc(roleLabel), button("Ir a mi panel", panelLink), MUTED);
		return shell("Tu cuenta está lista", body);
	}

	// ── Solicitud de contacto recibida (→ abogado) ─────────────────────────────

	static String contactRequestReceived(String lawyerName, String doctorName, String caseTitle,
			String message, String panelLink) {
		String caseRow = caseTitle == null || caseTitle.isBlank() ? ""
				: infoRow("Consulta", esc(caseTitle));
		String body = """
				<p style="margin:0 0 16px;font-size:15px;line-height:1.6;color:#0f172a;">
				  Hola <strong>%s</strong>, tienes una nueva solicitud de contacto de un profesional médico.
				</p>
				<table role="presentation" width="100%%" cellpadding="0" cellspacing="0"
				  style="margin:0 0 24px;border:1px solid %s;border-radius:10px;overflow:hidden;">
				  %s
				  %s
				  <tr>
				    <td style="padding:14px 16px;font-size:14px;line-height:1.6;color:#0f172a;background:#f8fafc;">
				      <span style="display:block;font-size:12px;color:%s;margin-bottom:4px;">Mensaje</span>
				      %s
				    </td>
				  </tr>
				</table>
				%s
				""".formatted(
				esc(lawyerName), BORDER,
				infoRow("Solicitante", esc(doctorName)), caseRow,
				MUTED, esc(message), button("Ver solicitud", panelLink));
		return shell("Nueva solicitud de contacto", body);
	}

	// ── Solicitud respondida (→ médico) ────────────────────────────────────────

	static String contactRequestAnswered(String doctorName, String lawyerName, String caseTitle,
			boolean accepted, String responseMessage, String panelLink) {
		String verb = accepted ? "aceptó" : "no pudo aceptar";
		String badge = accepted
				? "<span style=\"display:inline-block;padding:4px 12px;border-radius:999px;background:#dcfce7;color:#166534;font-size:12px;font-weight:600;\">Aceptada</span>"
				: "<span style=\"display:inline-block;padding:4px 12px;border-radius:999px;background:#fee2e2;color:#991b1b;font-size:12px;font-weight:600;\">Rechazada</span>";
		String caseRow = caseTitle == null || caseTitle.isBlank() ? ""
				: infoRow("Consulta", esc(caseTitle));
		String msgBlock = responseMessage == null || responseMessage.isBlank() ? ""
				: """
						<table role="presentation" width="100%%" cellpadding="0" cellspacing="0"
						  style="margin:0 0 24px;border:1px solid %s;border-radius:10px;overflow:hidden;">
						  <tr><td style="padding:14px 16px;font-size:14px;line-height:1.6;color:#0f172a;background:#f8fafc;">
						    <span style="display:block;font-size:12px;color:%s;margin-bottom:4px;">Respuesta</span>%s
						  </td></tr>
						</table>
						""".formatted(BORDER, MUTED, esc(responseMessage));
		String body = """
				<p style="margin:0 0 16px;font-size:15px;line-height:1.6;color:#0f172a;">
				  Hola <strong>%s</strong>, el/la Abg. <strong>%s</strong> %s tu solicitud de contacto. %s
				</p>
				<table role="presentation" width="100%%" cellpadding="0" cellspacing="0"
				  style="margin:16px 0 24px;border:1px solid %s;border-radius:10px;overflow:hidden;">
				  %s
				</table>
				%s
				%s
				""".formatted(
				esc(doctorName), esc(lawyerName), verb, badge, BORDER,
				caseRow.isBlank() ? infoRow("Estado", accepted ? "Abogado asignado" : "Sin asignar") : caseRow,
				msgBlock, button("Ver en mi panel", panelLink));
		return shell(accepted ? "Tu solicitud fue aceptada" : "Respuesta a tu solicitud", body);
	}

	// ── Piezas compartidas ─────────────────────────────────────────────────────

	private static String shell(String heading, String innerBody) {
		return """
				<!DOCTYPE html>
				<html lang="es">
				<body style="margin:0;padding:0;background:#f1f5f9;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;">
				  <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background:#f1f5f9;padding:32px 16px;">
				    <tr><td align="center">
				      <table role="presentation" width="560" cellpadding="0" cellspacing="0"
				        style="max-width:560px;width:100%%;background:#ffffff;border-radius:16px;overflow:hidden;box-shadow:0 4px 24px rgba(15,23,42,0.08);">
				        <tr>
				          <td style="background:%s;padding:28px 32px;">
				            <span style="font-size:20px;font-weight:700;color:#ffffff;letter-spacing:-0.02em;">Sinapsistencia</span>
				            <span style="display:block;margin-top:2px;font-size:12px;color:#94a3b8;">Plataforma clínico-legal</span>
				          </td>
				        </tr>
				        <tr>
				          <td style="padding:32px;">
				            <h1 style="margin:0 0 20px;font-size:19px;font-weight:700;color:#0f172a;letter-spacing:-0.01em;">%s</h1>
				            %s
				          </td>
				        </tr>
				        <tr>
				          <td style="padding:20px 32px;border-top:1px solid %s;background:#f8fafc;">
				            <p style="margin:0;font-size:12px;line-height:1.6;color:%s;">
				              Este es un mensaje automático de Sinapsistencia. Contenido simulado con fines
				              académicos (Ley 29733). Por favor no compartas este correo.
				            </p>
				          </td>
				        </tr>
				      </table>
				    </td></tr>
				  </table>
				</body>
				</html>
				""".formatted(BRAND, esc(heading), innerBody, BORDER, MUTED);
	}

	private static String button(String label, String href) {
		return """
				<table role="presentation" cellpadding="0" cellspacing="0" style="margin:0;">
				  <tr><td style="border-radius:10px;background:%s;">
				    <a href="%s" style="display:inline-block;padding:13px 28px;font-size:15px;font-weight:600;color:#ffffff;text-decoration:none;border-radius:10px;">%s</a>
				  </td></tr>
				</table>
				""".formatted(ACCENT, esc(href), esc(label));
	}

	private static String infoRow(String label, String value) {
		return """
				<tr>
				  <td style="padding:12px 16px;border-bottom:1px solid %s;font-size:14px;color:#0f172a;">
				    <span style="display:block;font-size:12px;color:%s;margin-bottom:2px;">%s</span>%s
				  </td>
				</tr>
				""".formatted(BORDER, MUTED, esc(label), value);
	}

	/** Escapa HTML para evitar inyección al interpolar datos de usuario en las plantillas. */
	private static String esc(String raw) {
		if (raw == null) {
			return "";
		}
		return raw.replace("&", "&amp;")
				.replace("<", "&lt;")
				.replace(">", "&gt;")
				.replace("\"", "&quot;")
				.replace("'", "&#39;");
	}
}
