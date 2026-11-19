import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, CanActivateFn, Router, RouterStateSnapshot, UrlTree, provideRouter } from '@angular/router';
import { authGuard, guestGuard, roleGuard } from './auth.guard';
import { AuthService, type AuthUser } from './auth.service';
import type { UserRole } from '../../shared/constants';

function usuario(role: UserRole): AuthUser {
  return { id: 'u', email: 'a@b.pe', name: 'N', role, isActive: true, createdAt: '2026-01-01', avatar: null };
}

async function correr(guard: CanActivateFn, user: AuthUser | null, url = '/doctor/cases') {
  TestBed.configureTestingModule({
    providers: [provideRouter([]), { provide: AuthService, useValue: { hydrate: async () => user } }],
  });
  const resultado = await TestBed.runInInjectionContext(() =>
    guard({} as ActivatedRouteSnapshot, { url } as RouterStateSnapshot),
  );
  const router = TestBed.inject(Router);
  return resultado instanceof UrlTree ? router.serializeUrl(resultado) : resultado;
}

describe('authGuard', () => {
  it('sin sesión manda a /login y recuerda la ruta pedida', async () => {
    expect(await correr(authGuard, null, '/doctor/cases')).toBe('/login?redirect=%2Fdoctor%2Fcases');
  });

  it('con sesión deja pasar', async () => {
    expect(await correr(authGuard, usuario('doctor'))).toBe(true);
  });
});

describe('roleGuard', () => {
  it('sin sesión manda a /login con el redirect', async () => {
    expect(await correr(roleGuard('admin'), null, '/admin/users')).toBe('/login?redirect=%2Fadmin%2Fusers');
  });

  it('el rol esperado pasa', async () => {
    expect(await correr(roleGuard('lawyer'), usuario('lawyer'))).toBe(true);
  });

  it.each([
    ['doctor', 'admin'],
    ['doctor', 'lawyer'],
    ['lawyer', 'doctor'],
    ['lawyer', 'admin'],
    ['admin', 'doctor'],
    ['admin', 'lawyer'],
  ] as [UserRole, UserRole][])('un %s no entra a la zona de %s: va a acceso denegado', async (actual, esperado) => {
    expect(await correr(roleGuard(esperado), usuario(actual))).toBe('/acceso-denegado');
  });
});

describe('guestGuard', () => {
  it('sin sesión deja ver el login', async () => {
    expect(await correr(guestGuard, null, '/login')).toBe(true);
  });

  it.each([
    ['doctor', '/doctor/dashboard'],
    ['lawyer', '/lawyer/dashboard'],
    ['admin', '/admin/dashboard'],
  ] as [UserRole, string][])('con sesión de %s redirige a su panel', async (rol, destino) => {
    expect(await correr(guestGuard, usuario(rol), '/login')).toBe(destino);
  });
});
