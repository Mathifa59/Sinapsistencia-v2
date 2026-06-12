import { Component, input } from '@angular/core';
import { SidebarComponent } from '../navigation/sidebar.component';
import { TopbarComponent } from '../navigation/topbar.component';

/** Shell portado de components/layout/dashboard-layout.tsx — mismas clases. */
@Component({
  selector: 'app-dashboard-layout',
  imports: [SidebarComponent, TopbarComponent],
  template: `
    <div class="flex h-screen bg-slate-50 overflow-hidden">
      <app-sidebar />
      <div class="flex flex-col flex-1 min-w-0 overflow-hidden">
        <app-topbar [title]="title()" />
        <main class="flex-1 overflow-y-auto p-4 lg:p-6">
          <ng-content />
        </main>
      </div>
    </div>
  `,
})
export class DashboardLayoutComponent {
  readonly title = input<string>('');
}
