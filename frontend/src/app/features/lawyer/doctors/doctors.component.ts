import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { LucideAngularModule } from 'lucide-angular';
import { MatchingApi } from '../../../core/api/matching.api';
import { BtnDirective } from '../../../shared/ui/button.directive';
import { InputDirective } from '../../../shared/ui/field.directives';
import { BadgeDirective } from '../../../shared/ui/badge.directive';
import { getInitials } from '../../../shared/utils/cn';
import { DoctorDetailModalComponent } from './doctor-detail-modal.component';
import type { DoctorCardDto } from '../../../core/api/generated/model/doctorCardDto';

/** Réplica de app/lawyer/doctors/page.tsx (médicos disponibles + modal de perfil). */
@Component({
  selector: 'app-lawyer-doctors',
  imports: [FormsModule, LucideAngularModule, BtnDirective, InputDirective, BadgeDirective, DoctorDetailModalComponent],
  template: `
    <div class="space-y-5">
      <div>
        <h1 class="text-2xl font-bold text-slate-900">Médicos disponibles</h1>
        <p class="text-slate-500 text-sm mt-1">Profesionales que pueden requerir asesoría legal</p>
      </div>

      <div class="relative">
        <lucide-icon name="search" class="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-slate-400" />
        <input appInput placeholder="Buscar por nombre o especialidad..." class="pl-9" [(ngModel)]="search" />
      </div>

      @if (doctorsQuery.isLoading()) {
        <div class="flex items-center justify-center py-16 text-slate-400">
          <lucide-icon name="loader-2" class="h-5 w-5 animate-spin mr-2" />
          <span>Cargando médicos...</span>
        </div>
      }

      <div class="grid md:grid-cols-2 gap-4">
        @for (doctor of filteredDoctors(); track doctor.id) {
          <div class="bg-white rounded-lg border border-slate-200 p-5 space-y-3">
            <div class="flex items-center gap-4">
              <div class="h-12 w-12 rounded-full bg-blue-600 flex items-center justify-center text-white font-bold text-sm shrink-0">
                {{ getInitials(doctor.fullName ?? '') }}
              </div>
              <div class="flex-1 min-w-0">
                <p class="font-semibold text-slate-900 truncate">{{ doctor.fullName }}</p>
                <p class="text-xs text-slate-500">CMP: {{ doctor.cmp }}</p>
              </div>
              <span appBadge variant="info" class="shrink-0">Activo</span>
            </div>

            <div class="space-y-1.5 text-sm">
              <div class="flex items-center gap-2 text-slate-600">
                <lucide-icon name="stethoscope" class="h-3.5 w-3.5 text-slate-400 shrink-0" />
                {{ doctor.specialty }}
              </div>
              <div class="flex items-center gap-2 text-slate-600">
                <lucide-icon name="building-2" class="h-3.5 w-3.5 text-slate-400 shrink-0" />
                {{ doctor.hospital }}
              </div>
            </div>

            @if (doctor.bio) {
              <p class="text-xs text-slate-500 line-clamp-2 leading-relaxed">{{ doctor.bio }}</p>
            }

            <div class="flex gap-2 pt-1">
              <span appBadge variant="secondary" class="text-xs">{{ doctor.yearsExperience }} años exp.</span>
            </div>

            <button appBtn variant="outline" size="sm" class="w-full text-xs" (click)="selected.set(doctor)">
              Ver perfil completo
            </button>
          </div>
        }

        @if (!doctorsQuery.isLoading() && filteredDoctors().length === 0) {
          <div class="col-span-full text-center py-16 text-slate-400">No se encontraron médicos</div>
        }
      </div>

      <app-doctor-detail-modal [doctor]="selected()" (close)="selected.set(null)" />
    </div>
  `,
})
export class LawyerDoctorsComponent {
  private readonly matchingApi = inject(MatchingApi);

  protected readonly search = signal('');
  protected readonly selected = signal<DoctorCardDto | null>(null);
  protected readonly getInitials = getInitials;

  protected readonly doctorsQuery = injectQuery(() => ({
    queryKey: ['matching', 'doctors'],
    queryFn: () => this.matchingApi.doctors(),
  }));

  protected readonly filteredDoctors = computed(() => {
    const term = this.search().trim().toLowerCase();
    const doctors = this.doctorsQuery.data() ?? [];
    if (!term) return doctors;
    return doctors.filter(
      (d) => (d.fullName ?? '').toLowerCase().includes(term) || (d.specialty ?? '').toLowerCase().includes(term),
    );
  });
}
