import { Component, computed, inject, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { LucideAngularModule } from 'lucide-angular';
import { AuthService } from '../../../core/auth/auth.service';
import { CasesApi } from '../../../core/api/cases.api';
import { BtnDirective } from '../../../shared/ui/button.directive';
import { InputDirective } from '../../../shared/ui/field.directives';
import { CaseStatusBadgeComponent, CasePriorityBadgeComponent } from '../../../shared/ui/status-badges.component';
import { formatDate } from '../../../shared/utils/cn';
import type { CasePriority, CaseStatus } from '../../../shared/constants';
import { CaseFormModalComponent } from './case-form-modal.component';

/** Réplica de app/doctor/cases/page.tsx, sin columna de Paciente. */
@Component({
  selector: 'app-doctor-cases',
  imports: [
    FormsModule,
    RouterLink,
    LucideAngularModule,
    BtnDirective,
    InputDirective,
    CaseStatusBadgeComponent,
    CasePriorityBadgeComponent,
    CaseFormModalComponent,
  ],
  template: `
    <div class="space-y-5">
      <div class="flex items-center justify-between">
        <div>
          <h1 class="text-2xl font-bold text-slate-900">Mis Consultas</h1>
          <p class="text-slate-500 text-sm mt-1">{{ casesQuery.data()?.total ?? 0 }} consultas registradas</p>
        </div>
        <button appBtn variant="primary" size="sm" class="gap-2" (click)="formModal.show()">
          <lucide-icon name="plus" class="h-4 w-4" />Nueva consulta
        </button>
      </div>

      <div class="relative">
        <lucide-icon name="search" class="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-slate-400" />
        <input appInput placeholder="Buscar por título..." class="pl-9" [(ngModel)]="search" />
      </div>

      <div class="bg-white rounded-lg border border-slate-200 overflow-hidden">
        <table class="w-full text-sm">
          <thead>
            <tr class="border-b border-slate-100 bg-slate-50">
              <th class="text-left px-5 py-3 font-semibold text-slate-600">Consulta</th>
              <th class="text-left px-5 py-3 font-semibold text-slate-600 hidden md:table-cell">Código</th>
              <th class="text-left px-5 py-3 font-semibold text-slate-600 hidden lg:table-cell">Abogado</th>
              <th class="text-left px-5 py-3 font-semibold text-slate-600">Estado</th>
              <th class="text-left px-5 py-3 font-semibold text-slate-600 hidden sm:table-cell">Prioridad</th>
              <th class="text-left px-5 py-3 font-semibold text-slate-600 hidden lg:table-cell">Actualizado</th>
              <th class="px-5 py-3"></th>
            </tr>
          </thead>
          <tbody class="divide-y divide-slate-50">
            @if (casesQuery.isLoading()) {
              <tr><td colspan="7" class="px-5 py-10 text-center text-slate-400">Cargando...</td></tr>
            }
            @for (c of filteredCases(); track c.id) {
              <tr class="hover:bg-slate-50 transition-colors">
                <td class="px-5 py-4">
                  <div class="flex items-center gap-3">
                    <div class="h-8 w-8 rounded-md bg-blue-50 flex items-center justify-center shrink-0">
                      <lucide-icon name="briefcase" class="h-4 w-4 text-blue-600" />
                    </div>
                    <span class="font-medium text-slate-800 line-clamp-1 max-w-xs">{{ c.title }}</span>
                  </div>
                </td>
                <td class="px-5 py-4 text-slate-600 hidden md:table-cell">{{ c.context?.contextCode ?? '—' }}</td>
                <td class="px-5 py-4 hidden lg:table-cell">
                  @if (c.lawyer) {
                    <span class="text-slate-700">{{ c.lawyer.fullName }}</span>
                  } @else {
                    <span class="text-slate-400 italic">Sin asignar</span>
                  }
                </td>
                <td class="px-5 py-4"><app-case-status-badge [status]="asStatus(c.status)" /></td>
                <td class="px-5 py-4 hidden sm:table-cell"><app-case-priority-badge [priority]="asPriority(c.priority)" /></td>
                <td class="px-5 py-4 text-slate-400 hidden lg:table-cell">{{ formatDate(c.updatedAt ?? '') }}</td>
                <td class="px-5 py-4">
                  <a [routerLink]="['/doctor/cases', c.id]" class="text-blue-600 hover:underline text-xs font-medium">Ver</a>
                </td>
              </tr>
            }
            @if (!casesQuery.isLoading() && filteredCases().length === 0) {
              <tr><td colspan="7" class="px-5 py-10 text-center text-slate-400">No se encontraron consultas</td></tr>
            }
          </tbody>
        </table>
      </div>
    </div>

    <app-case-form-modal #formModal />
  `,
})
export class DoctorCasesComponent {
  private readonly auth = inject(AuthService);
  private readonly casesApi = inject(CasesApi);

  protected readonly formModal = viewChild.required(CaseFormModalComponent);
  protected readonly search = signal('');
  protected readonly formatDate = formatDate;
  protected readonly asStatus = (s?: string) => (s ?? 'pendiente') as CaseStatus;
  protected readonly asPriority = (p?: string) => (p ?? 'media') as CasePriority;

  private readonly userId = computed(() => this.auth.user()?.id ?? '');

  protected readonly casesQuery = injectQuery(() => ({
    queryKey: ['cases', { doctorId: this.userId() }],
    queryFn: () => this.casesApi.list({ doctorId: this.userId() }),
    enabled: !!this.userId(),
  }));

  protected readonly filteredCases = computed(() => {
    const term = this.search().trim().toLowerCase();
    const cases = this.casesQuery.data()?.data ?? [];
    if (!term) return cases;
    return cases.filter((c) => (c.title ?? '').toLowerCase().includes(term));
  });
}
