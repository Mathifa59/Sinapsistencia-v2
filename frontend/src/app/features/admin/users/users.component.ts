import { Component, computed, inject, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { injectMutation, injectQuery, injectQueryClient } from '@tanstack/angular-query-experimental';
import { LucideAngularModule } from 'lucide-angular';
import { UsersApi } from '../../../core/api/users.api';
import { BtnDirective } from '../../../shared/ui/button.directive';
import { InputDirective } from '../../../shared/ui/field.directives';
import { BadgeDirective } from '../../../shared/ui/badge.directive';
import { RoleBadgeComponent } from '../../../shared/ui/status-badges.component';
import { formatDate, getInitials } from '../../../shared/utils/cn';
import type { UserRole } from '../../../shared/constants';
import { UserFormModalComponent } from './user-form-modal.component';

/** Réplica de app/admin/users/page.tsx. */
@Component({
  selector: 'app-admin-users',
  imports: [FormsModule, LucideAngularModule, BtnDirective, InputDirective, BadgeDirective, RoleBadgeComponent, UserFormModalComponent],
  template: `
    <div class="space-y-5">
      <div class="flex items-center justify-between">
        <div>
          <h1 class="text-2xl font-bold text-slate-900">Usuarios</h1>
          <p class="text-slate-500 text-sm mt-1">{{ usersQuery.data()?.length ?? 0 }} usuarios registrados</p>
        </div>
        <button appBtn variant="primary" size="sm" class="gap-2" (click)="formModal.show()">
          <lucide-icon name="plus" class="h-4 w-4" />Nuevo usuario
        </button>
      </div>

      <div class="relative">
        <lucide-icon name="search" class="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-slate-400" />
        <input appInput placeholder="Buscar por nombre o correo..." class="pl-9" [(ngModel)]="search" />
      </div>

      @if (usersQuery.isLoading()) {
        <div class="flex items-center justify-center py-16 text-slate-400">
          <lucide-icon name="loader-2" class="h-5 w-5 animate-spin mr-2" />
          <span>Cargando usuarios...</span>
        </div>
      } @else {
        <div class="bg-white rounded-lg border border-slate-200 overflow-hidden">
          <table class="w-full text-sm">
            <thead>
              <tr class="border-b border-slate-100 bg-slate-50">
                <th class="text-left px-5 py-3 font-semibold text-slate-600">Usuario</th>
                <th class="text-left px-5 py-3 font-semibold text-slate-600 hidden md:table-cell">Correo</th>
                <th class="text-left px-5 py-3 font-semibold text-slate-600">Rol</th>
                <th class="text-left px-5 py-3 font-semibold text-slate-600">Estado</th>
                <th class="text-left px-5 py-3 font-semibold text-slate-600 hidden lg:table-cell">Registro</th>
                <th class="px-5 py-3"></th>
              </tr>
            </thead>
            <tbody class="divide-y divide-slate-50">
              @for (user of filteredUsers(); track user.id) {
                <tr class="hover:bg-slate-50 transition-colors">
                  <td class="px-5 py-4">
                    <div class="flex items-center gap-3">
                      <div class="h-8 w-8 rounded-full bg-slate-200 flex items-center justify-center text-xs font-bold text-slate-600 shrink-0">
                        {{ getInitials(user.name ?? '') }}
                      </div>
                      <span class="font-medium text-slate-800">{{ user.name }}</span>
                    </div>
                  </td>
                  <td class="px-5 py-4 text-slate-500 hidden md:table-cell">{{ user.email }}</td>
                  <td class="px-5 py-4">
                    <app-role-badge [role]="asRole(user.role)" />
                  </td>
                  <td class="px-5 py-4">
                    <span appBadge [variant]="user.isActive ? 'success' : 'destructive'">
                      {{ user.isActive ? 'Activo' : 'Inactivo' }}
                    </span>
                  </td>
                  <td class="px-5 py-4 text-slate-400 hidden lg:table-cell">{{ formatDate(user.createdAt ?? '') }}</td>
                  <td class="px-5 py-4">
                    <button appBtn variant="outline" size="sm" class="text-xs h-7" [disabled]="toggleMutation.isPending()" (click)="toggle(user.id!)">
                      {{ user.isActive ? 'Desactivar' : 'Activar' }}
                    </button>
                  </td>
                </tr>
              }
              @if (filteredUsers().length === 0) {
                <tr>
                  <td colspan="6" class="px-5 py-10 text-center text-slate-400">No se encontraron usuarios</td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      }
    </div>

    <app-user-form-modal #formModal />
  `,
})
export class AdminUsersComponent {
  private readonly usersApi = inject(UsersApi);
  private readonly queryClient = injectQueryClient();

  protected readonly formModal = viewChild.required(UserFormModalComponent);
  protected readonly search = signal('');
  protected readonly formatDate = formatDate;
  protected readonly getInitials = getInitials;
  protected readonly asRole = (r?: string) => (r ?? 'doctor') as UserRole;

  protected readonly usersQuery = injectQuery(() => ({
    queryKey: ['users'],
    queryFn: () => this.usersApi.list(),
  }));

  protected readonly filteredUsers = computed(() => {
    const term = this.search().trim().toLowerCase();
    const users = this.usersQuery.data() ?? [];
    if (!term) return users;
    return users.filter((u) => (u.name ?? '').toLowerCase().includes(term) || (u.email ?? '').toLowerCase().includes(term));
  });

  protected readonly toggleMutation = injectMutation(() => ({
    mutationFn: (id: string) => this.usersApi.toggleActive(id),
    onSuccess: () => this.queryClient.invalidateQueries({ queryKey: ['users'] }),
  }));

  protected toggle(id: string): void {
    this.toggleMutation.mutate(id);
  }
}
