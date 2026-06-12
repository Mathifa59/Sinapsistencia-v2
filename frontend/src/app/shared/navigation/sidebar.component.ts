import { Component, computed, inject } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { LucideAngularModule } from 'lucide-angular';
import { AuthService } from '../../core/auth/auth.service';
import { UiService } from '../layout/ui.service';
import { cn, getInitials } from '../utils/cn';
import {
  NAVIGATION_ADMIN,
  NAVIGATION_DOCTOR,
  NAVIGATION_LAWYER,
  ROLE_PORTAL_LABELS,
  type NavigationItem,
  type UserRole,
} from '../constants';

const NAV_ITEMS: Record<UserRole, NavigationItem[]> = {
  doctor: NAVIGATION_DOCTOR,
  lawyer: NAVIGATION_LAWYER,
  admin: NAVIGATION_ADMIN,
};

/** Sidebar portado de components/navigation/sidebar.tsx — mismas clases. */
@Component({
  selector: 'app-sidebar',
  imports: [RouterLink, LucideAngularModule],
  template: `
    @if (user(); as currentUser) {
      <!-- Mobile overlay -->
      @if (ui.sidebarOpen()) {
        <div class="fixed inset-0 bg-black/30 z-20 lg:hidden" (click)="ui.setSidebarOpen(false)"></div>
      }

      <aside [class]="asideClass()">
        <!-- Logo -->
        <div class="flex items-center justify-between h-16 px-6 border-b border-slate-700">
          <div>
            <span class="font-bold text-white text-lg tracking-tight">Sinapsistencia</span>
            <p class="text-xs text-slate-400 mt-0.5">{{ portalLabel() }}</p>
          </div>
          <button
            type="button"
            (click)="ui.setSidebarOpen(false)"
            class="lg:hidden text-slate-400 hover:text-white"
          >
            <lucide-icon name="x" class="h-5 w-5" />
          </button>
        </div>

        <!-- Navigation -->
        <nav class="flex-1 px-4 py-6 space-y-1 overflow-y-auto">
          @for (item of navItems(); track item.href) {
            <a
              [routerLink]="item.href"
              (click)="ui.setSidebarOpen(false)"
              [class]="linkClass(item.href)"
            >
              <lucide-icon [name]="iconName(item.icon)" class="h-4 w-4 shrink-0" />
              <span class="flex-1">{{ item.label }}</span>
              @if (isActive(item.href)) {
                <lucide-icon name="chevron-right" class="h-3 w-3 opacity-60" />
              }
            </a>
          }
        </nav>

        <!-- User info -->
        <div class="border-t border-slate-700 px-4 py-4">
          <div class="flex items-center gap-3">
            <div
              class="h-8 w-8 rounded-full bg-blue-600 flex items-center justify-center text-xs font-bold text-white shrink-0"
            >
              {{ initials() }}
            </div>
            <div class="min-w-0">
              <p class="text-sm font-medium text-white truncate">{{ currentUser.name }}</p>
              <p class="text-xs text-slate-400 truncate">{{ currentUser.email }}</p>
            </div>
          </div>
        </div>
      </aside>
    }
  `,
})
export class SidebarComponent {
  protected readonly ui = inject(UiService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly user = this.auth.user;

  protected readonly navItems = computed(() => {
    const role = this.auth.role();
    return role ? (NAV_ITEMS[role] ?? []) : [];
  });

  protected readonly portalLabel = computed(() => {
    const role = this.auth.role();
    return role ? ROLE_PORTAL_LABELS[role] : '';
  });

  protected readonly initials = computed(() => getInitials(this.user()?.name ?? ''));

  protected readonly asideClass = computed(() =>
    cn(
      'fixed top-0 left-0 h-full w-64 bg-slate-900 text-white z-30 flex flex-col transition-transform duration-200',
      this.ui.sidebarOpen() ? 'translate-x-0' : '-translate-x-full',
      'lg:relative lg:translate-x-0 lg:z-auto',
    ),
  );

  protected isActive(href: string): boolean {
    const url = this.router.url;
    return url === href || url.startsWith(href + '/');
  }

  protected linkClass(href: string): string {
    return cn(
      'flex items-center gap-3 px-3 py-2.5 rounded-md text-sm font-medium transition-colors group',
      this.isActive(href)
        ? 'bg-blue-600 text-white'
        : 'text-slate-400 hover:bg-slate-800 hover:text-white',
    );
  }

  /** Mapea el nombre PascalCase del legacy al kebab-case de lucide-angular. */
  protected iconName(icon: string): string {
    return icon.replace(/([a-z0-9])([A-Z])/g, '$1-$2').toLowerCase();
  }
}
