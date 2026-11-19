import { TestBed } from '@angular/core/testing';
import { AvatarComponent } from './avatar.component';
import { StatCardComponent } from './stat-card.component';
import { UiService } from '../layout/ui.service';
import { importProvidersFrom } from '@angular/core';
import { LucideAngularModule, Users } from 'lucide-angular';

describe('AvatarComponent', () => {
  function montar(src: string | null, fallback = 'AB') {
    const fixture = TestBed.createComponent(AvatarComponent);
    fixture.componentRef.setInput('src', src);
    fixture.componentRef.setInput('alt', 'Foto');
    fixture.componentRef.setInput('fallback', fallback);
    fixture.detectChanges();
    return { fixture, el: fixture.nativeElement as HTMLElement };
  }

  it('sin imagen muestra las iniciales', () => {
    const { el } = montar(null, 'DP');
    expect(el.querySelector('img')).toBeNull();
    expect(el.textContent?.trim()).toBe('DP');
  });

  it('con imagen la muestra', () => {
    const { el } = montar('https://ejemplo.pe/foto.png');
    expect(el.querySelector('img')?.getAttribute('src')).toBe('https://ejemplo.pe/foto.png');
  });

  it('si la imagen falla, vuelve a las iniciales', () => {
    const { fixture, el } = montar('https://ejemplo.pe/rota.png', 'DP');
    el.querySelector('img')!.dispatchEvent(new Event('error'));
    fixture.detectChanges();
    expect(el.querySelector('img')).toBeNull();
    expect(el.textContent?.trim()).toBe('DP');
  });
});

describe('StatCardComponent', () => {
  it('muestra título, valor y descripción', () => {
    TestBed.configureTestingModule({ providers: [importProvidersFrom(LucideAngularModule.pick({ Users }))] });
    const fixture = TestBed.createComponent(StatCardComponent);
    fixture.componentRef.setInput('title', 'Consultas');
    fixture.componentRef.setInput('value', 12);
    fixture.componentRef.setInput('icon', 'users');
    fixture.componentRef.setInput('description', 'abiertas');
    fixture.detectChanges();
    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).toContain('Consultas');
    expect(texto).toContain('12');
    expect(texto).toContain('abiertas');
  });

  it('sin descripción no deja un párrafo vacío', () => {
    TestBed.configureTestingModule({ providers: [importProvidersFrom(LucideAngularModule.pick({ Users }))] });
    const fixture = TestBed.createComponent(StatCardComponent);
    fixture.componentRef.setInput('title', 'Consultas');
    fixture.componentRef.setInput('value', 0);
    fixture.componentRef.setInput('icon', 'users');
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).querySelectorAll('p').length).toBe(2);
  });
});

describe('UiService', () => {
  it('el menú lateral móvil empieza cerrado y se alterna', () => {
    const ui = TestBed.inject(UiService);
    expect(ui.sidebarOpen()).toBe(false);
    ui.toggleSidebar();
    expect(ui.sidebarOpen()).toBe(true);
    ui.toggleSidebar();
    expect(ui.sidebarOpen()).toBe(false);
  });

  it('se puede fijar explícitamente', () => {
    const ui = TestBed.inject(UiService);
    ui.setSidebarOpen(true);
    expect(ui.sidebarOpen()).toBe(true);
    ui.setSidebarOpen(false);
    expect(ui.sidebarOpen()).toBe(false);
  });
});
