import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { LucideAngularModule } from 'lucide-angular';
import { AuthService } from '../../../core/auth/auth.service';
import { CasesApi } from '../../../core/api/cases.api';
import { InputDirective, SelectDirective } from '../../../shared/ui/field.directives';
import { CaseStatusBadgeComponent, CasePriorityBadgeComponent } from '../../../shared/ui/status-badges.component';
import { formatDate } from '../../../shared/utils/cn';
import {
  CASE_STATUS_LABELS,
  CASE_PRIORITY_LABELS,
  type CasePriority,
  type CaseStatus,
} from '../../../shared/constants';

const PRIORITY_ORDER: Record<string, number> = { critica: 0, alta: 1, media: 2, baja: 3 };

/** Panel de casos asignados al abogado (HU-17, HU-34). */
@Component({
  selector: 'app-lawyer-cases',
  imports: [
    FormsModule,
    RouterLink,
    LucideAngularModule,
    InputDirective,
    SelectDirective,
    CaseStatusBadgeComponent,
    CasePriorityBadgeComponent,
  ],
  template: `
    <div class="space-y-5">
      <div>
        <h1 class="text-2xl font-bold text-slate-900">Mis Casos</h1>
        <p class="text-slate-500 text-sm mt-1">{{ sortedCases().length }} casos asignados</p>
      </div>

      <div class="flex flex-wrap gap-3">
        <div class="relative flex-1 min-w-[200px]">
          <lucide-icon name="search" class="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-slate-400" />
          <input appInput placeholder="Buscar por título..." class="pl-9" [(ngModel)]="search" />
        </div>
        <select appSelect class="w-40" [(ngModel)]="statusFilter">
          <option value="">Todos los estados</option>
          @for (s of statusOptions; track s) {
            <option [value]="s">{{ statusLabels[s] }}</option>
          }
        </select>
        <select appSelect class="w-40" [(ngModel)]="priorityFilter">
          <option value="">Todas las prioridades</option>
          @for (p of priorityOptions; track p) {
            <option [value]="p">{{ priorityLabels[p] }}</option>
          }
        </select>
      </div>

      <div class="bg-white rounded-lg border border-slate-200 overflow-hidden">
        <table class="w-full text-sm">
          <thead>
            <tr class="border-b border-slate-100 bg-slate-50">
              <th class="text-left px-5 py-3 font-semibold text-slate-600">Caso</th>
              <th class="text-left px-5 py-3 font-semibold text-slate-600 hidden md:table-cell">Médico</th>
              <th class="text-left px-5 py-3 font-semibold text-slate-600">Estado</th>
              <th class="text-left px-5 py-3 font-semibold text-slate-600 hidden sm:table-cell">Prioridad</th>
              <th class="text-left px-5 py-3 font-semibold text-slate-600 hidden lg:table-cell">Actualizado</th>
              <th class="px-5 py-3"></th>
            </tr>
          </thead>
          <tbody class="divide-y divide-slate-50">
            @if (casesQuery.isLoading()) {
              <tr><td colspan="6" class="px-5 py-10 text-center text-slate-400">Cargando...</td></tr>
            }
            @for (c of sortedCases(); track c.id) {
              <tr class="hover:bg-slate-50 transition-colors">
                <td class="px-5 py-4">
                  <div class="flex items-center gap-3">
                    <div class="h-8 w-8 rounded-md bg-emerald-50 flex items-center justify-center shrink-0">
                      <lucide-icon name="briefcase" class="h-4 w-4 text-emerald-600" />
                    </div>
                    <span class="font-medium text-slate-800 line-clamp-1 max-w-xs">{{ c.title }}</span>
                  </div>
                </td>
                <td class="px-5 py-4 text-slate-600 hidden md:table-cell">{{ c.doctor?.fullName ?? '—' }}</td>
                <td class="px-5 py-4"><app-case-status-badge [status]="asStatus(c.status)" /></td>
                <td class="px-5 py-4 hidden sm:table-cell"><app-case-priority-badge [priority]="asPriority(c.priority)" /></td>
                <td class="px-5 py-4 text-slate-400 hidden lg:table-cell">{{ formatDate(c.updatedAt ?? '') }}</td>
                <td class="px-5 py-4">
                  <a [routerLink]="['/lawyer/cases', c.id]" class="text-emerald-600 hover:underline text-xs font-medium">Ver</a>
                </td>
              </tr>
            }
            @if (!casesQuery.isLoading() && sortedCases().length === 0) {
              <tr><td colspan="6" class="px-5 py-10 text-center text-slate-400">No se encontraron casos</td></tr>
            }
          </tbody>
        </table>
      </div>
    </div>
  `,
})
export class LawyerCasesComponent {
  private readonly auth = inject(AuthService);
  private readonly casesApi = inject(CasesApi);

  protected readonly statusOptions: CaseStatus[] = ['pendiente', 'clasificada', 'asignada', 'en_revision', 'respondida', 'cerrada'];
  protected readonly priorityOptions: CasePriority[] = ['critica', 'alta', 'media', 'baja'];
  protected readonly statusLabels = CASE_STATUS_LABELS;
  protected readonly priorityLabels = CASE_PRIORITY_LABELS;
  protected readonly search = signal('');
  protected readonly statusFilter = signal('');
  protected readonly priorityFilter = signal('');
  protected readonly formatDate = formatDate;
  protected readonly asStatus = (s?: string) => (s ?? 'pendiente') as CaseStatus;
  protected readonly asPriority = (p?: string) => (p ?? 'media') as CasePriority;

  private readonly userId = computed(() => this.auth.user()?.id ?? '');

  protected readonly casesQuery = injectQuery(() => ({
    queryKey: ['cases', { assignedOnly: true, status: this.statusFilter(), priority: this.priorityFilter() }],
    queryFn: () =>
      this.casesApi.list({
        assignedOnly: true,
        status: this.statusFilter() || undefined,
        priority: this.priorityFilter() || undefined,
      }),
    enabled: !!this.userId(),
  }));

  protected readonly sortedCases = computed(() => {
    const term = this.search().trim().toLowerCase();
    let cases = this.casesQuery.data()?.data ?? [];
    if (term) {
      cases = cases.filter((c) => (c.title ?? '').toLowerCase().includes(term));
    }
    return [...cases].sort((a, b) => {
      const pa = PRIORITY_ORDER[a.priority ?? 'media'] ?? 2;
      const pb = PRIORITY_ORDER[b.priority ?? 'media'] ?? 2;
      return pa - pb;
    });
  });
}
