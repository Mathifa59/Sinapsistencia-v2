import { Component, inject } from '@angular/core';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { UsersApi } from '../../../core/api/users.api';
import { CasesApi } from '../../../core/api/cases.api';
import { DocumentsApi } from '../../../core/api/documents.api';
import { AuditApi } from '../../../core/api/audit.api';
import { StatCardComponent } from '../../../shared/ui/stat-card.component';
import { RoleBadgeComponent, AuditActionBadgeComponent } from '../../../shared/ui/status-badges.component';
import { BadgeDirective } from '../../../shared/ui/badge.directive';
import { formatDateTime, getInitials } from '../../../shared/utils/cn';
import type { UserRole, AuditAction } from '../../../shared/constants';

/** Réplica de app/admin/dashboard/page.tsx, sin pacientes/episodios (modelo académico). */
@Component({
  selector: 'app-admin-dashboard',
  imports: [StatCardComponent, RoleBadgeComponent, AuditActionBadgeComponent, BadgeDirective],
  template: `
    <div class="space-y-6">
      <div>
        <h1 class="text-2xl font-bold text-slate-900">Panel de Administración</h1>
        <p class="text-slate-500 text-sm mt-1">Supervisión general del sistema</p>
      </div>

      <div class="grid grid-cols-2 lg:grid-cols-3 gap-4">
        <app-stat-card title="Usuarios registrados" [value]="usersQuery.data()?.length ?? 0" icon="users" color="blue" description="Total en el sistema" />
        <app-stat-card title="Consultas" [value]="casesQuery.data()?.total ?? 0" icon="activity" color="emerald" description="Flujo de 6 estados" />
        <app-stat-card title="Documentos" [value]="documentsQuery.data()?.total ?? 0" icon="folder-open" color="amber" description="Total procesados" />
        <app-stat-card title="Eventos de auditoría" [value]="auditQuery.data()?.total ?? 0" icon="shield-check" color="slate" description="Registrados" />
        <app-stat-card title="Actividad sistema" value="Alta" icon="zap" color="emerald" description="Estado operativo" />
      </div>

      <div class="grid lg:grid-cols-2 gap-6">
        <div class="bg-white rounded-lg border border-slate-200">
          <div class="px-5 py-4 border-b border-slate-100">
            <h2 class="font-semibold text-slate-900">Usuarios del sistema</h2>
          </div>
          <table class="w-full text-sm">
            <thead>
              <tr class="border-b border-slate-50 bg-slate-50">
                <th class="text-left px-5 py-2.5 text-xs font-semibold text-slate-500 uppercase tracking-wider">Usuario</th>
                <th class="text-left px-5 py-2.5 text-xs font-semibold text-slate-500 uppercase tracking-wider hidden sm:table-cell">Rol</th>
                <th class="text-left px-5 py-2.5 text-xs font-semibold text-slate-500 uppercase tracking-wider">Estado</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-slate-50">
              @for (user of usersQuery.data() ?? []; track user.id) {
                <tr class="hover:bg-slate-50 transition-colors">
                  <td class="px-5 py-3">
                    <div class="flex items-center gap-3">
                      <div class="h-7 w-7 rounded-full bg-slate-200 flex items-center justify-center text-xs font-bold text-slate-600 shrink-0">
                        {{ getInitials(user.name ?? '') }}
                      </div>
                      <div class="min-w-0">
                        <p class="font-medium text-slate-800 truncate">{{ user.name }}</p>
                        <p class="text-xs text-slate-400 truncate hidden sm:block">{{ user.email }}</p>
                      </div>
                    </div>
                  </td>
                  <td class="px-5 py-3 hidden sm:table-cell">
                    <app-role-badge [role]="asRole(user.role)" />
                  </td>
                  <td class="px-5 py-3">
                    <span appBadge [variant]="user.isActive ? 'success' : 'destructive'">
                      {{ user.isActive ? 'Activo' : 'Inactivo' }}
                    </span>
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>

        <div class="bg-white rounded-lg border border-slate-200">
          <div class="px-5 py-4 border-b border-slate-100">
            <h2 class="font-semibold text-slate-900">Auditoría reciente</h2>
          </div>
          <div class="divide-y divide-slate-50">
            @for (log of auditQuery.data()?.data ?? []; track log.id) {
              <div class="px-5 py-3.5 flex items-start gap-3">
                <app-audit-action-badge [action]="asAction(log.action)" class="shrink-0 mt-0.5" />
                <div class="min-w-0 flex-1">
                  <p class="text-xs font-medium text-slate-700 truncate">{{ log.description }}</p>
                  <p class="text-xs text-slate-400 mt-0.5">{{ log.userName }} · {{ formatDateTime(log.createdAt ?? '') }}</p>
                </div>
              </div>
            }
          </div>
        </div>
      </div>
    </div>
  `,
})
export class AdminDashboardComponent {
  private readonly usersApi = inject(UsersApi);
  private readonly casesApi = inject(CasesApi);
  private readonly documentsApi = inject(DocumentsApi);
  private readonly auditApi = inject(AuditApi);

  protected readonly formatDateTime = formatDateTime;
  protected readonly getInitials = getInitials;
  protected readonly asRole = (r?: string) => (r ?? 'doctor') as UserRole;
  protected readonly asAction = (a?: string) => (a ?? 'view') as AuditAction;

  protected readonly usersQuery = injectQuery(() => ({
    queryKey: ['users'],
    queryFn: () => this.usersApi.list(),
  }));

  protected readonly casesQuery = injectQuery(() => ({
    queryKey: ['cases', { pageSize: 1 }],
    queryFn: () => this.casesApi.list({ pageSize: 1 }),
  }));

  protected readonly documentsQuery = injectQuery(() => ({
    queryKey: ['documents', { pageSize: 1 }],
    queryFn: () => this.documentsApi.list({ pageSize: 1 }),
  }));

  protected readonly auditQuery = injectQuery(() => ({
    queryKey: ['audit', { pageSize: 8 }],
    queryFn: () => this.auditApi.list({ pageSize: 8 }),
  }));
}
