import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { DashboardLayoutComponent } from './dashboard-layout.component';

/** Shell de portal: sidebar + topbar + router-outlet para las páginas de cada rol. */
@Component({
  selector: 'app-portal-layout',
  imports: [RouterOutlet, DashboardLayoutComponent],
  template: `
    <app-dashboard-layout>
      <router-outlet />
    </app-dashboard-layout>
  `,
})
export class PortalLayoutComponent {}
