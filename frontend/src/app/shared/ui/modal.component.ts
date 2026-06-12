import { Component, Directive, computed, input, output } from '@angular/core';
import { LucideAngularModule } from 'lucide-angular';
import type { ClassValue } from 'clsx';
import { cn } from '../utils/cn';

/** Modal simple (overlay + panel), equivalente visual de components/ui/dialog.tsx. */
@Component({
  selector: 'app-modal',
  imports: [LucideAngularModule],
  template: `
    @if (open()) {
      <div class="fixed inset-0 z-50 bg-black/50 backdrop-blur-sm" (click)="close.emit()"></div>
      <div
        class="fixed left-1/2 top-1/2 z-50 w-full max-w-lg -translate-x-1/2 -translate-y-1/2 bg-white rounded-xl border border-slate-200 shadow-xl max-h-[90vh] overflow-y-auto p-6"
        [class]="userClass()"
      >
        <ng-content />
        <button
          type="button"
          (click)="close.emit()"
          class="absolute right-4 top-4 rounded-md p-1 text-slate-400 hover:text-slate-700 hover:bg-slate-100 transition-colors focus:outline-none focus:ring-2 focus:ring-slate-300"
        >
          <lucide-icon name="x" class="h-4 w-4" />
          <span class="sr-only">Cerrar</span>
        </button>
      </div>
    }
  `,
})
export class ModalComponent {
  readonly open = input(false);
  readonly userClass = input<ClassValue>('', { alias: 'class' });
  readonly close = output<void>();
}

@Directive({ selector: '[appModalHeader]', host: { '[class]': 'computedClass()' } })
export class ModalHeaderDirective {
  readonly userClass = input<ClassValue>('', { alias: 'class' });
  protected readonly computedClass = computed(() => cn('mb-5', this.userClass()));
}

@Directive({ selector: '[appModalTitle]', host: { '[class]': 'computedClass()' } })
export class ModalTitleDirective {
  readonly userClass = input<ClassValue>('', { alias: 'class' });
  protected readonly computedClass = computed(() => cn('text-lg font-bold text-slate-900', this.userClass()));
}

@Directive({ selector: '[appModalDescription]', host: { '[class]': 'computedClass()' } })
export class ModalDescriptionDirective {
  readonly userClass = input<ClassValue>('', { alias: 'class' });
  protected readonly computedClass = computed(() => cn('text-sm text-slate-500 mt-1', this.userClass()));
}

@Directive({ selector: '[appModalFooter]', host: { '[class]': 'computedClass()' } })
export class ModalFooterDirective {
  readonly userClass = input<ClassValue>('', { alias: 'class' });
  protected readonly computedClass = computed(() =>
    cn('flex justify-end gap-2 mt-6 pt-4 border-t border-slate-100', this.userClass()),
  );
}
