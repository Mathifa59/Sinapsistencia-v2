package pe.sinapsistencia.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/** Habilita @Async (lo usan MailNotifier y RiskAlertNotifier para el envío fire-and-forget). */
@Configuration
@EnableAsync
public class AsyncConfig {
}
