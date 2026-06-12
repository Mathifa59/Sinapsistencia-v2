import { Component, computed, inject, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { LucideAngularModule } from 'lucide-angular';
import { DocumentsApi } from '../../../core/api/documents.api';
import { BtnDirective } from '../../../shared/ui/button.directive';
import { InputDirective } from '../../../shared/ui/field.directives';
import { DocumentStatusBadgeComponent } from '../../../shared/ui/status-badges.component';
import { DocumentDetailModalComponent } from '../../doctor/documents/document-detail-modal.component';
import { AdminDocumentFormModalComponent } from './document-form-modal.component';
import { formatDateTime, getInitials } from '../../../shared/utils/cn';
import { DOCUMENT_TYPE_LABELS, type DocumentStatus, type DocumentType } from '../../../shared/constants';
import type { DocumentResponse } from '../../../core/api/generated/model/documentResponse';

/** Réplica de app/admin/documents/page.tsx, sin columna Paciente (modelo académico). */
@Component({
  selector: 'app-admin-documents',
  imports: [FormsModule, LucideAngularModule, BtnDirective, InputDirective, DocumentStatusBadgeComponent, DocumentDetailModalComponent, AdminDocumentFormModalComponent],
  template: `
    <div class="space-y-5">
      <div class="flex items-center justify-between">
        <div>
          <h1 class="text-2xl font-bold text-slate-900">Documentos</h1>
          <p class="text-slate-500 text-sm mt-1">{{ documentsQuery.data()?.total ?? 0 }} documentos en el sistema</p>
        </div>
        <button appBtn variant="primary" size="sm" class="gap-2" (click)="formModal.show()">
          <lucide-icon name="plus" class="h-4 w-4" />Nuevo documento
        </button>
      </div>

      <div class="relative">
        <lucide-icon name="search" class="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-slate-400" />
        <input appInput placeholder="Buscar por título..." class="pl-9" [(ngModel)]="search" />
      </div>

      @if (documentsQuery.isLoading()) {
        <div class="flex items-center justify-center py-16 text-slate-400">
          <lucide-icon name="loader-2" class="h-5 w-5 animate-spin mr-2" />
          <span>Cargando documentos...</span>
        </div>
      } @else {
        <div class="bg-white rounded-lg border border-slate-200 overflow-hidden">
          <table class="w-full text-sm">
            <thead>
              <tr class="border-b border-slate-100 bg-slate-50">
                <th class="text-left px-5 py-3 font-semibold text-slate-600">Documento</th>
                <th class="text-left px-5 py-3 font-semibold text-slate-600 hidden md:table-cell">Tipo</th>
                <th class="text-left px-5 py-3 font-semibold text-slate-600 hidden sm:table-cell">Autor</th>
                <th class="text-left px-5 py-3 font-semibold text-slate-600">Estado</th>
                <th class="text-left px-5 py-3 font-semibold text-slate-600 hidden lg:table-cell">Firmas</th>
                <th class="text-left px-5 py-3 font-semibold text-slate-600 hidden lg:table-cell">Actualizado</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-slate-50">
              @for (doc of filteredDocuments(); track doc.id) {
                <tr class="hover:bg-slate-50 transition-colors cursor-pointer" (click)="openDetail(doc)">
                  <td class="px-5 py-4">
                    <div class="flex items-center gap-3">
                      <div class="h-8 w-8 rounded-lg bg-blue-50 flex items-center justify-center shrink-0">
                        <lucide-icon name="file-text" class="h-4 w-4 text-blue-600" />
                      </div>
                      <span class="font-medium text-slate-800">{{ doc.title }}</span>
                    </div>
                  </td>
                  <td class="px-5 py-4 text-slate-500 hidden md:table-cell">{{ documentTypeLabels[asType(doc.type)] }}</td>
                  <td class="px-5 py-4 text-slate-500 hidden sm:table-cell">
                    <div class="flex items-center gap-2">
                      <div class="h-6 w-6 rounded-full bg-slate-200 flex items-center justify-center text-[10px] font-bold text-slate-600 shrink-0">
                        {{ getInitials(doc.authorName ?? '') }}
                      </div>
                      {{ doc.authorName }}
                    </div>
                  </td>
                  <td class="px-5 py-4">
                    <app-document-status-badge [status]="asStatus(doc.status)" />
                  </td>
                  <td class="px-5 py-4 text-slate-400 hidden lg:table-cell">{{ doc.signatures?.length ?? 0 }}</td>
                  <td class="px-5 py-4 text-slate-400 hidden lg:table-cell">{{ formatDateTime(doc.updatedAt ?? '') }}</td>
                </tr>
              }
              @if (filteredDocuments().length === 0) {
                <tr>
                  <td colspan="6" class="px-5 py-10 text-center text-slate-400">No se encontraron documentos</td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      }
    </div>

    <app-admin-document-form-modal #formModal />
    <app-document-detail-modal [document]="selected()" (close)="selected.set(null)" />
  `,
})
export class AdminDocumentsComponent {
  private readonly documentsApi = inject(DocumentsApi);

  protected readonly formModal = viewChild.required(AdminDocumentFormModalComponent);
  protected readonly search = signal('');
  protected readonly selected = signal<DocumentResponse | null>(null);
  protected readonly formatDateTime = formatDateTime;
  protected readonly getInitials = getInitials;
  protected readonly documentTypeLabels = DOCUMENT_TYPE_LABELS;
  protected readonly asType = (t?: string) => (t ?? 'otro') as DocumentType;
  protected readonly asStatus = (s?: string) => (s ?? 'borrador') as DocumentStatus;

  protected readonly documentsQuery = injectQuery(() => ({
    queryKey: ['documents', { pageSize: 100 }],
    queryFn: () => this.documentsApi.list({ pageSize: 100 }),
  }));

  protected readonly filteredDocuments = computed(() => {
    const term = this.search().trim().toLowerCase();
    const docs = this.documentsQuery.data()?.data ?? [];
    if (!term) return docs;
    return docs.filter((d) => (d.title ?? '').toLowerCase().includes(term));
  });

  protected openDetail(doc: DocumentResponse): void {
    this.selected.set(doc);
  }
}
