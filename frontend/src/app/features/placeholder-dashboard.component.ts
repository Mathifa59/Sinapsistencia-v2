import { Component, computed, inject } from '@angular/core';
import { AuthService } from '../core/auth/auth.service';
import { DashboardLayoutComponent } from '../shared/layout/dashboard-layout.component';
import {
  CardContentDirective,
  CardDescriptionDirective,
  CardDirective,
  CardHeaderDirective,
  CardTitleDirective,
} from '../shared/ui/card.directives';

/**
 * Dashboard placeholder de FASE 6: demuestra el shell (sidebar/topbar/tema).
 * Las pantallas reales por portal llegan en FASE 7.
 */
@Component({
  selector: 'app-placeholder-dashboard',
  imports: [
    DashboardLayoutComponent,
    CardDirective,
    CardHeaderDirective,
    CardTitleDirective,
    CardDescriptionDirective,
    CardContentDirective,
  ],
  template: `
    <app-dashboard-layout title="Dashboard">
      <div appCard>
        <div appCardHeader>
          <h3 appCardTitle>Bienvenido, {{ userName() }}</h3>
          <p appCardDescription>
            Shell del portal verificado (FASE 6). Las funcionalidades de este portal se
            implementan en la FASE 7.
          </p>
        </div>
        <div appCardContent>
          <p class="text-sm text-slate-600">
            Sesión activa contra el backend Spring Boot mediante cookie httpOnly.
          </p>
        </div>
      </div>
    </app-dashboard-layout>
  `,
})
export class PlaceholderDashboardComponent {
  private readonly auth = inject(AuthService);
  protected readonly userName = computed(() => this.auth.user()?.name ?? '');
}
