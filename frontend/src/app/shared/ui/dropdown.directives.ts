import { Directive, computed, input } from '@angular/core';
import type { ClassValue } from 'clsx';
import { cn } from '../utils/cn';

/**
 * Estilos del dropdown-menu legacy aplicados sobre el CDK Menu de Angular
 * (CdkMenuTrigger/CdkMenu/CdkMenuItem reemplazan a Radix DropdownMenu).
 */

@Directive({ selector: '[appDropdownContent]', host: { '[class]': 'computedClass()' } })
export class DropdownContentDirective {
  readonly userClass = input<ClassValue>('', { alias: 'class' });
  protected readonly computedClass = computed(() =>
    cn(
      'z-50 min-w-[8rem] overflow-hidden rounded-lg border border-slate-200 bg-white p-1 shadow-lg block',
      this.userClass(),
    ),
  );
}

@Directive({ selector: '[appDropdownItem]', host: { '[class]': 'computedClass()' } })
export class DropdownItemDirective {
  readonly userClass = input<ClassValue>('', { alias: 'class' });
  protected readonly computedClass = computed(() =>
    cn(
      'relative flex w-full cursor-pointer select-none items-center gap-2 rounded-md px-2 py-1.5 text-sm text-slate-700 outline-none transition-colors hover:bg-slate-100 hover:text-slate-900 focus:bg-slate-100 focus:text-slate-900 data-[disabled]:pointer-events-none data-[disabled]:opacity-50',
      this.userClass(),
    ),
  );
}

@Directive({ selector: '[appDropdownLabel]', host: { '[class]': 'computedClass()' } })
export class DropdownLabelDirective {
  readonly userClass = input<ClassValue>('', { alias: 'class' });
  protected readonly computedClass = computed(() =>
    cn('px-2 py-1.5 text-sm font-semibold text-slate-900', this.userClass()),
  );
}

@Directive({ selector: '[appDropdownSeparator]', host: { '[class]': 'computedClass()' } })
export class DropdownSeparatorDirective {
  readonly userClass = input<ClassValue>('', { alias: 'class' });
  protected readonly computedClass = computed(() =>
    cn('-mx-1 my-1 h-px bg-slate-100 block', this.userClass()),
  );
}
