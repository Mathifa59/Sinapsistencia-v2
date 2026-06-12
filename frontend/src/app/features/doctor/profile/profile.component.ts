import { Component, computed, inject, signal, viewChild, ElementRef, effect } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { injectMutation, injectQuery, injectQueryClient } from '@tanstack/angular-query-experimental';
import { LucideAngularModule } from 'lucide-angular';
import { AuthService } from '../../../core/auth/auth.service';
import { ProfileApi } from '../../../core/api/profile.api';
import { BtnDirective } from '../../../shared/ui/button.directive';
import { InputDirective, LabelDirective, TextareaDirective, SelectDirective } from '../../../shared/ui/field.directives';
import { BadgeDirective } from '../../../shared/ui/badge.directive';
import { AvatarComponent } from '../../../shared/ui/avatar.component';
import { getInitials } from '../../../shared/utils/cn';
import { MEDICAL_SPECIALTIES } from '../../../shared/constants';

/** Réplica de app/doctor/profile/page.tsx (ProfileHeader + form profesional + avatar). */
@Component({
  selector: 'app-doctor-profile',
  imports: [ReactiveFormsModule, LucideAngularModule, BtnDirective, InputDirective, LabelDirective, TextareaDirective, SelectDirective, BadgeDirective, AvatarComponent],
  template: `
    <div class="max-w-3xl mx-auto space-y-6">
      <div>
        <h1 class="text-2xl font-bold text-slate-900">Mi Perfil</h1>
        <p class="text-slate-500 text-sm mt-1">Gestiona tu información profesional</p>
      </div>

      @if (profileQuery.isLoading()) {
        <div class="flex items-center justify-center py-20 text-slate-400">
          <lucide-icon name="loader-2" class="h-6 w-6 animate-spin mr-2" />
          <span>Cargando perfil...</span>
        </div>
      } @else if (!profile()) {
        <div class="text-center py-20 text-slate-400">
          <p>No se encontró el perfil médico.</p>
        </div>
      } @else {
        @let prof = profile()!.professional;

        <div class="rounded-xl border border-slate-200 shadow-sm overflow-hidden bg-white">
          <div class="h-24 bg-gradient-to-r from-blue-600 via-blue-500 to-cyan-500"></div>
          <div class="px-6 pb-6">
            <div class="flex items-end gap-4 -mt-10">
              <div class="relative">
                <app-avatar
                  [src]="avatarPreview() ?? profile()!.avatar ?? null"
                  [fallback]="getInitials(authUser()?.name ?? '')"
                  fallbackClass="bg-blue-600 text-white"
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
              @if (prof?.specialty) {
                <span appBadge variant="info" class="gap-1">
                  <lucide-icon name="stethoscope" class="h-3 w-3" />
                  {{ prof!.specialty }}
                </span>
              }
              @if ((prof?.yearsExperience ?? 0) > 0) {
                <span appBadge variant="secondary" class="gap-1">
                  <lucide-icon name="calendar" class="h-3 w-3" />
                  {{ prof!.yearsExperience }} años exp.
                </span>
              }
              @if (prof?.cmp) {
                <span appBadge variant="secondary" class="gap-1">
                  <lucide-icon name="award" class="h-3 w-3" />
                  CMP: {{ prof!.cmp }}
                </span>
              }
            </div>
          </div>
        </div>

        <div class="bg-white rounded-xl border border-slate-200 shadow-sm p-6">
          <h3 class="text-base font-semibold text-slate-900 mb-5">Información profesional</h3>

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

              <div class="space-y-1.5">
                <label appLabel>Especialidad médica *</label>
                <select appSelect formControlName="specialty">
                  <option value="">Selecciona</option>
                  @for (s of specialties; track s) {
                    <option [value]="s">{{ s }}</option>
                  }
                </select>
                @if (form.controls.specialty.invalid && form.controls.specialty.touched) {
                  <p class="text-xs text-red-600">La especialidad es requerida</p>
                }
              </div>

              <div class="space-y-1.5">
                <label appLabel>CMP (Colegio Médico del Perú)</label>
                <div class="relative">
                  <lucide-icon name="award" class="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-slate-400" />
                  <input appInput formControlName="cmp" class="pl-9" placeholder="Ej: 012345" />
                </div>
              </div>

              <div class="space-y-1.5">
                <label appLabel>Hospital o centro de salud</label>
                <div class="relative">
                  <lucide-icon name="building-2" class="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-slate-400" />
                  <input appInput formControlName="hospital" class="pl-9" placeholder="Ej: Hospital Loayza" />
                </div>
              </div>

              <div class="space-y-1.5">
                <label appLabel>Teléfono</label>
                <div class="relative">
                  <lucide-icon name="phone" class="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-slate-400" />
                  <input appInput formControlName="phone" class="pl-9" placeholder="+51 999 999 999" />
                </div>
              </div>

              <div class="space-y-1.5">
                <label appLabel>Años de experiencia</label>
                <input appInput type="number" min="0" max="60" formControlName="yearsExperience" placeholder="0" />
              </div>
            </div>

            <div class="space-y-1.5">
              <label appLabel>Biografía / Descripción profesional</label>
              <textarea appTextarea rows="4" placeholder="Describe tu experiencia, áreas de investigación, logros profesionales..." formControlName="bio"></textarea>
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
export class DoctorProfileComponent {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly profileApi = inject(ProfileApi);
  private readonly queryClient = injectQueryClient();

  protected readonly specialties = MEDICAL_SPECIALTIES;
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
    specialty: ['', Validators.required],
    cmp: [''],
    hospital: [''],
    phone: [''],
    yearsExperience: [0, [Validators.min(0), Validators.max(60)]],
    bio: [''],
  });

  constructor() {
    effect(() => {
      const data = this.profileQuery.data();
      if (!data) return;
      const prof = data.professional;
      this.form.reset({
        name: data.name ?? '',
        specialty: prof?.specialty ?? '',
        cmp: prof?.cmp ?? '',
        hospital: prof?.hospital ?? '',
        phone: prof?.phone ?? '',
        yearsExperience: prof?.yearsExperience ?? 0,
        bio: prof?.bio ?? '',
      });
    });
  }

  protected readonly saveMutation = injectMutation(() => ({
    mutationFn: () => {
      const v = this.form.getRawValue();
      return this.profileApi.patch({
        userId: this.userId(),
        name: v.name.trim(),
        professional: {
          cmp: v.cmp.trim() || undefined,
          specialty: v.specialty,
          hospital: v.hospital.trim() || undefined,
          phone: v.phone.trim() || undefined,
          bio: v.bio.trim() || undefined,
          yearsExperience: v.yearsExperience,
        },
      });
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
