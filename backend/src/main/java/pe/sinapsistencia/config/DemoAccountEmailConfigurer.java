package pe.sinapsistencia.config;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import pe.sinapsistencia.auth.domain.UserRole;
import pe.sinapsistencia.auth.infrastructure.ProfileRepository;

/**
 * Ajusta al arrancar los correos de las cuentas demo, tomando los valores de
 * configuración. Por defecto usa los correos originales del seed, por lo que en
 * tests y local NO cambia nada (los tests de integración siguen autenticándose
 * con {@code doctor.demo@sinapsistencia.pe}).
 *
 * <p>En producción:
 * <ul>
 *   <li>{@code DEMO_DOCTOR_EMAIL} / {@code DEMO_LAWYER_EMAIL}: correo real de las
 *       cuentas demo principales (Dr. Carlos / Dra. Lucía).</li>
 *   <li>{@code DEMO_MAIL_BASE} (opcional, p. ej. {@code micorreo@gmail.com}): si se
 *       define, TODOS los demás abogados demo ({@code *@sinapsistencia.pe}) pasan a
 *       un alias {@code base+usuario@dominio}, de modo que cualquier abogado que
 *       recomiende el matching recibe el correo en esa bandeja. La contraseña de
 *       todas las cuentas demo es {@code Demo123!}.</li>
 * </ul>
 * Es idempotente: si un correo ya coincide, no lo vuelve a tocar.
 */
@Component
public class DemoAccountEmailConfigurer implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(DemoAccountEmailConfigurer.class);

	/** IDs estables de las cuentas demo principales sembradas en V3__seed_demo.sql. */
	private static final UUID DOCTOR_ID = UUID.fromString("d0000000-0000-0000-0000-000000000001");
	private static final UUID LAWYER_ID = UUID.fromString("d0000000-0000-0000-0000-000000000002");

	private static final String SEED_DOMAIN = "@sinapsistencia.pe";

	private final ProfileRepository profileRepository;
	private final String doctorEmail;
	private final String lawyerEmail;
	private final String mailBase;

	public DemoAccountEmailConfigurer(ProfileRepository profileRepository,
			@Value("${app.demo.doctor-email:doctor.demo@sinapsistencia.pe}") String doctorEmail,
			@Value("${app.demo.lawyer-email:lawyer.demo@sinapsistencia.pe}") String lawyerEmail,
			@Value("${app.demo.mail-base:}") String mailBase) {
		this.profileRepository = profileRepository;
		this.doctorEmail = doctorEmail;
		this.lawyerEmail = lawyerEmail;
		this.mailBase = mailBase == null ? "" : mailBase.strip();
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		syncEmail(DOCTOR_ID, doctorEmail);
		syncEmail(LAWYER_ID, lawyerEmail);
		aliasRemainingDemoLawyers();
	}

	/** Fija el correo de una cuenta demo concreta (por su id estable). */
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

	/**
	 * Si hay un correo base configurado, reescribe los correos ficticios del resto
	 * de abogados demo a alias {@code base+usuario@dominio} (todos llegan a la misma
	 * bandeja). Sin base configurada no hace nada (tests/local).
	 */
	private void aliasRemainingDemoLawyers() {
		if (mailBase.isBlank()) {
			return;
		}
		int at = mailBase.indexOf('@');
		if (at < 1 || at == mailBase.length() - 1) {
			log.warn("[demo] DEMO_MAIL_BASE inválido ('{}'); se omite el aliasing de abogados", mailBase);
			return;
		}
		String baseLocal = mailBase.substring(0, at);
		String baseDomain = mailBase.substring(at + 1);

		int updated = 0;
		for (var profile : profileRepository.findAll()) {
			if (profile.getRole() != UserRole.LAWYER
					|| profile.getId().equals(DOCTOR_ID) || profile.getId().equals(LAWYER_ID)) {
				continue;
			}
			String current = profile.getEmail();
			if (current == null || !current.toLowerCase().endsWith(SEED_DOMAIN)) {
				continue; // ya tiene alias real o un correo propio
			}
			String userPart = current.substring(0, current.indexOf('@'));
			String alias = baseLocal + "+" + userPart + "@" + baseDomain;
			profile.setEmail(alias);
			profileRepository.save(profile);
			updated++;
		}
		if (updated > 0) {
			log.info("[demo] {} abogado(s) demo apuntados a alias de {}", updated, mailBase);
		}
	}
}
