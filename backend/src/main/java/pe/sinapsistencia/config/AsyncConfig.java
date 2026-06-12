package pe.sinapsistencia.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/** Habilita @Async (lo usa N8nNotifier para el webhook fire-and-forget). */
@Configuration
@EnableAsync
public class AsyncConfig {
}
