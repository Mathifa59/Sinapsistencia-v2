import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { LucideAngularModule } from 'lucide-angular';
import { AuditApi } from '../../../core/api/audit.api';
import { InputDirective } from '../../../shared/ui/field.directives';
import { AuditActionBadgeComponent, RoleBadgeComponent } from '../../../shared/ui/status-badges.component';
import { formatDateTime } from '../../../shared/utils/cn';
import type { AuditAction, UserRole } from '../../../shared/constants';

/** Réplica de app/admin/audit/page.tsx. */
@Component({
  selector: 'app-admin-audit',
  imports: [FormsModule, LucideAngularModule, InputDirective, AuditActionBadgeComponent, RoleBadgeComponent],
  template: `
    <div class="space-y-5">
      <div class="flex items-center gap-3">
        <div class="h-10 w-10 rounded-lg bg-slate-100 flex items-center justify-center shrink-0">
          <lucide-icon name="shield-check" class="h-5 w-5 text-slate-600" />
        </div>
        <div>
          <h1 class="text-2xl font-bold text-slate-900">Auditoría del Sistema</h1>
          <p class="text-slate-500 text-sm mt-1">{{ auditQuery.data()?.total ?? 0 }} eventos registrados</p>
        </div>
      </div>

      <div class="relative">
        <lucide-icon name="search" class="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-slate-400" />
        <input appInput placeholder="Buscar por descripción, usuario o recurso..." class="pl-9" [(ngModel)]="search" />
      </div>

      @if (auditQuery.isLoading()) {
        <div class="flex items-center justify-center py-16 text-slate-400">
          <lucide-icon name="loader-2" class="h-5 w-5 animate-spin mr-2" />
          <span>Cargando auditoría...</span>
        </div>
      } @else {
        <div class="bg-white rounded-lg border border-slate-200 overflow-hidden">
          <table class="w-full text-sm">
            <thead>
              <tr class="border-b border-slate-100 bg-slate-50">
                <th class="text-left px-5 py-3 font-semibold text-slate-600">Acción</th>
                <th class="text-left px-5 py-3 font-semibold text-slate-600">Descripción</th>
                <th class="text-left px-5 py-3 font-semibold text-slate-600 hidden md:table-cell">Usuario</th>
                <th class="text-left px-5 py-3 font-semibold text-slate-600 hidden lg:table-cell">IP</th>
                <th class="text-left px-5 py-3 font-semibold text-slate-600 hidden sm:table-cell">Fecha</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-slate-50">
              @for (log of filteredLogs(); track log.id) {
                <tr class="hover:bg-slate-50 transition-colors">
                  <td class="px-5 py-4">
                    <app-audit-action-badge [action]="asAction(log.action)" />
                  </td>
                  <td class="px-5 py-4">
                    <p class="text-slate-700">{{ log.description }}</p>
                    @if (log.resource) {
                      <p class="text-xs text-slate-400 mt-0.5">{{ log.resource }}{{ log.resourceId ? ' · ' + log.resourceId!.slice(0, 8) : '' }}</p>
                    }
                  </td>
                  <td class="px-5 py-4 hidden md:table-cell">
                    <p class="text-slate-700">{{ log.userName }}</p>
                    <app-role-badge [role]="asRole(log.userRole)" class="mt-1" />
                  </td>
                  <td class="px-5 py-4 text-slate-400 font-mono text-xs hidden lg:table-cell">{{ log.ipAddress }}</td>
                  <td class="px-5 py-4 text-slate-400 hidden sm:table-cell">{{ formatDateTime(log.createdAt ?? '') }}</td>
                </tr>
              }
              @if (filteredLogs().length === 0) {
                <tr>
                  <td colspan="5" class="px-5 py-10 text-center text-slate-400">No se encontraron eventos</td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      }
    </div>
  `,
})
export class AdminAuditComponent {
  private readonly auditApi = inject(AuditApi);

  protected readonly search = signal('');
  protected readonly formatDateTime = formatDateTime;
  protected readonly asAction = (a?: string) => (a ?? 'view') as AuditAction;
  protected readonly asRole = (r?: string) => (r ?? 'doctor') as UserRole;

  protected readonly auditQuery = injectQuery(() => ({
    queryKey: ['audit', { pageSize: 100 }],
    queryFn: () => this.auditApi.list({ pageSize: 100 }),
  }));

  protected readonly filteredLogs = computed(() => {
    const term = this.search().trim().toLowerCase();
    const logs = this.auditQuery.data()?.data ?? [];
    if (!term) return logs;
    return logs.filter(
      (l) =>
        (l.description ?? '').toLowerCase().includes(term) ||
        (l.userName ?? '').toLowerCase().includes(term) ||
        (l.resource ?? '').toLowerCase().includes(term),
    );
  });
}
