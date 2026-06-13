import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { LucideAngularModule } from 'lucide-angular';
import { AuthService } from '../../../core/auth/auth.service';
import { BtnDirective } from '../../../shared/ui/button.directive';
import { InputDirective, LabelDirective, SelectDirective } from '../../../shared/ui/field.directives';
import { cn } from '../../../shared/utils/cn';
import { LEGAL_SPECIALTIES, MEDICAL_SPECIALTIES } from '../../../shared/constants';

type RegisterRole = 'doctor' | 'lawyer';

/** Página de registro público (médicos y abogados) — POST /api/auth/register. */
@Component({
  selector: 'app-register',
  imports: [ReactiveFormsModule, RouterLink, LucideAngularModule, BtnDirective, InputDirective, LabelDirective, SelectDirective],
  template: `
    <div class="min-h-screen bg-slate-50 flex items-center justify-center p-4">
      <div class="w-full max-w-lg">
        <div class="text-center mb-8">
          <div class="inline-flex items-center justify-center h-12 w-12 rounded-xl bg-slate-900 mb-4">
            <lucide-icon name="shield" class="h-6 w-6 text-white" />
          </div>
          <h1 class="text-2xl font-bold text-slate-900">Crear cuenta</h1>
          <p class="text-sm text-slate-500 mt-1">Regístrate como médico o abogado</p>
        </div>

        <div class="bg-white rounded-xl border border-slate-200 shadow-sm p-6">
          <form [formGroup]="form" (ngSubmit)="onSubmit()" class="space-y-4">
            <div class="space-y-1.5">
              <label appLabel>Soy...</label>
              <div class="flex gap-2">
                <button
                  type="button"
                  [class]="cn('flex-1 py-2 px-3 rounded-lg text-sm font-medium border transition-all flex items-center justify-center gap-2', form.controls.role.value === 'doctor' ? 'bg-blue-50 border-blue-300 text-blue-700 shadow-sm' : 'bg-white border-slate-200 text-slate-600 hover:border-slate-300 hover:bg-slate-50')"
                  (click)="setRole('doctor')"
                >
                  <lucide-icon name="stethoscope" class="h-4 w-4" />Médico
                </button>
                <button
                  type="button"
                  [class]="cn('flex-1 py-2 px-3 rounded-lg text-sm font-medium border transition-all flex items-center justify-center gap-2', form.controls.role.value === 'lawyer' ? 'bg-emerald-50 border-emerald-300 text-emerald-700 shadow-sm' : 'bg-white border-slate-200 text-slate-600 hover:border-slate-300 hover:bg-slate-50')"
                  (click)="setRole('lawyer')"
                >
                  <lucide-icon name="scale" class="h-4 w-4" />Abogado
                </button>
              </div>
            </div>

            <div class="space-y-1.5">
              <label appLabel for="name">Nombre completo</label>
              <input appInput id="name" type="text" placeholder="Nombre y apellidos" formControlName="name" />
              @if (form.controls.name.invalid && form.controls.name.touched) {
                <p class="text-xs text-red-600">El nombre es requerido</p>
              }
            </div>

            <div class="space-y-1.5">
              <label appLabel for="email">Correo electrónico</label>
              <input appInput id="email" type="email" placeholder="correo@ejemplo.pe" formControlName="email" />
              @if (form.controls.email.invalid && form.controls.email.touched) {
                <p class="text-xs text-red-600">Ingresa un correo válido</p>
              }
            </div>

            <div class="grid grid-cols-2 gap-3">
              <div class="space-y-1.5">
                <label appLabel for="password">Contraseña</label>
                <input appInput id="password" type="password" placeholder="••••••••" formControlName="password" />
                @if (form.controls.password.invalid && form.controls.password.touched) {
                  <p class="text-xs text-red-600">Mínimo 8 caracteres</p>
                }
              </div>
              <div class="space-y-1.5">
                <label appLabel for="confirmPassword">Confirmar contraseña</label>
                <input appInput id="confirmPassword" type="password" placeholder="••••••••" formControlName="confirmPassword" />
                @if (form.controls.confirmPassword.touched && form.hasError('passwordMismatch')) {
                  <p class="text-xs text-red-600">Las contraseñas no coinciden</p>
                }
              </div>
            </div>

            @if (form.controls.role.value === 'doctor') {
              <div class="grid grid-cols-2 gap-3">
                <div class="space-y-1.5">
                  <label appLabel for="specialty">Especialidad médica</label>
                  <select appSelect id="specialty" formControlName="specialty">
                    <option value="">Selecciona una especialidad</option>
                    @for (s of medicalSpecialties; track s) {
                      <option [value]="s">{{ s }}</option>
                    }
                  </select>
                  @if (form.controls.specialty.invalid && form.controls.specialty.touched) {
                    <p class="text-xs text-red-600">La especialidad es requerida</p>
                  }
                </div>
                <div class="space-y-1.5">
                  <label appLabel for="cmp">N° CMP (opcional)</label>
                  <input appInput id="cmp" type="text" placeholder="12345" formControlName="cmp" />
                </div>
              </div>
              <div class="space-y-1.5">
                <label appLabel for="hospital">Hospital / clínica (opcional)</label>
                <input appInput id="hospital" type="text" placeholder="Hospital Nacional..." formControlName="hospital" />
              </div>
            }

            @if (form.controls.role.value === 'lawyer') {
              <div class="space-y-1.5">
                <label appLabel for="cab">N° CAB (opcional)</label>
                <input appInput id="cab" type="text" placeholder="12345" formControlName="cab" />
              </div>

              <div class="space-y-1.5">
                <label appLabel>Especialidades legales</label>
                <p class="text-xs text-slate-400">Selecciona al menos una</p>
                <div class="flex flex-wrap gap-2">
                  @for (s of legalSpecialties; track s) {
                    <button
                      type="button"
                      [class]="cn('px-3 py-1.5 rounded-lg text-xs font-medium border transition-all', selectedLegalSpecialties().includes(s) ? 'bg-blue-50 border-blue-300 text-blue-700 shadow-sm' : 'bg-white border-slate-200 text-slate-600 hover:border-slate-300 hover:bg-slate-50')"
                      (click)="toggleLegalSpecialty(s)"
                    >
                      {{ s }}
                    </button>
                  }
                </div>
                @if (touchedLawyerLists() && selectedLegalSpecialties().length === 0) {
                  <p class="text-xs text-red-600">Selecciona al menos una especialidad legal</p>
                }
              </div>

              <div class="space-y-1.5">
                <label appLabel>Áreas médicas de interés</label>
                <p class="text-xs text-slate-400">Selecciona al menos una</p>
                <div class="flex flex-wrap gap-2">
                  @for (s of medicalSpecialties; track s) {
                    <button
                      type="button"
                      [class]="cn('px-3 py-1.5 rounded-lg text-xs font-medium border transition-all', selectedMedicalAreas().includes(s) ? 'bg-emerald-50 border-emerald-300 text-emerald-700 shadow-sm' : 'bg-white border-slate-200 text-slate-600 hover:border-slate-300 hover:bg-slate-50')"
                      (click)="toggleMedicalArea(s)"
                    >
                      {{ s }}
                    </button>
                  }
                </div>
                @if (touchedLawyerLists() && selectedMedicalAreas().length === 0) {
                  <p class="text-xs text-red-600">Selecciona al menos un área médica</p>
                }
              </div>
            }

            @if (error()) {
              <p class="text-xs text-red-600 bg-red-50 border border-red-100 rounded-md px-3 py-2">
                {{ error() }}
              </p>
            }
            @if (success()) {
              <p class="text-xs text-emerald-700 bg-emerald-50 border border-emerald-100 rounded-md px-3 py-2">
                {{ success() }}
              </p>
            }

            <button appBtn type="submit" variant="primary" class="w-full" [disabled]="auth.isLoading()">
              {{ auth.isLoading() ? 'Creando cuenta...' : 'Crear cuenta' }}
            </button>
          </form>
        </div>

        <p class="text-center text-sm text-slate-500 mt-5">
          ¿Ya tienes cuenta?
          <a routerLink="/login" class="text-blue-600 hover:underline font-medium">Iniciar sesión</a>
        </p>
      </div>
    </div>
  `,
})
export class RegisterComponent {
  protected readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder);

  protected readonly cn = cn;
  protected readonly legalSpecialties = LEGAL_SPECIALTIES;
  protected readonly medicalSpecialties = MEDICAL_SPECIALTIES;

  protected readonly error = signal<string | null>(null);
  protected readonly success = signal<string | null>(null);
  protected readonly touchedLawyerLists = signal(false);
  protected readonly selectedLegalSpecialties = signal<string[]>([]);
  protected readonly selectedMedicalAreas = signal<string[]>([]);

  protected readonly form = this.fb.nonNullable.group(
    {
      role: ['doctor' as RegisterRole, Validators.required],
      name: ['', Validators.required],
      email: ['', [Validators.required, Validators.email]],
      password: ['', [Validators.required, Validators.minLength(8)]],
      confirmPassword: ['', Validators.required],
      specialty: [''],
      cmp: [''],
      hospital: [''],
      cab: [''],
    },
    { validators: [passwordsMatch] },
  );

  protected setRole(role: RegisterRole): void {
    this.form.controls.role.setValue(role);
    if (role === 'doctor') {
      this.form.controls.specialty.addValidators(Validators.required);
    } else {
      this.form.controls.specialty.clearValidators();
    }
    this.form.controls.specialty.updateValueAndValidity();
  }

  protected toggleLegalSpecialty(value: string): void {
    this.selectedLegalSpecialties.update((list) =>
      list.includes(value) ? list.filter((v) => v !== value) : [...list, value],
    );
  }

  protected toggleMedicalArea(value: string): void {
    this.selectedMedicalAreas.update((list) =>
      list.includes(value) ? list.filter((v) => v !== value) : [...list, value],
    );
  }

  protected async onSubmit(): Promise<void> {
    this.error.set(null);
    this.success.set(null);
    this.touchedLawyerLists.set(true);

    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const { role, name, email, password, specialty, cmp, hospital, cab } = this.form.getRawValue();

    if (role === 'lawyer' && (this.selectedLegalSpecialties().length === 0 || this.selectedMedicalAreas().length === 0)) {
      return;
    }

    try {
      await this.auth.register({
        name,
        email,
        password,
        role,
        ...(role === 'doctor'
          ? { specialty, cmp: cmp || undefined, hospital: hospital || undefined }
          : { cab: cab || undefined, legalSpecialties: this.selectedLegalSpecialties(), medicalAreas: this.selectedMedicalAreas() }),
      });
      this.success.set('Cuenta creada exitosamente. Ya puedes iniciar sesión.');
      setTimeout(() => this.router.navigate(['/login']), 1500);
    } catch (err) {
      this.error.set(err instanceof Error ? err.message : 'No se pudo crear la cuenta');
    }
  }
}

function passwordsMatch(group: { get: (name: string) => { value: string } | null }) {
  const password = group.get('password')?.value;
  const confirmPassword = group.get('confirmPassword')?.value;
  return password && confirmPassword && password !== confirmPassword ? { passwordMismatch: true } : null;
}
