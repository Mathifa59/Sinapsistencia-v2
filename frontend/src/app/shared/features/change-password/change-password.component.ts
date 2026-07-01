import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { LucideAngularModule } from 'lucide-angular';
import { AuthService } from '../../../core/auth/auth.service';
import { BtnDirective } from '../../ui/button.directive';
import { InputDirective, LabelDirective } from '../../ui/field.directives';

/** Sección reutilizable de cambio de contraseña (HU-04). */
@Component({
  selector: 'app-change-password',
  imports: [ReactiveFormsModule, LucideAngularModule, BtnDirective, InputDirective, LabelDirective],
  template: `
    <div class="bg-white rounded-xl border border-slate-200 shadow-sm p-6">
      <h3 class="text-base font-semibold text-slate-900 mb-1 flex items-center gap-2">
        <lucide-icon name="lock" class="h-4 w-4 text-slate-600" />
        Cambiar contraseña
      </h3>
      <p class="text-xs text-slate-400 mb-5">Actualiza tu contraseña de acceso al sistema</p>

      <form [formGroup]="form" (ngSubmit)="onSubmit()" class="space-y-4 max-w-md">
        <div class="space-y-1.5">
          <label appLabel for="current-password">Contraseña actual</label>
          <input appInput id="current-password" type="password" formControlName="currentPassword" autocomplete="current-password" />
          @if (form.controls.currentPassword.invalid && form.controls.currentPassword.touched) {
            <p class="text-xs text-red-600">La contraseña actual es requerida</p>
          }
        </div>

        <div class="space-y-1.5">
          <label appLabel for="new-password">Nueva contraseña</label>
          <input appInput id="new-password" type="password" formControlName="newPassword" autocomplete="new-password" />
          @if (form.controls.newPassword.invalid && form.controls.newPassword.touched) {
            <p class="text-xs text-red-600">Mínimo 8 caracteres</p>
          }
        </div>

        <div class="space-y-1.5">
          <label appLabel for="confirm-password">Confirmar nueva contraseña</label>
          <input appInput id="confirm-password" type="password" formControlName="confirmPassword" autocomplete="new-password" />
          @if (form.hasError('mismatch') && form.controls.confirmPassword.touched) {
            <p class="text-xs text-red-600">Las contraseñas no coinciden</p>
          }
        </div>

        @if (error()) {
          <div class="flex items-start gap-2 text-sm text-red-700 bg-red-50 border border-red-100 rounded-md px-3 py-2.5">
            <lucide-icon name="x-circle" class="h-4 w-4 shrink-0 mt-0.5" />
            {{ error() }}
          </div>
        }
        @if (success()) {
          <div class="flex items-start gap-2 text-sm text-emerald-700 bg-emerald-50 border border-emerald-100 rounded-md px-3 py-2.5">
            <lucide-icon name="check-circle-2" class="h-4 w-4 shrink-0 mt-0.5" />
            Contraseña actualizada correctamente.
          </div>
        }

        <button appBtn type="submit" variant="primary" class="gap-2" [disabled]="isLoading()">
          @if (isLoading()) {
            <lucide-icon name="loader-2" class="h-4 w-4 animate-spin" />Actualizando...
          } @else {
            <lucide-icon name="key" class="h-4 w-4" />Actualizar contraseña
          }
        </button>
      </form>
    </div>
  `,
})
export class ChangePasswordComponent {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);

  protected readonly isLoading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly success = signal(false);

  protected readonly form = this.fb.nonNullable.group(
    {
      currentPassword: ['', Validators.required],
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
    this.success.set(false);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const { currentPassword, newPassword } = this.form.getRawValue();
    this.isLoading.set(true);
    try {
      await this.auth.changePassword(currentPassword, newPassword);
      this.form.reset();
      this.success.set(true);
      setTimeout(() => this.success.set(false), 4000);
    } catch {
      this.error.set('No se pudo cambiar la contraseña. Verifica la contraseña actual.');
    } finally {
      this.isLoading.set(false);
    }
  }
}
