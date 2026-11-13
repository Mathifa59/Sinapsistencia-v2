import { Component, ElementRef, computed, forwardRef, input, signal, viewChild } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { CdkConnectedOverlay, CdkOverlayOrigin, type ConnectedPosition } from '@angular/cdk/overlay';
import { LucideAngularModule } from 'lucide-angular';
import type { ClassValue } from 'clsx';
import { cn } from '../utils/cn';

export interface ComboboxOption {
  value: string;
  label: string;
}

/** Atajo para listas de texto plano (el valor es igual a la etiqueta). */
export const toOptions = (items: readonly string[]): ComboboxOption[] =>
  items.map((value) => ({ value, label: value }));

/** Urgencia percibida / prioridad (cuatro niveles). */
export const URGENCY_OPTIONS: ComboboxOption[] = [
  { value: 'baja', label: 'Baja' },
  { value: 'media', label: 'Media' },
  { value: 'alta', label: 'Alta' },
  { value: 'critica', label: 'Crítica' },
];

/** Complejidad del procedimiento (tres niveles). */
export const COMPLEXITY_OPTIONS: ComboboxOption[] = URGENCY_OPTIONS.filter((o) => o.value !== 'critica');

/** Sin tildes y en minúsculas, para que "cirugia" encuentre "Cirugía". */
const normalize = (text: string): string =>
  text.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase();

/** Cuántas opciones hacen útil el cuadro de búsqueda (con menos, la lista se ve completa). */
const SEARCH_MIN_OPTIONS = 8;

/**
 * Selector desplegable reemplazo del <select> nativo: abre hacia abajo (solo voltea hacia arriba
 * si no hay espacio), se pinta fuera del contenedor (un modal no lo recorta) y permite escribir
 * para filtrar cuando la lista es larga. Funciona con formControlName y ngModel.
 */
@Component({
  selector: 'app-combobox',
  imports: [CdkOverlayOrigin, CdkConnectedOverlay, LucideAngularModule],
  host: { class: 'block' },
  providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => ComboboxComponent), multi: true }],
  template: `
    <button
      #trigger
      type="button"
      cdkOverlayOrigin
      #origin="cdkOverlayOrigin"
      role="combobox"
      aria-haspopup="listbox"
      [attr.id]="inputId() || null"
      [attr.aria-expanded]="open()"
      [disabled]="disabled()"
      [class]="triggerClasses()"
      (click)="toggle()"
      (keydown)="onTriggerKeydown($event)"
    >
      <span class="truncate" [class.text-slate-400]="!selected()">{{ selected()?.label ?? placeholder() }}</span>
      <lucide-icon name="chevron-down" class="h-4 w-4 shrink-0 text-slate-400" />
    </button>

    <ng-template
      cdkConnectedOverlay
      [cdkConnectedOverlayOrigin]="origin"
      [cdkConnectedOverlayOpen]="open()"
      [cdkConnectedOverlayPositions]="positions"
      [cdkConnectedOverlayMinWidth]="triggerWidth()"
      [cdkConnectedOverlayHasBackdrop]="true"
      cdkConnectedOverlayBackdropClass="cdk-overlay-transparent-backdrop"
      (backdropClick)="close()"
      (attach)="onAttach()"
      (detach)="close()"
    >
      <div
        #panel
        tabindex="-1"
        class="overflow-hidden rounded-md border border-slate-200 bg-white shadow-lg outline-none"
        (keydown)="onPanelKeydown($event)"
      >
        @if (showSearch()) {
          <div class="border-b border-slate-100 p-2">
            <input
              #search
              type="text"
              autocomplete="off"
              placeholder="Buscar..."
              class="h-8 w-full rounded-md border border-slate-200 px-2 text-sm placeholder:text-slate-400 focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-slate-400"
              [value]="query()"
              (input)="onQuery($any($event.target).value)"
            />
          </div>
        }
        <ul role="listbox" class="max-h-56 overflow-y-auto py-1">
          @for (option of filtered(); track option.value; let i = $index) {
            <li
              role="option"
              [attr.data-index]="i"
              [attr.aria-selected]="option.value === value()"
              class="flex cursor-pointer items-center justify-between gap-2 px-3 py-1.5 text-sm text-slate-800"
              [class.bg-slate-100]="i === active()"
              (mouseenter)="active.set(i)"
              (click)="select(option)"
            >
              <span class="truncate">{{ option.label }}</span>
              @if (option.value === value()) {
                <lucide-icon name="check" class="h-4 w-4 shrink-0 text-blue-600" />
              }
            </li>
          } @empty {
            <li class="px-3 py-2 text-sm text-slate-400">Sin resultados</li>
          }
        </ul>
      </div>
    </ng-template>
  `,
})
export class ComboboxComponent implements ControlValueAccessor {
  readonly options = input<ComboboxOption[]>([]);
  readonly placeholder = input('Selecciona...');
  /** Fuerza o desactiva la búsqueda; por defecto aparece desde SEARCH_MIN_OPTIONS opciones. */
  readonly searchable = input<boolean | undefined>(undefined);
  /** Para enlazar con <label for="...">. */
  readonly inputId = input('');
  readonly triggerClass = input<ClassValue>('');

