import { Injectable, signal } from '@angular/core';

/** Reemplaza el ui.store de Zustand (estado del sidebar móvil). */
@Injectable({ providedIn: 'root' })
export class UiService {
  private readonly _sidebarOpen = signal(false);

  readonly sidebarOpen = this._sidebarOpen.asReadonly();

  toggleSidebar(): void {
    this._sidebarOpen.update((open) => !open);
  }

  setSidebarOpen(open: boolean): void {
    this._sidebarOpen.set(open);
  }
}
