import { Component, inject, output, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { injectMutation, injectQueryClient } from '@tanstack/angular-query-experimental';
import { LucideAngularModule } from 'lucide-angular';
import { CasesApi } from '../../../core/api/cases.api';
import { ModalComponent, ModalHeaderDirective, ModalTitleDirective, ModalDescriptionDirective, ModalFooterDirective } from '../../../shared/ui/modal.component';
import { BtnDirective } from '../../../shared/ui/button.directive';
import { InputDirective, LabelDirective, TextareaDirective, SelectDirective } from '../../../shared/ui/field.directives';
import { MEDICAL_SPECIALTIES } from '../../../shared/constants';

/** Adaptación de CaseFormModal.tsx: sin paciente, con CaseContextDto (datos simulados, Ley 29733). */
@Component({
  selector: 'app-case-form-modal',
  imports: [
    ReactiveFormsModule,
    LucideAngularModule,
    ModalComponent,
    ModalHeaderDirective,
    ModalTitleDirective,
    ModalDescriptionDirective,
    ModalFooterDirective,
    BtnDirective,
    InputDirective,
    LabelDirective,
    TextareaDirective,
    SelectDirective,
  ],
  template: `
    <app-modal [open]="opened()" (close)="handleClose()" class="max-w-lg">
      <div appModalHeader>
        <h2 appModalTitle>Nueva consulta</h2>
        <p appModalDescription>Registra una nueva consulta clínico-legal para seguimiento y gestión.</p>
      </div>

      <form [formGroup]="form" (ngSubmit)="onSubmit()" class="space-y-4">
        <div class="space-y-1.5">
          <label appLabel for="case-title">Título de la consulta *</label>
          <input appInput id="case-title" placeholder="Ej: Revisión de consentimiento informado — Cirugía" formControlName="title" />
          @if (form.controls.title.invalid && form.controls.title.touched) {
            <p class="text-xs text-red-500">El título debe tener al menos 3 caracteres</p>
          }
        </div>

        <div class="space-y-1.5">
          <label appLabel for="case-description">Descripción *</label>
          <textarea appTextarea id="case-description" rows="3" placeholder="Describe el contexto clínico y los aspectos legales relevantes..." formControlName="description"></textarea>
          @if (form.controls.description.invalid && form.controls.description.touched) {
            <p class="text-xs text-red-500">La descripción debe tener al menos 10 caracteres</p>
          }
        </div>

        <div class="grid grid-cols-2 gap-4">
          <div class="space-y-1.5">
            <label appLabel>Prioridad *</label>
            <select appSelect formControlName="priority">
              <option value="baja">Baja</option>
              <option value="media">Media</option>
              <option value="alta">Alta</option>
              <option value="critica">Crítica</option>
            </select>
          </div>

          <div class="space-y-1.5">
            <label appLabel>Especialidad médica</label>
            <select appSelect formControlName="medicalSpecialty">
              <option value="">Sin especificar</option>
              @for (s of specialties; track s) {
                <option [value]="s">{{ s }}</option>
              }
            </select>
          </div>
        </div>

        <div class="grid grid-cols-2 gap-4">
          <div class="space-y-1.5">
            <label appLabel for="case-event-type">Tipo de evento</label>
            <input appInput id="case-event-type" placeholder="Ej: Cirugía, consulta, diagnóstico" formControlName="eventType" />
          </div>

          <div class="space-y-1.5">
            <label appLabel>Urgencia percibida</label>
            <select appSelect formControlName="perceivedUrgency">
              <option value="">Sin especificar</option>
              <option value="baja">Baja</option>
              <option value="media">Media</option>
              <option value="alta">Alta</option>
              <option value="critica">Crítica</option>
            </select>
          </div>
        </div>

        <div class="space-y-1.5">
          <label appLabel for="case-notes">Notas adicionales</label>
          <textarea appTextarea id="case-notes" rows="2" placeholder="Observaciones o información adicional relevante..." formControlName="notes"></textarea>
        </div>

        <div class="border-t border-slate-100 pt-4 space-y-3">
          <p class="text-xs font-semibold text-slate-500 uppercase tracking-wider">
            Contexto simulado (sin datos identificables — Ley 29733)
          </p>

          <div class="grid grid-cols-2 gap-4">
            <div class="space-y-1.5">
              <label appLabel for="context-code">Código de referencia</label>
              <input appInput id="context-code" placeholder="Auto (Caso-XXXXXXXX)" formControlName="contextCode" />
            </div>
            <div class="space-y-1.5">
              <label appLabel for="context-age">Edad de referencia</label>
              <input appInput id="context-age" type="number" min="0" placeholder="Ej: 45" formControlName="ageReference" />
            </div>
          </div>

          <div class="grid grid-cols-2 gap-4">
            <div class="space-y-1.5">
              <label appLabel for="context-area">Área médica</label>
              <input appInput id="context-area" placeholder="Ej: Cirugía General" formControlName="medicalArea" />
            </div>
            <div class="space-y-1.5">
              <label appLabel for="context-date">Fecha del evento</label>
              <input appInput id="context-date" type="date" formControlName="eventDate" />
            </div>
          </div>

          <div class="space-y-1.5">
            <label appLabel for="context-summary">Resumen del contexto</label>
            <textarea appTextarea id="context-summary" rows="2" placeholder="Resumen anonimizado del caso..." formControlName="summary"></textarea>
          </div>

          <div class="space-y-1.5">
            <label appLabel for="context-factors">Factores relevantes</label>
            <input appInput id="context-factors" placeholder="Separados por coma: ej. cirugía electiva, consentimiento firmado" formControlName="relevantFactors" />
          </div>
        </div>

        @if (serverError()) {
          <p class="text-xs text-red-600 bg-red-50 border border-red-100 rounded-md px-3 py-2">{{ serverError() }}</p>
        }

        <div appModalFooter>
          <button appBtn type="button" variant="outline" (click)="handleClose()" [disabled]="createMutation.isPending()">
            Cancelar
          </button>
          <button appBtn type="submit" variant="primary" class="gap-2" [disabled]="createMutation.isPending()">
            @if (createMutation.isPending()) {
              <lucide-icon name="loader-2" class="h-4 w-4 animate-spin" />
            }
            Crear consulta
          </button>
        </div>
      </form>
    </app-modal>
  `,
})
export class CaseFormModalComponent {
  private readonly fb = inject(FormBuilder);
  private readonly casesApi = inject(CasesApi);
  private readonly queryClient = injectQueryClient();

  protected readonly opened = signal(false);
  readonly closed = output<void>();

  protected readonly specialties = MEDICAL_SPECIALTIES;
  protected readonly serverError = signal<string | null>(null);

  protected readonly form = this.fb.nonNullable.group({
    title: ['', [Validators.required, Validators.minLength(3)]],
    description: ['', [Validators.required, Validators.minLength(10)]],
    priority: ['media', Validators.required],
    medicalSpecialty: [''],
    eventType: [''],
    perceivedUrgency: [''],
    notes: [''],
    contextCode: [''],
    ageReference: [''],
    medicalArea: [''],
    eventDate: [''],
    summary: [''],
    relevantFactors: [''],
  });

  protected readonly createMutation = injectMutation(() => ({
    mutationFn: () => {
      const v = this.form.getRawValue();
      const context = v.medicalArea.trim()
        ? {
            contextCode: v.contextCode.trim() || undefined,
            ageReference: v.ageReference ? Number(v.ageReference) : undefined,
            medicalArea: v.medicalArea.trim(),
            eventDate: v.eventDate || undefined,
            summary: v.summary.trim() || undefined,
            relevantFactors: v.relevantFactors.trim()
              ? v.relevantFactors.split(',').map((f) => f.trim()).filter(Boolean)
              : undefined,
          }
        : undefined;

      return this.casesApi.create({
        title: v.title.trim(),
        description: v.description.trim(),
        priority: v.priority,
        medicalSpecialty: v.medicalSpecialty || undefined,
        eventType: v.eventType.trim() || undefined,
        perceivedUrgency: v.perceivedUrgency || undefined,
        notes: v.notes.trim() || undefined,
        context,
      });
    },
    onSuccess: () => {
      this.queryClient.invalidateQueries({ queryKey: ['cases'] });
      this.form.reset({ priority: 'media' });
      this.opened.set(false);
      this.closed.emit();
    },
    onError: (err: Error) => this.serverError.set(err.message),
  }));

  show(): void {
    this.serverError.set(null);
    this.opened.set(true);
  }

  protected onSubmit(): void {
    this.serverError.set(null);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.createMutation.mutate();
  }

  protected handleClose(): void {
    this.form.reset({ priority: 'media' });
    this.serverError.set(null);
    this.opened.set(false);
    this.closed.emit();
  }
}
