import { routes } from './app.routes';

describe('rutas de la aplicación', () => {
  const por = (path: string) => routes.find((r) => r.path === path);

  it.each(['doctor', 'lawyer', 'admin'])('la zona /%s exige sesión y rol (dos guards)', (zona) => {
    const ruta = por(zona);
    expect(ruta).toBeDefined();
    expect(ruta!.canActivate?.length).toBe(2);
  });

  it.each(['login', 'register', 'forgot-password', 'reset-password'])('/%s solo es para quien no tiene sesión', (ruta) => {
    expect(por(ruta)?.canActivate?.length).toBe(1);
  });

  it('la raíz lleva al login', () => {
    expect(por('')?.redirectTo).toBe('login');
  });

  it('existe la pantalla de acceso denegado', () => {
    expect(por('acceso-denegado')).toBeDefined();
  });
});
