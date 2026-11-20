import { Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { LucideAngularModule } from 'lucide-angular';
import { AuthService } from '../../../core/auth/auth.service';

/**
 * Destino del enlace del correo de confirmación: /verify-email?email=…&token=….
 * Confirma que el titular controla el buzón; recién entonces la cuenta puede iniciar
 * sesión y recibir el enlace de recuperación de contraseña.
 */
@Component({
  selector: 'app-verify-email',
  imports: [RouterLink, LucideAngularModule],
  template: `
    <div class="min-h-screen bg-slate-50 flex items-center justify-center p-4">
      <div class="w-full max-w-sm text-center">
        <img
          src="logo/sinapsistencia-color-horizontal-transparent.svg"
          alt="Sinapsistencia"
          class="h-11 mx-auto mb-6"
        />

        <div class="bg-white rounded-xl border border-slate-200 shadow-sm p-6 space-y-4">
          @switch (state()) {
            @case ('loading') {
              <lucide-icon name="loader-2" class="h-8 w-8 mx-auto animate-spin text-blue-600" />
              <p class="text-sm text-slate-600">Confirmando tu correo…</p>
            }
            @case ('ok') {
              <lucide-icon name="check-circle-2" class="h-10 w-10 mx-auto text-emerald-500" />
              <h1 class="text-lg font-semibold text-slate-900">Correo confirmado</h1>
              <p class="text-sm text-slate-600">Tu cuenta ya está activa. Con este correo también podrás recuperar tu contraseña.</p>
              <a routerLink="/login" class="inline-block w-full rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700">
                Iniciar sesión
              </a>
            }
            @case ('error') {
              <lucide-icon name="alert-triangle" class="h-10 w-10 mx-auto text-amber-500" />
              <h1 class="text-lg font-semibold text-slate-900">No se pudo confirmar</h1>
              <p class="text-sm text-slate-600">{{ message() }}</p>
              <p class="text-xs text-slate-500">
                Inicia sesión con tu correo y contraseña: te ofreceremos enviar un nuevo enlace.
              </p>
              <a routerLink="/login" class="inline-block w-full rounded-md border border-slate-200 px-4 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50">
                Ir a iniciar sesión
              </a>
            }
          }
        </div>
      </div>
    </div>
  `,
})
export class VerifyEmailComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly auth = inject(AuthService);

  protected readonly state = signal<'loading' | 'ok' | 'error'>('loading');
  protected readonly message = signal('');

  async ngOnInit(): Promise<void> {
    const email = this.route.snapshot.queryParamMap.get('email');
    const token = this.route.snapshot.queryParamMap.get('token');
    if (!email || !token) {
      this.fail('El enlace está incompleto.');
      return;
    }
    try {
      await this.auth.verifyEmail(email, token);
      this.state.set('ok');
    } catch (err) {
      this.fail(err instanceof Error ? err.message : 'El enlace es inválido o expiró.');
    }
  }

  private fail(message: string): void {
    this.message.set(message);
    this.state.set('error');
  }
}
