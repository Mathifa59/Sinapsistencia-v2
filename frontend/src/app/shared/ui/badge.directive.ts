import { Directive, computed, input } from '@angular/core';
import { cva, type VariantProps } from 'class-variance-authority';
import type { ClassValue } from 'clsx';
import { cn } from '../utils/cn';

/** Variantes CVA portadas verbatim de components/ui/badge.tsx. */
export const badgeVariants = cva(
  'inline-flex items-center rounded-full border px-2.5 py-0.5 text-xs font-semibold transition-colors',
  {
    variants: {
      variant: {
        default: 'border-transparent bg-slate-900 text-white',
        secondary: 'border-transparent bg-slate-100 text-slate-900',
        destructive: 'border-transparent bg-red-100 text-red-700',
        outline: 'text-slate-900',
        success: 'border-transparent bg-emerald-100 text-emerald-700',
        warning: 'border-transparent bg-amber-100 text-amber-700',
        info: 'border-transparent bg-blue-100 text-blue-700',
      },
    },
    defaultVariants: { variant: 'default' },
  },
);

export type BadgeVariant = NonNullable<VariantProps<typeof badgeVariants>['variant']>;

@Directive({
  selector: '[appBadge]',
  host: { '[class]': 'computedClass()' },
})
export class BadgeDirective {
  readonly variant = input<BadgeVariant>('default');
  readonly userClass = input<ClassValue>('', { alias: 'class' });

  protected readonly computedClass = computed(() =>
    cn(badgeVariants({ variant: this.variant() }), this.userClass()),
  );
}
