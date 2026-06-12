package pe.sinapsistencia.audit.infrastructure;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marca un método de controlador para que {@link AuditAspect} registre una
 * entrada en audit_logs tras una respuesta exitosa (HU-10/42).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Auditable {

	/** Acción (login, logout, create, update, delete, sign, ...). */
	String action();

	/** Recurso afectado (auth, legal_case, document, user, contact_request, profile). */
	String resource();
}
