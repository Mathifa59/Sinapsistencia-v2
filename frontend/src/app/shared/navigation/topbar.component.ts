import { Component, computed, inject, input } from '@angular/core';
import { Router } from '@angular/router';
import { CdkMenu, CdkMenuItem, CdkMenuTrigger } from '@angular/cdk/menu';
import { LucideAngularModule } from 'lucide-angular';
import { AuthService } from '../../core/auth/auth.service';
import { UiService } from '../layout/ui.service';
import { getInitials } from '../utils/cn';
import { ROLE_LABELS } from '../constants';
import { BtnDirective } from '../ui/button.directive';
import { AvatarComponent } from '../ui/avatar.component';
import {
  DropdownContentDirective,
  DropdownItemDirective,
  DropdownLabelDirective,
  DropdownSeparatorDirective,
} from '../ui/dropdown.directives';

/** Topbar portado de components/navigation/topbar.tsx — mismas clases. */
@Component({
  selector: 'app-topbar',
  imports: [
    LucideAngularModule,
    CdkMenu,
    CdkMenuItem,
    CdkMenuTrigger,
    BtnDirective,
    AvatarComponent,
    DropdownContentDirective,
    DropdownItemDirective,
    DropdownLabelDirective,
    DropdownSeparatorDirective,
  ],
  template: `
    <header
      class="h-16 bg-white border-b border-slate-200 flex items-center justify-between px-4 lg:px-6 shrink-0"
    >
      <div class="flex items-center gap-4">
        <button
          appBtn
          variant="ghost"
          size="icon"
          class="text-slate-500 hover:text-slate-900"
          (click)="ui.toggleSidebar()"
        >
          <lucide-icon name="menu" class="h-5 w-5" />
        </button>
        @if (title()) {
          <h1 class="text-lg font-semibold text-slate-900 hidden sm:block">{{ title() }}</h1>
        }
      </div>

      <div class="flex items-center gap-3">
        <button appBtn variant="ghost" size="icon" class="relative text-slate-500">
          <lucide-icon name="bell" class="h-5 w-5" />
          <span class="absolute top-1.5 right-1.5 h-2 w-2 rounded-full bg-blue-600"></span>
        </button>

        @if (user(); as currentUser) {
          <button
            type="button"
            [cdkMenuTriggerFor]="userMenu"
            class="flex items-center gap-2 rounded-lg px-2 py-1.5 hover:bg-slate-50 transition-colors outline-none"
          >
            <app-avatar
              class="h-8 w-8"
              [src]="currentUser.avatar"
              [alt]="currentUser.name"
              [fallback]="initials()"
              fallbackClass="bg-blue-600 text-white text-xs"
            />
            <div class="hidden md:block text-left">
              <p class="text-sm font-medium text-slate-900 leading-none">{{ currentUser.name }}</p>
              <p class="text-xs text-slate-500 mt-0.5">{{ roleLabel() }}</p>
            </div>
            <lucide-icon name="chevron-down" class="h-4 w-4 text-slate-400 hidden md:block" />
          </button>

          <ng-template #userMenu>
            <div cdkMenu appDropdownContent class="w-56">
              <div appDropdownLabel class="font-normal">
                <div class="flex flex-col space-y-1">
                  <p class="text-sm font-medium text-slate-900">{{ currentUser.name }}</p>
                  <p class="text-xs text-slate-500 truncate">{{ currentUser.email }}</p>
                </div>
              </div>
              <div appDropdownSeparator></div>
              <button cdkMenuItem appDropdownItem (cdkMenuItemTriggered)="goToProfile()">
                <lucide-icon name="user" class="h-4 w-4 mr-2 text-slate-500" />
                Mi Perfil
              </button>
              <button cdkMenuItem appDropdownItem (cdkMenuItemTriggered)="goToProfile()">
                <lucide-icon name="settings" class="h-4 w-4 mr-2 text-slate-500" />
                Configuración
              </button>
              <div appDropdownSeparator></div>
              <button
                cdkMenuItem
                appDropdownItem
                class="text-red-600 hover:text-red-700 hover:bg-red-50 focus:text-red-700 focus:bg-red-50"
                (cdkMenuItemTriggered)="logout()"
              >
                <lucide-icon name="log-out" class="h-4 w-4 mr-2" />
                Cerrar sesión
              </button>
            </div>
          </ng-template>
        }
      </div>
    </header>
  `,
})
export class TopbarComponent {
  readonly title = input<string>('');

  protected readonly ui = inject(UiService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly user = this.auth.user;
  protected readonly initials = computed(() => getInitials(this.user()?.name ?? ''));
  protected readonly roleLabel = computed(() => {
    const role = this.auth.role();
    return role ? ROLE_LABELS[role] : '';
  });

  protected goToProfile(): void {
    const role = this.auth.role();
    if (role) {
      this.router.navigate([`/${role}/profile`]);
    }
  }

  protected async logout(): Promise<void> {
    await this.auth.logout();
    this.router.navigate(['/login']);
  }
}
