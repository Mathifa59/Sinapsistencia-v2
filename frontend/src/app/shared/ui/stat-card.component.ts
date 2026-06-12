import { Component, computed, input } from '@angular/core';
import { LucideAngularModule } from 'lucide-angular';
import { cn } from '../utils/cn';

type StatCardColor = 'blue' | 'emerald' | 'amber' | 'red' | 'slate';

const COLOR_MAP: Record<StatCardColor, { bg: string; icon: string; value: string }> = {
  blue: { bg: 'bg-blue-50', icon: 'text-blue-600', value: 'text-blue-700' },
  emerald: { bg: 'bg-emerald-50', icon: 'text-emerald-600', value: 'text-emerald-700' },
  amber: { bg: 'bg-amber-50', icon: 'text-amber-600', value: 'text-amber-700' },
  red: { bg: 'bg-red-50', icon: 'text-red-600', value: 'text-red-700' },
  slate: { bg: 'bg-slate-100', icon: 'text-slate-600', value: 'text-slate-700' },
};

/** Tarjeta de estadística portada verbatim de components/dashboard/stat-card.tsx. */
@Component({
  selector: 'app-stat-card',
  imports: [LucideAngularModule],
  template: `
    <div class="bg-white rounded-lg border border-slate-200 p-5 flex items-start gap-4">
      <div [class]="iconWrapClass()">
        <lucide-icon [name]="icon()" [class]="iconClass()" />
      </div>
      <div class="min-w-0 flex-1">
        <p class="text-sm text-slate-500 font-medium">{{ title() }}</p>
        <p [class]="valueClass()">{{ value() }}</p>
        @if (description()) {
          <p class="text-xs text-slate-400 mt-1">{{ description() }}</p>
        }
      </div>
    </div>
  `,
})
export class StatCardComponent {
  readonly title = input.required<string>();
  readonly value = input.required<string | number>();
  readonly description = input<string>('');
  readonly icon = input.required<string>();
  readonly color = input<StatCardColor>('slate');

  protected readonly iconWrapClass = computed(() =>
    cn('h-10 w-10 rounded-lg flex items-center justify-center shrink-0', COLOR_MAP[this.color()].bg),
  );
  protected readonly iconClass = computed(() => cn('h-5 w-5', COLOR_MAP[this.color()].icon));
  protected readonly valueClass = computed(() => cn('text-2xl font-bold mt-0.5', COLOR_MAP[this.color()].value));
}
