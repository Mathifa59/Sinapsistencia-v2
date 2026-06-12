import { Component, computed, input } from '@angular/core';
import type { ClassValue } from 'clsx';
import { cn } from '../utils/cn';

/** Clases portadas verbatim de components/ui/separator.tsx. */
@Component({
  selector: 'app-separator',
  template: '',
  host: {
    '[class]': 'computedClass()',
    role: 'none',
  },
})
export class SeparatorComponent {
  readonly orientation = input<'horizontal' | 'vertical'>('horizontal');
  readonly userClass = input<ClassValue>('', { alias: 'class' });

  protected readonly computedClass = computed(() =>
    cn(
      'block shrink-0 bg-slate-200',
      this.orientation() === 'horizontal' ? 'h-[1px] w-full' : 'h-full w-[1px]',
      this.userClass(),
    ),
  );
}
