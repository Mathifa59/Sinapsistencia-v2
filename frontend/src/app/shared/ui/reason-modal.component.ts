import { Component, input, output, signal } from '@angular/core';
import {
  ModalComponent,
  ModalHeaderDirective,
  ModalTitleDirective,
  ModalDescriptionDirective,
  ModalFooterDirective,
} from './modal.component';
import { BtnDirective } from './button.directive';
import { LabelDirective, TextareaDirective } from './field.directives';

/**
 * Modal reutilizable para acciones que exigen un motivo (cerrar caso, rechazar
 * solicitud). Reemplaza los window.prompt nativos: se abre con show() y emite
 * `confirmed` con el motivo ya validado (no vacío).
 */
@Component({
  selector: 'app-reason-modal',
  imports: [
    ModalComponent,
    ModalHeaderDirective,
    ModalTitleDirective,
    ModalDescriptionDirective,
    ModalFooterDirective,
    BtnDirective,
    LabelDirective,
    TextareaDirective,
  ],
  template: `
    <app-modal [open]="opened()" (close)="cancel()" class="max-w-md">
      <div appModalHeader>
        <h2 appModalTitle>{{ title() }}</h2>
        @if (description()) {
          <p appModalDescription>{{ description() }}</p>
        }
      </div>

      <div class="space-y-1.5">
        <label appLabel>{{ label() }}</label>
        <textarea
          appTextarea
          rows="3"
          [placeholder]="placeholder()"
          [value]="reason()"
          (input)="reason.set($any($event.target).value)"
        ></textarea>
        @if (touched() && !reason().trim()) {
          <p class="text-xs text-red-600">El motivo es requerido</p>
        }
      </div>

      <div appModalFooter>
        <button appBtn type="button" variant="outline" (click)="cancel()">Cancelar</button>
        <button appBtn type="button" variant="primary" (click)="submit()">{{ confirmLabel() }}</button>
      </div>
    </app-modal>
  `,
})
export class ReasonModalComponent {
  readonly title = input('Confirmar acción');
  readonly description = input('');
  readonly label = input('Motivo *');
  readonly placeholder = input('Escribe el motivo...');
  readonly confirmLabel = input('Confirmar');

  /** Se emite con el motivo (trim, no vacío) al confirmar. */
  readonly confirmed = output<string>();

  protected readonly opened = signal(false);
  protected readonly reason = signal('');
  protected readonly touched = signal(false);

  show(): void {
    this.reason.set('');
    this.touched.set(false);
    this.opened.set(true);
  }

  protected cancel(): void {
    this.opened.set(false);
  }

  protected submit(): void {
    this.touched.set(true);
    const value = this.reason().trim();
    if (!value) {
      return;
    }
    this.opened.set(false);
    this.confirmed.emit(value);
  }
}
