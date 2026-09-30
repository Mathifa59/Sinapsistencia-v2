package pe.sinapsistencia.ml.application;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import pe.sinapsistencia.notifications.MailTemplates;
import pe.sinapsistencia.notifications.ResendClient;

/**
 * HU-31: alerta por correo a un administrador cuando un caso se clasifica con
 * riesgo alto o crítico. Semántica fire-and-forget: {@code @Async}, nunca lanza;
 * si no hay API key de Resend o no hay destinatario configurado, se omite con
 * {@code log.warn} y el resto de la app sigue funcionando con normalidad.
 */
@Service
public class RiskAlertNotifier {

	private static final Logger log = LoggerFactory.getLogger(RiskAlertNotifier.class);

	private final ResendClient resendClient;
	private final String riskAlertTo;

	public RiskAlertNotifier(
			ResendClient resendClient,
			@Value("${app.resend.risk-alert-to:}") String riskAlertTo) {
		this.resendClient = resendClient;
		this.riskAlertTo = riskAlertTo == null ? "" : riskAlertTo.strip();
	}

	@Async
	public void triggerRiskAlert(Map<String, Object> alert) {
		if (!resendClient.isConfigured()) {
			log.warn("[risk-alert] RESEND_API_KEY no configurada — alerta omitida (riskLevel={})",
					alert.get("riskLevel"));
			return;
		}
		if (riskAlertTo.isBlank()) {
			log.warn("[risk-alert] RISK_ALERT_EMAIL no configurada — alerta omitida (riskLevel={})",
					alert.get("riskLevel"));
			return;
		}
		try {
			String riskLevel = String.valueOf(alert.get("riskLevel"));
			resendClient.send(riskAlertTo, "Alerta de riesgo " + riskLevel + " — Sinapsistencia",
					MailTemplates.riskAlert(alert));
			log.info("[risk-alert] Alerta enviada (riskLevel={})", riskLevel);
		} catch (Exception ex) {
			log.error("[risk-alert] Error al enviar alerta: {}", ex.getMessage());
		}
	}
}
