import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { LucideAngularModule } from 'lucide-angular';
import { AuthService } from '../../../core/auth/auth.service';
import { BtnDirective } from '../../../shared/ui/button.directive';
import { InputDirective, LabelDirective } from '../../../shared/ui/field.directives';

/** HU-04: solicitud de restablecimiento de contraseña. */
@Component({
  selector: 'app-forgot-password',
  imports: [ReactiveFormsModule, RouterLink, LucideAngularModule, BtnDirective, InputDirective, LabelDirective],
  template: `
    <div class="min-h-screen bg-slate-50 flex items-center justify-center p-4">
      <div class="w-full max-w-sm">
        <div class="text-center mb-8">
          <div class="inline-flex items-center justify-center h-12 w-12 rounded-xl bg-slate-900 mb-4">
            <lucide-icon name="key" class="h-6 w-6 text-white" />
          </div>
          <h1 class="text-2xl font-bold text-slate-900">Recuperar contraseña</h1>
          <p class="text-sm text-slate-500 mt-1">Te enviaremos instrucciones para restablecer tu acceso</p>
        </div>

        <div class="bg-white rounded-xl border border-slate-200 shadow-sm p-6">
          @if (sent()) {
            <div class="text-center space-y-3">
              <lucide-icon name="mail-check" class="h-10 w-10 text-emerald-500 mx-auto" />
              <p class="text-sm text-slate-700">{{ successMessage() }}</p>
              @if (resetToken()) {
                <p class="text-xs text-slate-500 bg-slate-50 border border-slate-100 rounded-md px-3 py-2">
                  Token de demo: <code class="font-mono text-slate-800">{{ resetToken() }}</code>
                </p>
              }
              <a routerLink="/reset-password" [queryParams]="{ email: form.controls.email.value }" class="inline-block text-blue-600 text-sm hover:underline font-medium">
                Ir a restablecer contraseña
              </a>
            </div>
          } @else {
            <form [formGroup]="form" (ngSubmit)="onSubmit()" class="space-y-4">
              <div class="space-y-1.5">
                <label appLabel for="email">Correo electrónico</label>
                <input appInput id="email" type="email" placeholder="correo@ejemplo.pe" formControlName="email" />
                @if (form.controls.email.invalid && form.controls.email.touched) {
                  <p class="text-xs text-red-600">Ingresa un correo válido</p>
                }
              </div>

              @if (error()) {
                <p class="text-xs text-red-600 bg-red-50 border border-red-100 rounded-md px-3 py-2">{{ error() }}</p>
              }

              <button appBtn type="submit" variant="primary" class="w-full" [disabled]="isLoading()">
                {{ isLoading() ? 'Enviando...' : 'Enviar instrucciones' }}
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
export class ForgotPasswordComponent {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);

  protected readonly isLoading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly sent = signal(false);
  protected readonly successMessage = signal('');
  protected readonly resetToken = signal<string | null>(null);

  protected readonly form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
  });

  protected async onSubmit(): Promise<void> {
    this.error.set(null);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.isLoading.set(true);
    try {
      const result = await this.auth.forgotPassword(this.form.controls.email.value);
      this.successMessage.set(result.message ?? 'Revisa tu correo para continuar.');
      this.resetToken.set(result.resetToken ?? null);
      this.sent.set(true);
    } catch {
      this.error.set('No se pudo procesar la solicitud. Verifica el correo e intenta de nuevo.');
    } finally {
      this.isLoading.set(false);
    }
  }
}
