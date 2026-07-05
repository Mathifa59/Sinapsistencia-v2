package pe.sinapsistencia.config;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import pe.sinapsistencia.auth.infrastructure.ProfileRepository;

/**
 * Ajusta el correo de las cuentas demo (Dr. Carlos / Dra. Lucía) al arrancar,
 * tomando el valor de configuración. Por defecto usa el correo original del
 * seed, por lo que en tests y local NO cambia nada (los tests de integración
 * siguen autenticándose con {@code doctor.demo@sinapsistencia.pe}).
 *
 * <p>En producción se definen las variables {@code DEMO_DOCTOR_EMAIL} y
 * {@code DEMO_LAWYER_EMAIL} con correos reales (p. ej. alias {@code +} de Gmail)
 * para poder demostrar el envío de correos transaccionales a una bandeja real.
 * Es idempotente: si el correo ya coincide, no hace nada.
 */
@Component
public class DemoAccountEmailConfigurer implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(DemoAccountEmailConfigurer.class);

	/** IDs estables de las cuentas demo sembradas en V3__seed_demo.sql. */
	private static final UUID DOCTOR_ID = UUID.fromString("d0000000-0000-0000-0000-000000000001");
	private static final UUID LAWYER_ID = UUID.fromString("d0000000-0000-0000-0000-000000000002");

	private final ProfileRepository profileRepository;
	private final String doctorEmail;
	private final String lawyerEmail;

	public DemoAccountEmailConfigurer(ProfileRepository profileRepository,
			@Value("${app.demo.doctor-email:doctor.demo@sinapsistencia.pe}") String doctorEmail,
			@Value("${app.demo.lawyer-email:lawyer.demo@sinapsistencia.pe}") String lawyerEmail) {
		this.profileRepository = profileRepository;
		this.doctorEmail = doctorEmail;
		this.lawyerEmail = lawyerEmail;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		syncEmail(DOCTOR_ID, doctorEmail);
		syncEmail(LAWYER_ID, lawyerEmail);
	}

	private void syncEmail(UUID profileId, String targetEmail) {
		profileRepository.findById(profileId).ifPresent(profile -> {
			if (!profile.getEmail().equalsIgnoreCase(targetEmail)) {
				String previous = profile.getEmail();
				profile.setEmail(targetEmail);
				profileRepository.save(profile);
				log.info("[demo] Correo de cuenta demo actualizado: {} -> {}", previous, targetEmail);
			}
		});
	}
}
