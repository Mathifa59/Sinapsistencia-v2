import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { LucideAngularModule } from 'lucide-angular';
import { AuthService } from '../../../core/auth/auth.service';
import { BtnDirective } from '../../../shared/ui/button.directive';
import { InputDirective, LabelDirective } from '../../../shared/ui/field.directives';
import { ROLE_DASHBOARD, type UserRole } from '../../../shared/constants';

/** Réplica de app/login/page.tsx (Reactive Forms en lugar de react-hook-form+zod). */
@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule, RouterLink, LucideAngularModule, BtnDirective, InputDirective, LabelDirective],
  template: `
    <div class="min-h-screen bg-slate-50 flex items-center justify-center p-4">
      <div class="w-full max-w-sm">
        <div class="text-center mb-8">
          <img
            src="logo/sinapsistencia-color-horizontal-transparent.svg"
            alt="Sinapsistencia"
            class="h-11 mx-auto mb-4"
          />
          <p class="text-sm text-slate-500">Ingresa a tu cuenta</p>
        </div>

        <div class="bg-white rounded-xl border border-slate-200 shadow-sm p-6">
          <form [formGroup]="form" (ngSubmit)="onSubmit()" class="space-y-4">
            <div class="space-y-1.5">
              <label appLabel for="email">Correo electrónico</label>
              <input appInput id="email" type="email" placeholder="correo@ejemplo.pe" formControlName="email" />
              @if (form.controls.email.invalid && form.controls.email.touched) {
                <p class="text-xs text-red-600">Ingresa un correo válido</p>
              }
            </div>

            <div class="space-y-1.5">
              <label appLabel for="password">Contraseña</label>
              <div class="relative">
                <input
                  appInput
                  id="password"
                  [type]="showPassword() ? 'text' : 'password'"
                  placeholder="••••••••"
                  formControlName="password"
                />
                <button
                  type="button"
                  (click)="showPassword.set(!showPassword())"
                  class="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600"
                >
                  <lucide-icon [name]="showPassword() ? 'eye-off' : 'eye'" class="h-4 w-4" />
                </button>
              </div>
              @if (form.controls.password.invalid && form.controls.password.touched) {
                <p class="text-xs text-red-600">La contraseña es requerida</p>
              }
              <div class="text-right">
                <a routerLink="/forgot-password" class="text-xs text-blue-600 hover:underline">¿Olvidaste tu contraseña?</a>
              </div>
            </div>

            @if (error()) {
              <p class="text-xs text-red-600 bg-red-50 border border-red-100 rounded-md px-3 py-2">
                {{ error() }}
              </p>
            }

            <button appBtn type="submit" variant="primary" class="w-full" [disabled]="auth.isLoading()">
              {{ auth.isLoading() ? 'Ingresando...' : 'Ingresar' }}
            </button>
          </form>
        </div>

        <p class="text-center text-sm text-slate-500 mt-5">
          ¿No tienes cuenta?
          <a routerLink="/register" class="text-blue-600 hover:underline font-medium">Crear cuenta</a>
        </p>
      </div>
    </div>
  `,
})
export class LoginComponent {
  protected readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly fb = inject(FormBuilder);

  protected readonly showPassword = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', Validators.required],
  });

  protected async onSubmit(): Promise<void> {
    this.error.set(null);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const { email, password } = this.form.getRawValue();
    try {
      const user = await this.auth.login(email, password);
      this.redirectAfterLogin(user.role);
    } catch {
      this.error.set('Credenciales incorrectas. Verifica tu correo y contraseña.');
    }
  }

  private redirectAfterLogin(role: UserRole): void {
    const redirectTo = this.route.snapshot.queryParamMap.get('redirect');
    if (redirectTo && redirectTo.startsWith(`/${role}`)) {
      this.router.navigateByUrl(redirectTo);
    } else {
      this.router.navigate([ROLE_DASHBOARD[role]]);
    }
  }
}
