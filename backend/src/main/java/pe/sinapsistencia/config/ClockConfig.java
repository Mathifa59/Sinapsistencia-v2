package pe.sinapsistencia.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * H-05: reloj inyectable con la zona de negocio (Lima), en vez de que cada
 * servicio use el reloj/zona por defecto del sistema -- así "días desde el
 * evento" es reproducible y testeable sin depender del TZ del servidor.
 */
@Configuration
public class ClockConfig {

	public static final ZoneId BUSINESS_ZONE = ZoneId.of("America/Lima");

	@Bean
	public Clock businessClock() {
		return Clock.system(BUSINESS_ZONE);
	}
}
