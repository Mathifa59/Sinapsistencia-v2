import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { LoginComponent } from './login.component';
import { AuthService } from '../../../core/auth/auth.service';
import type { UserRole } from '../../../shared/constants';
import { provideTestIcons } from '../../../testing/test-icons';

function montar(login: (e: string, p: string) => Promise<unknown>, redirect: string | null = null) {
  TestBed.configureTestingModule({
    providers: [
      provideRouter([]),
      provideTestIcons(),
      { provide: AuthService, useValue: { login, isLoading: () => false } },
      { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: convertToParamMap(redirect ? { redirect } : {}) } } },
    ],
  });
  const router = TestBed.inject(Router);
  const navigateByUrl = vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
  const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);
  const fixture = TestBed.createComponent(LoginComponent);
  fixture.detectChanges();
  const el = fixture.nativeElement as HTMLElement;
  const escribir = (id: string, valor: string) => {
    const input = el.querySelector<HTMLInputElement>('#' + id)!;
    input.value = valor;
    input.dispatchEvent(new Event('input'));
  };
  const enviar = async () => {
    el.querySelector('form')!.dispatchEvent(new Event('submit'));
    await fixture.whenStable();
    fixture.detectChanges();
  };
  return { fixture, el, escribir, enviar, navigate, navigateByUrl };
}

describe('LoginComponent', () => {
  it('con el formulario vacío no llama al servidor y muestra los avisos', async () => {
    const login = vi.fn();
    const { el, enviar } = montar(login);
    await enviar();
    expect(login).not.toHaveBeenCalled();
    expect(el.textContent).toContain('Ingresa un correo válido');
    expect(el.textContent).toContain('La contraseña es requerida');
  });

  it('un correo mal escrito no se envía', async () => {
    const login = vi.fn();
    const { escribir, enviar } = montar(login);
    escribir('email', 'esto-no-es-un-correo');
    escribir('password', 'secreta');
    await enviar();
    expect(login).not.toHaveBeenCalled();
  });

  it('credenciales incorrectas muestran el mensaje y no navegan', async () => {
    const login = vi.fn().mockRejectedValue(new Error('401'));
    const { el, escribir, enviar, navigate, navigateByUrl } = montar(login);
    escribir('email', 'medico@ejemplo.pe');
    escribir('password', 'mala');
    await enviar();
    expect(login).toHaveBeenCalledWith('medico@ejemplo.pe', 'mala');
    expect(el.textContent).toContain('Credenciales incorrectas');
    expect(navigate).not.toHaveBeenCalled();
    expect(navigateByUrl).not.toHaveBeenCalled();
  });

  it.each([
    ['doctor', '/doctor/dashboard'],
    ['lawyer', '/lawyer/dashboard'],
    ['admin', '/admin/dashboard'],
  ] as [UserRole, string][])('un %s entra a su panel', async (rol, destino) => {
    const login = vi.fn().mockResolvedValue({ role: rol });
    const { escribir, enviar, navigate } = montar(login);
    escribir('email', 'a@b.pe');
    escribir('password', 'secreta');
    await enviar();
    expect(navigate).toHaveBeenCalledWith([destino]);
  });

  it('respeta la ruta pedida si pertenece a su propio rol', async () => {
    const login = vi.fn().mockResolvedValue({ role: 'doctor' });
    const { escribir, enviar, navigateByUrl } = montar(login, '/doctor/cases');
    escribir('email', 'a@b.pe');
    escribir('password', 'secreta');
    await enviar();
    expect(navigateByUrl).toHaveBeenCalledWith('/doctor/cases');
  });

  it('ignora una ruta pedida de otro rol y va al panel propio', async () => {
    const login = vi.fn().mockResolvedValue({ role: 'doctor' });
    const { escribir, enviar, navigate, navigateByUrl } = montar(login, '/admin/users');
    escribir('email', 'a@b.pe');
    escribir('password', 'secreta');
    await enviar();
    expect(navigateByUrl).not.toHaveBeenCalled();
    expect(navigate).toHaveBeenCalledWith(['/doctor/dashboard']);
  });

  it('no sigue una redirección a otro sitio', async () => {
    const login = vi.fn().mockResolvedValue({ role: 'doctor' });
    const { escribir, enviar, navigate, navigateByUrl } = montar(login, 'https://sitio-malicioso.example');
    escribir('email', 'a@b.pe');
    escribir('password', 'secreta');
    await enviar();
    expect(navigateByUrl).not.toHaveBeenCalled();
    expect(navigate).toHaveBeenCalledWith(['/doctor/dashboard']);
  });

  it('el botón del ojo alterna entre ocultar y mostrar la contraseña', () => {
    const { fixture, el } = montar(vi.fn());
    const campo = el.querySelector<HTMLInputElement>('#password')!;
    expect(campo.type).toBe('password');
    el.querySelector<HTMLButtonElement>('button[type="button"]')!.click();
    fixture.detectChanges();
    expect(campo.type).toBe('text');
    el.querySelector<HTMLButtonElement>('button[type="button"]')!.click();
    fixture.detectChanges();
    expect(campo.type).toBe('password');
  });
});
