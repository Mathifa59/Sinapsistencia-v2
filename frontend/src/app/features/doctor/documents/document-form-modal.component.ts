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
import type { DocumentResponse } from '../../../core/api/generated/model/documentResponse';

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

        <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
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

        <div class="space-y-1.5">
          <label appLabel for="doc-file">Archivo adjunto <span class="text-slate-400 font-normal">(opcional · PDF, DOCX, PNG, JPG · máx 10 MB)</span></label>
          <div class="flex items-center gap-3">
            <label class="flex items-center gap-2 cursor-pointer rounded-md border border-slate-200 bg-white px-3 py-2 text-sm text-slate-700 hover:bg-slate-50 transition-colors">
              <lucide-icon name="paperclip" class="h-4 w-4 text-slate-400 shrink-0" />
              <span>{{ selectedFile() ? selectedFile()!.name : 'Seleccionar archivo' }}</span>
              <input id="doc-file" type="file" class="sr-only" accept=".pdf,.docx,image/png,image/jpeg" (change)="onFileChange($event)" />
            </label>
            @if (selectedFile()) {
              <button type="button" class="text-slate-400 hover:text-slate-600" (click)="clearFile()">
                <lucide-icon name="x" class="h-4 w-4" />
              </button>
            }
          </div>
        </div>

        @if (serverError()) {
          <p class="text-xs text-red-600 bg-red-50 border border-red-100 rounded-md px-3 py-2">{{ serverError() }}</p>
        }

        <div appModalFooter>
          <button appBtn type="button" variant="outline" (click)="handleClose()" [disabled]="isBusy()">
            Cancelar
          </button>
          <button appBtn type="submit" variant="primary" class="gap-2" [disabled]="isBusy()">
            @if (isBusy()) {
              <lucide-icon name="loader-2" class="h-4 w-4 animate-spin" />
              {{ uploadMutation.isPending() ? 'Subiendo archivo…' : 'Creando…' }}
            } @else {
              Crear documento
            }
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
  protected readonly selectedFile = signal<File | null>(null);

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

  protected readonly uploadMutation = injectMutation(() => ({
    mutationFn: ({ id, file }: { id: string; file: File }) =>
      this.documentsApi.uploadFile(id, file),
    onSuccess: () => this.finishAndClose(),
    onError: (err: Error) => this.serverError.set(`Documento creado, pero falló la subida del archivo: ${err.message}`),
  }));

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
    onSuccess: (doc: DocumentResponse) => {
      const file = this.selectedFile();
      if (file && doc.id) {
        this.uploadMutation.mutate({ id: doc.id, file });
      } else {
        this.finishAndClose();
      }
    },
    onError: (err: Error) => this.serverError.set(err.message),
  }));

  protected readonly isBusy = computed(
    () => this.createMutation.isPending() || this.uploadMutation.isPending(),
  );

  show(): void {
    this.serverError.set(null);
    this.opened.set(true);
  }

  protected onFileChange(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.selectedFile.set(input.files?.[0] ?? null);
  }

  protected clearFile(): void {
    this.selectedFile.set(null);
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
    if (this.isBusy()) return;
    this.form.reset({ type: 'informe_medico' });
    this.serverError.set(null);
    this.selectedFile.set(null);
    this.opened.set(false);
    this.closed.emit();
  }

  private finishAndClose(): void {
    this.queryClient.invalidateQueries({ queryKey: ['documents'] });
    this.form.reset({ type: 'informe_medico' });
    this.selectedFile.set(null);
    this.opened.set(false);
    this.closed.emit();
  }
}
