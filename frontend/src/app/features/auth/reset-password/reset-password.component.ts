import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { LucideAngularModule } from 'lucide-angular';
import { AuthService } from '../../../core/auth/auth.service';
import { BtnDirective } from '../../../shared/ui/button.directive';
import { InputDirective, LabelDirective } from '../../../shared/ui/field.directives';

/** HU-04: restablecimiento de contraseña con token. */
@Component({
  selector: 'app-reset-password',
  imports: [ReactiveFormsModule, RouterLink, LucideAngularModule, BtnDirective, InputDirective, LabelDirective],
  template: `
    <div class="min-h-screen bg-slate-50 flex items-center justify-center p-4">
      <div class="w-full max-w-sm">
        <div class="text-center mb-8">
          <div class="inline-flex items-center justify-center h-12 w-12 rounded-xl bg-slate-900 mb-4">
            <lucide-icon name="lock-keyhole" class="h-6 w-6 text-white" />
          </div>
          <h1 class="text-2xl font-bold text-slate-900">Nueva contraseña</h1>
          <p class="text-sm text-slate-500 mt-1">Ingresa el token recibido y tu nueva contraseña</p>
        </div>

        <div class="bg-white rounded-xl border border-slate-200 shadow-sm p-6">
          @if (done()) {
            <div class="text-center space-y-3">
              <lucide-icon name="check-circle-2" class="h-10 w-10 text-emerald-500 mx-auto" />
              <p class="text-sm text-slate-700">Contraseña restablecida correctamente.</p>
              <a routerLink="/login" class="inline-block text-blue-600 text-sm hover:underline font-medium">Iniciar sesión</a>
            </div>
          } @else {
            <form [formGroup]="form" (ngSubmit)="onSubmit()" class="space-y-4">
              <div class="space-y-1.5">
                <label appLabel for="email">Correo electrónico</label>
                <input appInput id="email" type="email" formControlName="email" />
              </div>

              <div class="space-y-1.5">
                <label appLabel for="token">Token de restablecimiento</label>
                <input appInput id="token" formControlName="token" placeholder="Pega el token recibido" />
                @if (form.controls.token.invalid && form.controls.token.touched) {
                  <p class="text-xs text-red-600">El token es requerido</p>
                }
              </div>

              <div class="space-y-1.5">
                <label appLabel for="new-password">Nueva contraseña</label>
                <input appInput id="new-password" type="password" formControlName="newPassword" />
                @if (form.controls.newPassword.invalid && form.controls.newPassword.touched) {
                  <p class="text-xs text-red-600">Mínimo 8 caracteres</p>
                }
              </div>

              <div class="space-y-1.5">
                <label appLabel for="confirm-password">Confirmar contraseña</label>
                <input appInput id="confirm-password" type="password" formControlName="confirmPassword" />
                @if (form.hasError('mismatch') && form.controls.confirmPassword.touched) {
                  <p class="text-xs text-red-600">Las contraseñas no coinciden</p>
                }
              </div>

              @if (error()) {
                <p class="text-xs text-red-600 bg-red-50 border border-red-100 rounded-md px-3 py-2">{{ error() }}</p>
              }

              <button appBtn type="submit" variant="primary" class="w-full" [disabled]="isLoading()">
                {{ isLoading() ? 'Restableciendo...' : 'Restablecer contraseña' }}
              </button>
            </form>
          }
        </div>

        <p class="text-center text-sm text-slate-500 mt-5">
          <a routerLink="/login" class="text-blue-600 hover:underline font-medium">Volver al inicio de sesión</a>
        </p>
      </div>
    </div>
  `,
})
export class ResetPasswordComponent {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);

  protected readonly isLoading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly done = signal(false);

  protected readonly form = this.fb.nonNullable.group(
    {
      email: [this.route.snapshot.queryParamMap.get('email') ?? '', [Validators.required, Validators.email]],
      token: [this.route.snapshot.queryParamMap.get('token') ?? '', Validators.required],
      newPassword: ['', [Validators.required, Validators.minLength(8)]],
      confirmPassword: ['', Validators.required],
    },
    {
      validators: (group) =>
        group.get('newPassword')?.value === group.get('confirmPassword')?.value ? null : { mismatch: true },
    },
  );

  protected async onSubmit(): Promise<void> {
    this.error.set(null);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const { email, token, newPassword } = this.form.getRawValue();
    this.isLoading.set(true);
    try {
      await this.auth.resetPassword(email, token, newPassword);
      this.done.set(true);
    } catch {
      this.error.set('No se pudo restablecer la contraseña. Verifica el token e intenta de nuevo.');
    } finally {
      this.isLoading.set(false);
    }
  }
}
