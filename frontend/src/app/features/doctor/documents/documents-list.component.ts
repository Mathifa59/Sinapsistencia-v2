import { Component, computed, inject, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { LucideAngularModule } from 'lucide-angular';
import { DocumentsApi } from '../../../core/api/documents.api';
import { BtnDirective } from '../../../shared/ui/button.directive';
import { InputDirective } from '../../../shared/ui/field.directives';
import { DocumentStatusBadgeComponent } from '../../../shared/ui/status-badges.component';
import { formatDate } from '../../../shared/utils/cn';
import { DOCUMENT_TYPE_LABELS, type DocumentStatus, type DocumentType } from '../../../shared/constants';
import type { DocumentResponse } from '../../../core/api/generated/model/documentResponse';
import { DocumentFormModalComponent } from './document-form-modal.component';
import { DocumentDetailModalComponent } from './document-detail-modal.component';

/** Réplica de app/doctor/documents/page.tsx, sin columna de Paciente. */
@Component({
  selector: 'app-doctor-documents',
  imports: [
    FormsModule,
    LucideAngularModule,
    BtnDirective,
    InputDirective,
    DocumentStatusBadgeComponent,
    DocumentFormModalComponent,
    DocumentDetailModalComponent,
  ],
  template: `
    <div class="space-y-5">
      <div class="flex items-center justify-between">
        <div>
          <h1 class="text-2xl font-bold text-slate-900">Documentos</h1>
          <p class="text-slate-500 text-sm mt-1">{{ documentsQuery.data()?.total ?? 0 }} documentos registrados</p>
        </div>
        <button appBtn variant="primary" size="sm" class="gap-2" (click)="formModal.show()">
          <lucide-icon name="plus" class="h-4 w-4" />Nuevo documento
        </button>
      </div>

      <div class="relative">
        <lucide-icon name="search" class="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-slate-400" />
        <input appInput placeholder="Buscar por título..." class="pl-9" [(ngModel)]="search" />
      </div>

      <div class="grid gap-3">
        @if (documentsQuery.isLoading()) {
          <p class="text-center py-10 text-slate-400">Cargando...</p>
        }
        @for (doc of filteredDocs(); track doc.id) {
          <button
            type="button"
            class="bg-white rounded-lg border border-slate-200 p-5 flex items-start gap-4 hover:border-slate-300 transition-colors text-left w-full"
            (click)="selected.set(doc)"
          >
            <div class="h-10 w-10 rounded-lg bg-slate-100 flex items-center justify-center shrink-0">
              <lucide-icon name="file-text" class="h-5 w-5 text-slate-500" />
            </div>
            <div class="flex-1 min-w-0">
              <div class="flex items-start justify-between gap-3">
                <div class="min-w-0">
                  <p class="font-semibold text-slate-800 truncate">{{ doc.title }}</p>
                  <p class="text-xs text-slate-500 mt-0.5">{{ documentTypeLabels[asType(doc.type)] }}</p>
                </div>
                <app-document-status-badge [status]="asStatus(doc.status)" />
              </div>
              <div class="flex items-center gap-4 mt-3 text-xs text-slate-400">
                <span>Versiones: <span class="text-slate-600">{{ doc.versions?.length ?? 0 }}</span></span>
                <span>Firmas: <span class="text-slate-600">{{ doc.signatures?.length ?? 0 }}</span></span>
                <span>Actualizado: <span class="text-slate-600">{{ formatDate(doc.updatedAt ?? '') }}</span></span>
              </div>
            </div>
          </button>
        }
        @if (!documentsQuery.isLoading() && filteredDocs().length === 0) {
          <div class="text-center py-16 text-slate-400">
            <lucide-icon name="file-text" class="h-10 w-10 mx-auto mb-3 opacity-40" />
            <p>No se encontraron documentos</p>
          </div>
        }
      </div>
    </div>

    <app-document-form-modal #formModal />
    <app-document-detail-modal [document]="selected()" (close)="selected.set(null)" />
  `,
})
export class DoctorDocumentsComponent {
  private readonly documentsApi = inject(DocumentsApi);

  protected readonly formModal = viewChild.required(DocumentFormModalComponent);
  protected readonly search = signal('');
  protected readonly selected = signal<DocumentResponse | null>(null);
  protected readonly formatDate = formatDate;
  protected readonly documentTypeLabels = DOCUMENT_TYPE_LABELS;
  protected readonly asStatus = (s?: string) => (s ?? 'borrador') as DocumentStatus;
  protected readonly asType = (t?: string) => (t ?? 'otro') as DocumentType;

  protected readonly documentsQuery = injectQuery(() => ({
    queryKey: ['documents', { pageSize: 100 }],
    queryFn: () => this.documentsApi.list({ pageSize: 100 }),
  }));

  protected readonly filteredDocs = computed(() => {
    const term = this.search().trim().toLowerCase();
    const docs = this.documentsQuery.data()?.data ?? [];
    if (!term) return docs;
    return docs.filter((d) => (d.title ?? '').toLowerCase().includes(term));
  });
}
