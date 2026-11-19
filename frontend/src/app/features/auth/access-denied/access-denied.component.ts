import { Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { LucideAngularModule } from 'lucide-angular';
import { AuthService } from '../../../core/auth/auth.service';
import { BtnDirective } from '../../../shared/ui/button.directive';
import { ROLE_DASHBOARD } from '../../../shared/constants';

/**
 * Destino del roleGuard cuando un usuario con sesión abre una ruta de otro rol.
 * Es solo experiencia de frontend: el backend sigue rechazando por su cuenta (403).
 */
@Component({
  selector: 'app-access-denied',
  imports: [RouterLink, LucideAngularModule, BtnDirective],
  template: `
    <div class="min-h-screen bg-slate-50 flex items-center justify-center p-4">
      <div class="w-full max-w-md text-center">
        <img
          src="logo/sinapsistencia-color-horizontal-transparent.svg"
          alt="Sinapsistencia"
          class="h-11 mx-auto mb-6"
        />
        <div class="bg-white rounded-xl border border-slate-200 shadow-sm p-8">
          <div class="mx-auto mb-4 flex h-12 w-12 items-center justify-center rounded-full bg-amber-50">
            <lucide-icon name="shield-alert" class="h-6 w-6 text-amber-600" />
          </div>
          <h1 class="text-xl font-bold text-slate-900">Acceso denegado</h1>
          <p class="mt-2 text-sm text-slate-500">No tienes permisos para acceder a esta sección.</p>
          <a appBtn variant="primary" class="mt-6 w-full" [routerLink]="dashboard()">Volver a mi panel</a>
        </div>
      </div>
    </div>
  `,
})
export class AccessDeniedComponent {
  private readonly auth = inject(AuthService);

  protected readonly dashboard = computed(() => {
    const role = this.auth.role();
    return role ? ROLE_DASHBOARD[role] : '/login';
  });
}
