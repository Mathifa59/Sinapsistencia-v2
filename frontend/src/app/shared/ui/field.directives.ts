import { Directive, computed, input } from '@angular/core';
import type { ClassValue } from 'clsx';
import { cn } from '../utils/cn';

/** Clases portadas verbatim de components/ui/input.tsx. */
@Directive({
  selector: 'input[appInput]',
  host: { '[class]': 'computedClass()' },
})
export class InputDirective {
  readonly userClass = input<ClassValue>('', { alias: 'class' });

  protected readonly computedClass = computed(() =>
    cn(
      'flex h-9 w-full rounded-md border border-slate-200 bg-white px-3 py-1 text-sm shadow-sm transition-colors placeholder:text-slate-400 focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-slate-400 disabled:cursor-not-allowed disabled:opacity-50',
      this.userClass(),
    ),
  );
}

/** Clases portadas verbatim de components/ui/textarea.tsx. */
@Directive({
  selector: 'textarea[appTextarea]',
  host: { '[class]': 'computedClass()' },
})
export class TextareaDirective {
  readonly userClass = input<ClassValue>('', { alias: 'class' });

  protected readonly computedClass = computed(() =>
    cn(
      'flex min-h-[80px] w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm shadow-sm placeholder:text-slate-400 focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-slate-400 disabled:cursor-not-allowed disabled:opacity-50',
      this.userClass(),
    ),
  );
}

/** Clases portadas verbatim de components/ui/label.tsx. */
@Directive({
  selector: 'label[appLabel]',
  host: { '[class]': 'computedClass()' },
})
export class LabelDirective {
  readonly userClass = input<ClassValue>('', { alias: 'class' });

  protected readonly computedClass = computed(() =>
    cn(
      'text-sm font-medium text-slate-700 leading-none peer-disabled:cursor-not-allowed peer-disabled:opacity-70',
      this.userClass(),
    ),
  );
}

/** Equivalente nativo del trigger de components/ui/select.tsx (Radix). */
@Directive({
  selector: 'select[appSelect]',
  host: { '[class]': 'computedClass()' },
})
export class SelectDirective {
  readonly userClass = input<ClassValue>('', { alias: 'class' });

  protected readonly computedClass = computed(() =>
    cn(
      'flex h-9 w-full items-center justify-between rounded-md border border-slate-200 bg-white px-3 py-1 text-sm text-slate-900 shadow-sm transition-colors',
      'focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-slate-400',
      'disabled:cursor-not-allowed disabled:opacity-50',
      this.userClass(),
    ),
  );
}
