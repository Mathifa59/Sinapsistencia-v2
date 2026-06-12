import { Directive, computed, input } from '@angular/core';
import type { ClassValue } from 'clsx';
import { cn } from '../utils/cn';

/** Clases portadas verbatim de components/ui/card.tsx. */

@Directive({ selector: '[appCard]', host: { '[class]': 'computedClass()' } })
export class CardDirective {
  readonly userClass = input<ClassValue>('', { alias: 'class' });
  protected readonly computedClass = computed(() =>
    cn('rounded-lg border border-slate-200 bg-white shadow-sm', this.userClass()),
  );
}

@Directive({ selector: '[appCardHeader]', host: { '[class]': 'computedClass()' } })
export class CardHeaderDirective {
  readonly userClass = input<ClassValue>('', { alias: 'class' });
  protected readonly computedClass = computed(() =>
    cn('flex flex-col space-y-1.5 p-6', this.userClass()),
  );
}

@Directive({ selector: '[appCardTitle]', host: { '[class]': 'computedClass()' } })
export class CardTitleDirective {
  readonly userClass = input<ClassValue>('', { alias: 'class' });
  protected readonly computedClass = computed(() =>
    cn('font-semibold leading-none tracking-tight text-slate-900', this.userClass()),
  );
}

@Directive({ selector: '[appCardDescription]', host: { '[class]': 'computedClass()' } })
export class CardDescriptionDirective {
  readonly userClass = input<ClassValue>('', { alias: 'class' });
  protected readonly computedClass = computed(() => cn('text-sm text-slate-500', this.userClass()));
}

@Directive({ selector: '[appCardContent]', host: { '[class]': 'computedClass()' } })
export class CardContentDirective {
  readonly userClass = input<ClassValue>('', { alias: 'class' });
  protected readonly computedClass = computed(() => cn('p-6 pt-0', this.userClass()));
}

@Directive({ selector: '[appCardFooter]', host: { '[class]': 'computedClass()' } })
export class CardFooterDirective {
  readonly userClass = input<ClassValue>('', { alias: 'class' });
  protected readonly computedClass = computed(() =>
    cn('flex items-center p-6 pt-0', this.userClass()),
  );
}
