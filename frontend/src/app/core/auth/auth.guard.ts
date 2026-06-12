import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';
import { ROLE_DASHBOARD, type UserRole } from '../../shared/constants';

/**
 * Replican el ruteo del proxy.ts legacy:
 *  - sin sesión → /login?redirect=<url original>
 *  - prefijo de ruta ≠ rol → dashboard del rol propio
 */
export const authGuard: CanActivateFn = async (_route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);

  const user = await auth.hydrate();
  if (!user) {
    return router.createUrlTree(['/login'], { queryParams: { redirect: state.url } });
  }
  return true;
};

/** Guard de prefijo por rol: /doctor/** solo para doctor, etc. */
export function roleGuard(expectedRole: UserRole): CanActivateFn {
  return async (_route, state) => {
    const auth = inject(AuthService);
    const router = inject(Router);

    const user = await auth.hydrate();
    if (!user) {
      return router.createUrlTree(['/login'], { queryParams: { redirect: state.url } });
    }
    if (user.role !== expectedRole) {
      return router.createUrlTree([ROLE_DASHBOARD[user.role]]);
    }
    return true;
  };
}

/** En /login con sesión activa → dashboard del rol (igual que el proxy legacy). */
export const guestGuard: CanActivateFn = async () => {
  const auth = inject(AuthService);
  const router = inject(Router);

  const user = await auth.hydrate();
  if (user) {
    return router.createUrlTree([ROLE_DASHBOARD[user.role]]);
  }
  return true;
};
