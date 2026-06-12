import { Component, computed, input, signal } from '@angular/core';
import type { ClassValue } from 'clsx';
import { cn } from '../utils/cn';

/**
 * Avatar con fallback de iniciales — clases portadas verbatim de
 * components/ui/avatar.tsx (Root/Image/Fallback de Radix).
 */
@Component({
  selector: 'app-avatar',
  template: `
    @if (src() && !imageFailed()) {
      <img
        [src]="src()"
        [alt]="alt()"
        class="aspect-square h-full w-full"
        (error)="imageFailed.set(true)"
      />
    } @else {
      <span
        [class]="
          cn(
            'flex h-full w-full items-center justify-center rounded-full bg-slate-100 text-slate-600 text-sm font-medium',
            fallbackClass()
          )
        "
        >{{ fallback() }}</span
      >
    }
  `,
  host: { '[class]': 'computedClass()' },
})
export class AvatarComponent {
  readonly src = input<string | null>(null);
  readonly alt = input('');
  readonly fallback = input('');
  readonly fallbackClass = input<ClassValue>('');
  readonly userClass = input<ClassValue>('', { alias: 'class' });

  protected readonly imageFailed = signal(false);
  protected readonly cn = cn;

  protected readonly computedClass = computed(() =>
    cn('relative flex h-10 w-10 shrink-0 overflow-hidden rounded-full', this.userClass()),
  );
}
