import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { LucideAngularModule } from 'lucide-angular';
import { AuthService } from '../../../core/auth/auth.service';
import { BtnDirective } from '../../../shared/ui/button.directive';
import { InputDirective, LabelDirective } from '../../../shared/ui/field.directives';
import { ROLE_DASHBOARD, type UserRole } from '../../../shared/constants';

const DEMO_ROLES: { role: UserRole; label: string; color: string }[] = [
  { role: 'doctor', label: 'Médico', color: 'bg-blue-600 hover:bg-blue-700 text-white' },
  { role: 'lawyer', label: 'Abogado', color: 'bg-emerald-600 hover:bg-emerald-700 text-white' },
  { role: 'admin', label: 'Administrador', color: 'bg-slate-700 hover:bg-slate-800 text-white' },
];

/** Réplica de app/login/page.tsx (Reactive Forms en lugar de react-hook-form+zod). */
@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule, RouterLink, LucideAngularModule, BtnDirective, InputDirective, LabelDirective],
  template: `
    <div class="min-h-screen bg-slate-50 flex items-center justify-center p-4">
      <div class="w-full max-w-sm">
        <div class="text-center mb-8">
          <div class="inline-flex items-center justify-center h-12 w-12 rounded-xl bg-slate-900 mb-4">
            <lucide-icon name="shield" class="h-6 w-6 text-white" />
          </div>
          <h1 class="text-2xl font-bold text-slate-900">Sinapsistencia</h1>
          <p class="text-sm text-slate-500 mt-1">Ingresa a tu cuenta</p>
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

        <!-- Acceso rápido demo -->
        <div class="mt-6">
          <div class="flex items-center gap-2 mb-3">
            <div class="flex-1 h-px bg-slate-200"></div>
            <span class="flex items-center gap-1 text-xs text-slate-400 font-medium">
              <lucide-icon name="zap" class="h-3 w-3" />
              Acceso rápido (demo)
            </span>
            <div class="flex-1 h-px bg-slate-200"></div>
          </div>
          <div class="flex gap-2">
            @for (demo of demoRoles; track demo.role) {
              <button
                type="button"
                (click)="handleDemoLogin(demo.role)"
                [disabled]="auth.isLoading() || demoLoading() !== null"
                class="flex-1 py-2 px-2 rounded-lg text-xs font-semibold transition-colors disabled:opacity-50 disabled:cursor-not-allowed {{ demo.color }}"
              >
                {{ demoLoading() === demo.role ? '...' : demo.label }}
              </button>
            }
          </div>
        </div>
      </div>
    </div>
  `,
})
export class LoginComponent {
  protected readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly fb = inject(FormBuilder);

  protected readonly demoRoles = DEMO_ROLES;
  protected readonly showPassword = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly demoLoading = signal<UserRole | null>(null);

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

  protected async handleDemoLogin(role: UserRole): Promise<void> {
    this.error.set(null);
    this.demoLoading.set(role);
    try {
      const user = await this.auth.loginByRole(role);
      this.redirectAfterLogin(user.role);
    } catch {
      this.error.set(`No se pudo ingresar como ${role} demo. Verifica que la cuenta exista.`);
    } finally {
      this.demoLoading.set(null);
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