  protected readonly value = signal('');
  protected readonly disabled = signal(false);
  protected readonly open = signal(false);
  protected readonly query = signal('');
  protected readonly active = signal(0);
  protected readonly triggerWidth = signal(0);

  private readonly trigger = viewChild.required<ElementRef<HTMLButtonElement>>('trigger');
  private readonly panel = viewChild<ElementRef<HTMLElement>>('panel');
  private readonly search = viewChild<ElementRef<HTMLInputElement>>('search');

  /** Abajo por defecto; arriba solo si abajo no cabe. */
  protected readonly positions: ConnectedPosition[] = [
    { originX: 'start', originY: 'bottom', overlayX: 'start', overlayY: 'top', offsetY: 4 },
    { originX: 'start', originY: 'top', overlayX: 'start', overlayY: 'bottom', offsetY: -4 },
  ];

  protected readonly selected = computed(() => this.options().find((o) => o.value === this.value()) ?? null);

  protected readonly showSearch = computed(() => this.searchable() ?? this.options().length >= SEARCH_MIN_OPTIONS);

  protected readonly filtered = computed(() => {
    const term = normalize(this.query().trim());
    if (!term) return this.options();
    return this.options().filter((o) => normalize(o.label).includes(term));
  });

  protected readonly triggerClasses = computed(() =>
    cn(
      'flex h-9 w-full items-center justify-between gap-2 rounded-md border border-slate-200 bg-white px-3 py-1 text-left text-sm text-slate-900 shadow-sm transition-colors',
      'focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-slate-400',
      'disabled:cursor-not-allowed disabled:opacity-50',
      this.triggerClass(),
    ),
  );

  private onChange: (value: string) => void = () => {};
  private onTouched: () => void = () => {};

  writeValue(value: string | null | undefined): void {
    this.value.set(value ?? '');
  }

  registerOnChange(fn: (value: string) => void): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }

  setDisabledState(isDisabled: boolean): void {
    this.disabled.set(isDisabled);
  }

  protected toggle(): void {
    if (this.open()) {
      this.close();
      return;
    }
    this.openPanel();
  }

  private openPanel(): void {
    if (this.disabled()) return;
    this.triggerWidth.set(this.trigger().nativeElement.offsetWidth);
    this.query.set('');
    const index = this.options().findIndex((o) => o.value === this.value());
    this.active.set(Math.max(index, 0));
    this.open.set(true);
  }

  protected close(): void {
    if (!this.open()) return;
    this.open.set(false);
    this.onTouched();
    this.trigger().nativeElement.focus();
  }

  protected select(option: ComboboxOption): void {
    this.value.set(option.value);
    this.onChange(option.value);
    this.close();
  }

  protected onQuery(text: string): void {
    this.query.set(text);
    this.active.set(0);
  }

  /** Con el panel ya en el DOM: el foco va al buscador, o al panel si no hay búsqueda. */
  protected onAttach(): void {
    setTimeout(() => {
      (this.search()?.nativeElement ?? this.panel()?.nativeElement)?.focus();
      this.scrollActiveIntoView();
    });
  }

  protected onTriggerKeydown(event: KeyboardEvent): void {
    if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      event.preventDefault();
      if (!this.open()) this.openPanel();
    }
  }

  protected onPanelKeydown(event: KeyboardEvent): void {
    const count = this.filtered().length;
    switch (event.key) {
      case 'ArrowDown':
        event.preventDefault();
        this.active.set(count ? (this.active() + 1) % count : 0);
        this.scrollActiveIntoView();
        break;
      case 'ArrowUp':
        event.preventDefault();
        this.active.set(count ? (this.active() - 1 + count) % count : 0);
        this.scrollActiveIntoView();
        break;
      case 'Enter': {
        event.preventDefault();
        const option = this.filtered()[this.active()];
        if (option) this.select(option);
        break;
      }
      case 'Escape':
        event.preventDefault();
        event.stopPropagation();
        this.close();
        break;
      case 'Tab':
        this.close();
        break;
    }
  }

  private scrollActiveIntoView(): void {
    queueMicrotask(() => {
      this.panel()
        ?.nativeElement.querySelector(`[data-index="${this.active()}"]`)
        ?.scrollIntoView({ block: 'nearest' });
    });
  }
}
