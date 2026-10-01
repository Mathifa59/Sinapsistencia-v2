package pe.sinapsistencia.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Habilita @Async (lo usa MailNotifier para bienvenida/recuperación de
 * contraseña, fire-and-forget) y @Scheduled (H-06: NotificationWorker despacha
 * el outbox de avisos en lotes periódicos).
 */
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {
}
