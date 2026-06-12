import { Component, computed, inject, signal, viewChild, ElementRef, effect } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { injectMutation, injectQuery, injectQueryClient } from '@tanstack/angular-query-experimental';
import { LucideAngularModule } from 'lucide-angular';
import { AuthService } from '../../../core/auth/auth.service';
import { ProfileApi } from '../../../core/api/profile.api';
import { BtnDirective } from '../../../shared/ui/button.directive';
import { InputDirective, LabelDirective } from '../../../shared/ui/field.directives';
import { BadgeDirective } from '../../../shared/ui/badge.directive';
import { AvatarComponent } from '../../../shared/ui/avatar.component';
import { getInitials } from '../../../shared/utils/cn';

/** Réplica de app/admin/profile/page.tsx: solo nombre editable. */
@Component({
  selector: 'app-admin-profile',
  imports: [ReactiveFormsModule, LucideAngularModule, BtnDirective, InputDirective, LabelDirective, BadgeDirective, AvatarComponent],
  template: `
    <div class="max-w-3xl mx-auto space-y-6">
      <div>
        <h1 class="text-2xl font-bold text-slate-900">Mi Perfil</h1>
        <p class="text-slate-500 text-sm mt-1">Gestiona tu información de cuenta</p>
      </div>

      @if (profileQuery.isLoading()) {
        <div class="flex items-center justify-center py-20 text-slate-400">
          <lucide-icon name="loader-2" class="h-6 w-6 animate-spin mr-2" />
          <span>Cargando perfil...</span>
        </div>
      } @else {
        <div class="rounded-xl border border-slate-200 shadow-sm overflow-hidden bg-white">
          <div class="h-24 bg-gradient-to-r from-slate-800 via-slate-700 to-slate-600"></div>
          <div class="px-6 pb-6">
            <div class="flex items-end gap-4 -mt-10">
              <div class="relative">
                <app-avatar
                  [src]="avatarPreview() ?? profile()?.avatar ?? null"
                  [fallback]="getInitials(authUser()?.name ?? '')"
                  fallbackClass="bg-slate-800 text-white"
                  class="h-20 w-20 ring-4 ring-white text-lg"
                />
                <button
                  type="button"
                  class="absolute -bottom-1 -right-1 h-7 w-7 rounded-full bg-white border border-slate-200 shadow-sm flex items-center justify-center hover:bg-slate-50"
                  (click)="fileInput.click()"
                  [disabled]="avatarMutation.isPending()"
                >
                  @if (avatarMutation.isPending()) {
                    <lucide-icon name="loader-2" class="h-3.5 w-3.5 animate-spin text-slate-500" />
                  } @else {
                    <lucide-icon name="camera" class="h-3.5 w-3.5 text-slate-500" />
                  }
                </button>
                <input #fileInputEl type="file" accept="image/*" class="hidden" (change)="onAvatarSelected($event)" />
              </div>
              <div class="pt-10">
                <p class="font-semibold text-slate-900">{{ authUser()?.name }}</p>
                <p class="text-sm text-slate-500">{{ authUser()?.email }}</p>
              </div>
            </div>

            <div class="flex flex-wrap gap-2 mt-4">
              <span appBadge variant="warning" class="gap-1">
                <lucide-icon name="shield-check" class="h-3 w-3" />Administrador
              </span>
            </div>
          </div>
        </div>

        <div class="bg-white rounded-xl border border-slate-200 shadow-sm p-6">
          <h3 class="text-base font-semibold text-slate-900 mb-5">Datos de la cuenta</h3>

          <form [formGroup]="form" (ngSubmit)="onSubmit()" class="space-y-5">
            <div class="grid sm:grid-cols-2 gap-4">
              <div class="space-y-1.5">
                <label appLabel>Nombre completo</label>
                <input appInput formControlName="name" />
                @if (form.controls.name.invalid && form.controls.name.touched) {
                  <p class="text-xs text-red-600">El nombre debe tener al menos 3 caracteres</p>
                }
              </div>

              <div class="space-y-1.5">
                <label appLabel>Correo electrónico</label>
                <input appInput [value]="authUser()?.email ?? ''" disabled class="bg-slate-50" />
                <p class="text-xs text-slate-400">No se puede modificar</p>
              </div>
            </div>

            @if (saveError()) {
              <div class="flex items-start gap-2 text-sm text-red-700 bg-red-50 border border-red-100 rounded-md px-3 py-2.5">
                <lucide-icon name="x-circle" class="h-4 w-4 shrink-0 mt-0.5" />
                {{ saveError() }}
              </div>
            }
            @if (saveSuccess()) {
              <div class="flex items-start gap-2 text-sm text-emerald-700 bg-emerald-50 border border-emerald-100 rounded-md px-3 py-2.5">
                <lucide-icon name="check-circle-2" class="h-4 w-4 shrink-0 mt-0.5" />
                Cambios guardados correctamente.
              </div>
            }

            <div class="flex justify-end pt-2">
              <button appBtn type="submit" variant="primary" class="gap-2" [disabled]="saveMutation.isPending() || form.pristine">
                @if (saveMutation.isPending()) {
                  <lucide-icon name="loader-2" class="h-4 w-4 animate-spin" />Guardando...
                } @else {
                  <lucide-icon name="save" class="h-4 w-4" />Guardar cambios
                }
              </button>
            </div>
          </form>
        </div>
      }
    </div>
  `,
})
export class AdminProfileComponent {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly profileApi = inject(ProfileApi);
  private readonly queryClient = injectQueryClient();

