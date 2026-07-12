package pe.sinapsistencia.auth.application;

import java.time.Duration;
import java.time.Instant;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

import org.springframework.stereotype.Service;

import pe.sinapsistencia.shared.exception.TooManyRequestsException;

/**
 * Protección contra fuerza bruta en el login (ventana deslizante en memoria).
 *
 * Regla: máximo {@value #MAX_FAILURES} intentos fallidos por cuenta en
 * {@value #WINDOW_MINUTES} minutos; al superarlos, el login de esa cuenta
 * responde 429 hasta que la ventana se libere. Un login exitoso resetea el
 * contador. La clave es el email normalizado (el mecanismo protege la cuenta;
 * la capa de red/proxy puede añadir límites por IP).
 *
 * En memoria a propósito: una instancia de backend (Railway) no necesita
 * estado compartido; con múltiples réplicas se movería a Redis.
 */
@Service
public class LoginAttemptService {

	static final int MAX_FAILURES = 5;
	static final int WINDOW_MINUTES = 15;

	private static final Duration WINDOW = Duration.ofMinutes(WINDOW_MINUTES);

	private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();

	/** Lanza 429 si la cuenta superó el límite de intentos fallidos en la ventana. */
	public void assertNotBlocked(String email) {
		Deque<Instant> attempts = failures.get(key(email));
		if (attempts == null) {
			return;
		}
		prune(attempts);
		if (attempts.size() >= MAX_FAILURES) {
			Instant oldest = attempts.peekFirst();
			long minutesLeft = oldest == null ? WINDOW_MINUTES
					: Math.max(1, Duration.between(Instant.now(), oldest.plus(WINDOW)).toMinutes() + 1);
			throw new TooManyRequestsException(
					"Demasiados intentos fallidos. Intenta de nuevo en " + minutesLeft + " minuto(s).");
		}
	}

	/** Registra un intento fallido para la cuenta. */
	public void recordFailure(String email) {
		Deque<Instant> attempts = failures.computeIfAbsent(key(email), k -> new ConcurrentLinkedDeque<>());
		prune(attempts);
		attempts.addLast(Instant.now());
	}

	/** Un login exitoso limpia el historial de la cuenta. */
	public void reset(String email) {
		failures.remove(key(email));
	}

	private static void prune(Deque<Instant> attempts) {
		Instant cutoff = Instant.now().minus(WINDOW);
		while (!attempts.isEmpty() && attempts.peekFirst().isBefore(cutoff)) {
			attempts.pollFirst();
		}
	}

	private static String key(String email) {
		return email == null ? "" : email.strip().toLowerCase();
	}
}
