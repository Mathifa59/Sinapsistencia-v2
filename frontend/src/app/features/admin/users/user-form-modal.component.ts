import { Component, inject, output, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { injectMutation, injectQueryClient } from '@tanstack/angular-query-experimental';
import { LucideAngularModule } from 'lucide-angular';
import { UsersApi } from '../../../core/api/users.api';
import { ModalComponent, ModalHeaderDirective, ModalTitleDirective, ModalDescriptionDirective, ModalFooterDirective } from '../../../shared/ui/modal.component';
import { BtnDirective } from '../../../shared/ui/button.directive';
import { InputDirective, LabelDirective, SelectDirective } from '../../../shared/ui/field.directives';
import { ROLE_LABELS, type UserRole } from '../../../shared/constants';

const ROLES: UserRole[] = ['doctor', 'lawyer', 'admin'];

/** Modal de creación de usuario, adaptación de UserFormModal.tsx. */
@Component({
  selector: 'app-user-form-modal',
  imports: [ReactiveFormsModule, LucideAngularModule, ModalComponent, ModalHeaderDirective, ModalTitleDirective, ModalDescriptionDirective, ModalFooterDirective, BtnDirective, InputDirective, LabelDirective, SelectDirective],
  template: `
    <app-modal [open]="opened()" (close)="handleClose()">
      <div appModalHeader>
        <h2 appModalTitle>Nuevo usuario</h2>
        <p appModalDescription>Crea una cuenta para un médico, abogado o administrador.</p>
      </div>

      <form [formGroup]="form" (ngSubmit)="onSubmit()" class="space-y-4">
        <div class="space-y-1.5">
          <label appLabel for="user-name">Nombre completo *</label>
          <input appInput id="user-name" placeholder="Ej: Dra. María García" formControlName="name" />
          @if (form.controls.name.invalid && form.controls.name.touched) {
            <p class="text-xs text-red-500">El nombre debe tener al menos 3 caracteres</p>
          }
        </div>

        <div class="space-y-1.5">
          <label appLabel for="user-email">Correo electrónico *</label>
          <input appInput id="user-email" type="email" placeholder="correo@sinapsistencia.com" formControlName="email" />
          @if (form.controls.email.invalid && form.controls.email.touched) {
            <p class="text-xs text-red-500">Ingresa un correo válido</p>
          }
        </div>

        <div class="grid grid-cols-2 gap-4">
          <div class="space-y-1.5">
            <label appLabel>Rol *</label>
            <select appSelect formControlName="role">
              @for (role of roles; track role) {
                <option [value]="role">{{ roleLabels[role] }}</option>
              }
            </select>
          </div>

          <div class="space-y-1.5">
            <label appLabel for="user-password">Contraseña *</label>
            <input appInput id="user-password" type="password" placeholder="Mínimo 6 caracteres" formControlName="password" />
            @if (form.controls.password.invalid && form.controls.password.touched) {
              <p class="text-xs text-red-500">Mínimo 6 caracteres</p>
            }
          </div>
        </div>

        @if (serverError()) {
          <p class="text-xs text-red-600 bg-red-50 border border-red-100 rounded-md px-3 py-2">{{ serverError() }}</p>
        }

        <div appModalFooter>
          <button appBtn type="button" variant="outline" (click)="handleClose()" [disabled]="createMutation.isPending()">
            Cancelar
          </button>
          <button appBtn type="submit" variant="primary" class="gap-2" [disabled]="createMutation.isPending()">
            @if (createMutation.isPending()) {
              <lucide-icon name="loader-2" class="h-4 w-4 animate-spin" />
            }
            Crear usuario
          </button>
        </div>
      </form>
    </app-modal>
  `,
})
export class UserFormModalComponent {
  private readonly fb = inject(FormBuilder);
  private readonly usersApi = inject(UsersApi);
  private readonly queryClient = injectQueryClient();

  protected readonly opened = signal(false);
  readonly closed = output();

  protected readonly roles = ROLES;
  protected readonly roleLabels = ROLE_LABELS;
  protected readonly serverError = signal<string | null>(null);

  protected readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.minLength(3)]],
    email: ['', [Validators.required, Validators.email]],
    role: ['doctor' as UserRole, Validators.required],
    password: ['', [Validators.required, Validators.minLength(6)]],
  });

  protected readonly createMutation = injectMutation(() => ({
    mutationFn: () => {
      const v = this.form.getRawValue();
      return this.usersApi.create({ name: v.name.trim(), email: v.email.trim(), role: v.role, password: v.password });
    },
    onSuccess: () => {
      this.queryClient.invalidateQueries({ queryKey: ['users'] });
      this.form.reset({ role: 'doctor' });
      this.opened.set(false);
      this.closed.emit();
    },
    onError: (err: Error) => this.serverError.set(err.message),
  }));

  show(): void {
    this.serverError.set(null);
    this.opened.set(true);
  }

  protected onSubmit(): void {
    this.serverError.set(null);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.createMutation.mutate();
  }

  protected handleClose(): void {
    this.form.reset({ role: 'doctor' });
    this.serverError.set(null);
    this.opened.set(false);
    this.closed.emit();
  }
}
