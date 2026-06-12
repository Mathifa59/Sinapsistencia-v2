import { Component, computed, inject, output, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { injectMutation, injectQuery, injectQueryClient } from '@tanstack/angular-query-experimental';
import { LucideAngularModule } from 'lucide-angular';
import { AuthService } from '../../../core/auth/auth.service';
import { CasesApi } from '../../../core/api/cases.api';
import { DocumentsApi } from '../../../core/api/documents.api';
import { ModalComponent, ModalHeaderDirective, ModalTitleDirective, ModalDescriptionDirective, ModalFooterDirective } from '../../../shared/ui/modal.component';
import { BtnDirective } from '../../../shared/ui/button.directive';
import { InputDirective, LabelDirective, TextareaDirective, SelectDirective } from '../../../shared/ui/field.directives';
import { DOCUMENT_TYPE_LABELS, type DocumentType } from '../../../shared/constants';

const DOCUMENT_TYPES: DocumentType[] = [
  'historia_clinica', 'consentimiento_informado', 'informe_medico', 'receta',
  'orden_laboratorio', 'certificado_medico', 'documento_legal', 'otro',
];

/** Adaptación de DocumentFormModal.tsx: sin paciente, con consulta (caseId) opcional. */
@Component({
  selector: 'app-document-form-modal',
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
    <app-modal [open]="opened()" (close)="handleClose()">
      <div appModalHeader>
        <h2 appModalTitle>Nuevo documento</h2>
        <p appModalDescription>Crea un nuevo documento clínico o legal en el sistema.</p>
      </div>

      <form [formGroup]="form" (ngSubmit)="onSubmit()" class="space-y-4">
        <div class="space-y-1.5">
          <label appLabel for="doc-title">Título *</label>
          <input appInput id="doc-title" placeholder="Ej: Informe de consulta — Dr. García" formControlName="title" />
          @if (form.controls.title.invalid && form.controls.title.touched) {
            <p class="text-xs text-red-500">El título debe tener al menos 3 caracteres</p>
          }
        </div>

        <div class="grid grid-cols-2 gap-4">
          <div class="space-y-1.5">
            <label appLabel>Tipo de documento *</label>
            <select appSelect formControlName="type">
              @for (docType of documentTypes; track docType) {
                <option [value]="docType">{{ documentTypeLabels[docType] }}</option>
              }
            </select>
          </div>

          <div class="space-y-1.5">
            <label appLabel>Consulta asociada</label>
            <select appSelect formControlName="caseId">
              <option value="">Sin consulta</option>
              @for (c of casesQuery.data()?.data ?? []; track c.id) {
                <option [value]="c.id">{{ c.title }}</option>
              }
            </select>
          </div>
        </div>

        <div class="space-y-1.5">
          <label appLabel for="doc-content">Contenido inicial</label>
          <textarea appTextarea id="doc-content" rows="4" placeholder="Escribe el contenido del documento..." formControlName="initialContent"></textarea>
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
            Crear documento
          </button>
        </div>
      </form>
    </app-modal>
  `,
})
export class DocumentFormModalComponent {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly casesApi = inject(CasesApi);
  private readonly documentsApi = inject(DocumentsApi);
  private readonly queryClient = injectQueryClient();

  protected readonly opened = signal(false);
  readonly closed = output();

  protected readonly documentTypes = DOCUMENT_TYPES;
  protected readonly documentTypeLabels = DOCUMENT_TYPE_LABELS;
  protected readonly serverError = signal<string | null>(null);

  private readonly userId = computed(() => this.auth.user()?.id ?? '');

  protected readonly casesQuery = injectQuery(() => ({
    queryKey: ['cases', { doctorId: this.userId() }],
    queryFn: () => this.casesApi.list({ doctorId: this.userId(), pageSize: 100 }),
    enabled: !!this.userId(),
  }));

  protected readonly form = this.fb.nonNullable.group({
    title: ['', [Validators.required, Validators.minLength(3)]],
    type: ['informe_medico' as DocumentType, Validators.required],
    caseId: [''],
    initialContent: [''],
  });

  protected readonly createMutation = injectMutation(() => ({
    mutationFn: () => {
      const v = this.form.getRawValue();
      return this.documentsApi.create({
        title: v.title.trim(),
        type: v.type,
        caseId: v.caseId || undefined,
        initialContent: v.initialContent.trim() || undefined,
      });
    },
    onSuccess: () => {
      this.queryClient.invalidateQueries({ queryKey: ['documents'] });
      this.form.reset({ type: 'informe_medico' });
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
    this.form.reset({ type: 'informe_medico' });
    this.serverError.set(null);
    this.opened.set(false);
    this.closed.emit();
  }
}
