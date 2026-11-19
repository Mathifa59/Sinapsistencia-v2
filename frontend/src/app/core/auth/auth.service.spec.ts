import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { AuthService, type AuthUser } from './auth.service';

const MEDICO: AuthUser = {
  id: 'u1',
  email: 'medico@ejemplo.pe',
  name: 'Dra. Prueba',
  role: 'doctor',
  isActive: true,
  createdAt: '2026-01-01T00:00:00Z',
  avatar: null,
};

describe('AuthService', () => {
  let auth: AuthService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    auth = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('arranca sin sesión', () => {
    expect(auth.user()).toBeNull();
    expect(auth.isAuthenticated()).toBe(false);
    expect(auth.role()).toBeNull();
  });

  it('login correcto guarda el usuario y expone el rol', async () => {
    const p = auth.login('medico@ejemplo.pe', 'secreta');
    const req = http.expectOne('/api/auth/login');
    expect(req.request.body).toEqual({ email: 'medico@ejemplo.pe', password: 'secreta' });
    expect(auth.isLoading()).toBe(true);
    req.flush({ success: true, data: { user: MEDICO, token: 't' } });
    expect(await p).toEqual(MEDICO);
    expect(auth.isAuthenticated()).toBe(true);
    expect(auth.role()).toBe('doctor');
    expect(auth.isLoading()).toBe(false);
  });

  it('login fallido no deja sesión y apaga el indicador de carga', async () => {
    const p = auth.login('medico@ejemplo.pe', 'mala');
    http
      .expectOne('/api/auth/login')
      .flush({ success: false, error: 'Credenciales inválidas' }, { status: 401, statusText: 'Unauthorized' });
    await expect(p).rejects.toThrow('Credenciales inválidas');
    expect(auth.isAuthenticated()).toBe(false);
    expect(auth.isLoading()).toBe(false);
  });

  it('hydrate recupera la sesión desde /api/auth/me y no vuelve a preguntar', async () => {
    const p = auth.hydrate();
    http.expectOne('/api/auth/me').flush({ success: true, data: MEDICO });
    expect(await p).toEqual(MEDICO);
    // segunda llamada: usa lo ya cargado, sin otra petición (http.verify lo comprueba)
    expect(await auth.hydrate()).toEqual(MEDICO);
  });

  it('hydrate sin cookie válida deja al usuario sin sesión', async () => {
    const p = auth.hydrate();
    http
      .expectOne('/api/auth/me')
      .flush({ success: false, error: 'No autenticado' }, { status: 401, statusText: 'Unauthorized' });
    expect(await p).toBeNull();
    expect(auth.isAuthenticated()).toBe(false);
  });

  it('logout limpia la sesión aunque el servidor falle', async () => {
    const l = auth.login('medico@ejemplo.pe', 'secreta');
    http.expectOne('/api/auth/login').flush({ success: true, data: { user: MEDICO, token: 't' } });
    await l;
    const p = auth.logout();
    http.expectOne('/api/auth/logout').error(new ProgressEvent('error'));
    await p;
    expect(auth.user()).toBeNull();
  });

  it('register envía el consentimiento de datos (Ley 29733) y apaga la carga', async () => {
    const p = auth.register({
      name: 'Dr. Nuevo',
      email: 'nuevo@ejemplo.pe',
      password: 'Clave-larga-1',
      role: 'doctor',
      cmp: '12345',
      specialty: 'Cardiología',
      acceptPrivacyPolicy: true,
    });
    const req = http.expectOne('/api/auth/register');
    expect(req.request.body.acceptPrivacyPolicy).toBe(true);
    req.flush({ success: true, data: { message: 'ok' } });
    await p;
    expect(auth.isLoading()).toBe(false);
  });

  it.each([
    ['forgotPassword', (a: AuthService) => a.forgotPassword('a@b.pe'), '/api/auth/forgot-password', { email: 'a@b.pe' }],
    [
      'resetPassword',
      (a: AuthService) => a.resetPassword('a@b.pe', 'tok', 'Nueva-1234'),
      '/api/auth/reset-password',
      { email: 'a@b.pe', token: 'tok', newPassword: 'Nueva-1234' },
    ],
    [
      'changePassword',
      (a: AuthService) => a.changePassword('vieja', 'Nueva-1234'),
      '/api/auth/change-password',
      { currentPassword: 'vieja', newPassword: 'Nueva-1234' },
    ],
  ])('%s llama al endpoint con el cuerpo esperado', async (_n, call, url, body) => {
    const p = call(auth);
    const req = http.expectOne(url);
    expect(req.request.body).toEqual(body);
    req.flush({ success: true, data: { message: 'ok' } });
    await p;
  });
});