  protected readonly getInitials = getInitials;
  protected readonly authUser = this.auth.user;

  protected readonly fileInputRef = viewChild.required<ElementRef<HTMLInputElement>>('fileInputEl');
  protected get fileInput(): HTMLInputElement {
    return this.fileInputRef().nativeElement;
  }

  protected readonly avatarPreview = signal<string | null>(null);
  protected readonly saveError = signal<string | null>(null);
  protected readonly saveSuccess = signal(false);

  private readonly userId = computed(() => this.auth.user()?.id ?? '');

  protected readonly profileQuery = injectQuery(() => ({
    queryKey: ['profile', this.userId()],
    queryFn: () => this.profileApi.get(this.userId()),
    enabled: !!this.userId(),
  }));

  protected readonly profile = computed(() => this.profileQuery.data());

  protected readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.minLength(3)]],
  });

  constructor() {
    effect(() => {
      const data = this.profileQuery.data();
      if (!data) return;
      this.form.reset({ name: data.name ?? '' });
    });
  }

  protected readonly saveMutation = injectMutation(() => ({
    mutationFn: () => {
      const v = this.form.getRawValue();
      return this.profileApi.patch({ userId: this.userId(), name: v.name.trim() });
    },
    onSuccess: () => {
      this.queryClient.invalidateQueries({ queryKey: ['profile', this.userId()] });
      this.saveSuccess.set(true);
      this.saveError.set(null);
      setTimeout(() => this.saveSuccess.set(false), 3000);
    },
    onError: () => {
      this.saveError.set('Error al guardar los cambios. Intenta de nuevo.');
      this.saveSuccess.set(false);
    },
  }));

  protected readonly avatarMutation = injectMutation(() => ({
    mutationFn: (form: FormData) => this.profileApi.uploadAvatar(form),
    onSuccess: () => this.queryClient.invalidateQueries({ queryKey: ['profile', this.userId()] }),
  }));

  protected onAvatarSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;

    this.avatarPreview.set(URL.createObjectURL(file));
    const form = new FormData();
    form.append('file', file);
    this.avatarMutation.mutate(form);
  }

  protected onSubmit(): void {
    this.saveError.set(null);
    this.saveSuccess.set(false);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.saveMutation.mutate();
  }
}
