import { Component, input, output } from '@angular/core';
import { LucideAngularModule } from 'lucide-angular';
import { ModalComponent, ModalHeaderDirective, ModalTitleDirective, ModalFooterDirective } from '../../../shared/ui/modal.component';
import { BtnDirective } from '../../../shared/ui/button.directive';
import { BadgeDirective } from '../../../shared/ui/badge.directive';
import { getInitials } from '../../../shared/utils/cn';
import type { DoctorCardDto } from '../../../core/api/generated/model/doctorCardDto';

/** Adaptación de DoctorProfileModal en app/lawyer/doctors/page.tsx. */
@Component({
  selector: 'app-doctor-detail-modal',
  imports: [LucideAngularModule, ModalComponent, ModalHeaderDirective, ModalTitleDirective, ModalFooterDirective, BtnDirective, BadgeDirective],
  template: `
    <app-modal [open]="!!doctor()" (close)="close.emit()" class="max-w-md">
      @let d = doctor();
      @if (d) {
        <div appModalHeader>
          <div class="flex items-start gap-4">
            <div class="h-14 w-14 rounded-full bg-blue-600 flex items-center justify-center text-white font-bold text-lg shrink-0">
              {{ getInitials(d.fullName ?? '') }}
            </div>
            <div>
              <h2 appModalTitle class="text-left">{{ d.fullName }}</h2>
              <p class="text-sm text-slate-500 mt-0.5">CMP: {{ d.cmp }}</p>
            </div>
          </div>
        </div>

        <div class="space-y-4">
          <div class="grid grid-cols-2 gap-3">
            <div class="bg-slate-50 rounded-md p-3">
              <p class="text-xs text-slate-400 uppercase tracking-wider mb-1">Especialidad</p>
              <p class="text-sm font-medium text-slate-800 flex items-center gap-1.5">
                <lucide-icon name="stethoscope" class="h-3.5 w-3.5 text-slate-400" />
                {{ d.specialty }}
              </p>
            </div>
            <div class="bg-slate-50 rounded-md p-3">
              <p class="text-xs text-slate-400 uppercase tracking-wider mb-1">Hospital</p>
              <p class="text-sm font-medium text-slate-800 flex items-center gap-1.5">
                <lucide-icon name="building-2" class="h-3.5 w-3.5 text-slate-400" />
                {{ d.hospital }}
              </p>
            </div>
          </div>

          <div class="flex flex-wrap items-center gap-3">
            <span appBadge variant="secondary">{{ d.yearsExperience }} años de experiencia</span>
            <span appBadge variant="info">Activo</span>
          </div>

          @if (d.phone) {
            <div class="flex items-center gap-2 text-sm text-slate-600">
              <lucide-icon name="phone" class="h-4 w-4 text-slate-400" />
              {{ d.phone }}
            </div>
          }

          @if (d.bio) {
            <div>
              <p class="text-xs text-slate-400 uppercase tracking-wider mb-1.5">Sobre el médico</p>
              <p class="text-sm text-slate-600 leading-relaxed">{{ d.bio }}</p>
            </div>
          }
        </div>

        <div appModalFooter class="justify-end">
          <button appBtn variant="outline" size="sm" (click)="close.emit()" class="gap-1.5">
            <lucide-icon name="x" class="h-4 w-4" />Cerrar
          </button>
        </div>
      }
    </app-modal>
  `,
})
export class DoctorDetailModalComponent {
  readonly doctor = input<DoctorCardDto | null>(null);
  readonly close = output<void>();
  protected readonly getInitials = getInitials;
}
