import { Component, computed, inject, input, output, signal } from '@angular/core';
import { injectMutation, injectQueryClient } from '@tanstack/angular-query-experimental';
import { LucideAngularModule } from 'lucide-angular';
import { DocumentsApi } from '../../../core/api/documents.api';
import { ModalComponent, ModalHeaderDirective, ModalTitleDirective, ModalDescriptionDirective, ModalFooterDirective } from '../../../shared/ui/modal.component';
import { BtnDirective } from '../../../shared/ui/button.directive';
import { BadgeDirective } from '../../../shared/ui/badge.directive';
import { DocumentStatusBadgeComponent } from '../../../shared/ui/status-badges.component';
import { formatDate, formatDateTime } from '../../../shared/utils/cn';
import { DOCUMENT_TYPE_LABELS, type DocumentStatus, type DocumentType } from '../../../shared/constants';
import type { DocumentResponse } from '../../../core/api/generated/model/documentResponse';

/** Adaptación de DocumentDetailModal.tsx: sin paciente, firmas por signerId. */
@Component({
  selector: 'app-document-detail-modal',
  imports: [LucideAngularModule, ModalComponent, ModalHeaderDirective, ModalTitleDirective, ModalDescriptionDirective, ModalFooterDirective, BtnDirective, BadgeDirective, DocumentStatusBadgeComponent],
  template: `
    <app-modal [open]="!!document()" (close)="close.emit()" class="max-w-lg">
      @let doc = document();
      @if (doc) {
        <div appModalHeader>
          <div class="flex items-start gap-3">
            <div class="h-9 w-9 rounded-lg bg-slate-100 flex items-center justify-center shrink-0">
              <lucide-icon name="file-text" class="h-5 w-5 text-slate-500" />
            </div>
            <div class="min-w-0">
              <h2 appModalTitle class="text-left leading-snug">{{ doc.title }}</h2>
              <p appModalDescription class="text-left mt-0.5">{{ documentTypeLabels[asType(doc.type)] }}</p>
            </div>
          </div>
        </div>

        <div class="space-y-4">
          <div class="flex flex-wrap items-center gap-3 text-sm">
            <app-document-status-badge [status]="asStatus(doc.status)" />
            <span class="text-slate-400">·</span>
            <span class="text-slate-500">
              {{ (doc.versions?.length ?? 0) }} versión{{ (doc.versions?.length ?? 0) !== 1 ? 'es' : '' }}
            </span>
            <span class="text-slate-400">·</span>
            <span class="text-slate-500 flex items-center gap-1">
              <lucide-icon name="check-circle" class="h-3.5 w-3.5 text-emerald-500" />
              {{ validSignatureCount() }} firma{{ validSignatureCount() !== 1 ? 's' : '' }}
            </span>
          </div>

          @if (currentVersion()) {
            <div class="border border-slate-200 rounded-md p-4 bg-white">
              <div class="flex items-center justify-between mb-2">
                <p class="text-xs font-semibold text-slate-500 uppercase tracking-wider">Versión {{ currentVersion()!.version }}</p>
                <p class="text-xs text-slate-400">{{ formatDate(currentVersion()!.createdAt ?? '') }}</p>
              </div>
              <p class="text-sm text-slate-700 leading-relaxed whitespace-pre-line line-clamp-6">
                @if (currentVersion()!.content) {
                  {{ currentVersion()!.content }}
                } @else {
                  <span class="italic text-slate-400">Sin contenido</span>
                }
              </p>
            </div>
          }

          @if ((doc.signatures?.length ?? 0) > 0) {
            <div>
              <p class="text-xs font-semibold text-slate-500 uppercase tracking-wider mb-2">Firmas</p>
              <div class="space-y-2">
                @for (signature of doc.signatures; track signature.id) {
                  <div class="flex items-center justify-between text-sm bg-slate-50 rounded-md px-3 py-2">
                    <span class="text-slate-700">Firmante {{ (signature.signerId ?? '').slice(0, 8) }}</span>
                    <div class="flex items-center gap-2">
                      <span appBadge [variant]="signature.isValid ? 'success' : 'destructive'" class="text-xs">
                        {{ signature.isValid ? 'Válida' : 'Inválida' }}
                      </span>
                      <span class="text-xs text-slate-400">{{ formatDateTime(signature.signedAt ?? '') }}</span>
                    </div>
                  </div>
                }
              </div>
            </div>
          }

          @if (actionError()) {
            <p class="text-xs text-red-500">{{ actionError() }}</p>
          }
        </div>

        <div appModalFooter class="flex-col sm:flex-row gap-2">
          @if (doc.status === 'borrador') {
            <button appBtn variant="primary" size="sm" class="gap-2 flex-1" (click)="setStatus('pendiente_firma')" [disabled]="updateMutation.isPending()">
              <lucide-icon [name]="updateMutation.isPending() ? 'loader-2' : 'pen'" [class]="updateMutation.isPending() ? 'h-4 w-4 animate-spin' : 'h-4 w-4'" />
              Enviar a firma
            </button>
          }
          @if (doc.status === 'pendiente_firma') {
            <button appBtn variant="primary" size="sm" class="gap-2 flex-1" (click)="setStatus('firmado')" [disabled]="updateMutation.isPending()">
              <lucide-icon [name]="updateMutation.isPending() ? 'loader-2' : 'check-circle'" [class]="updateMutation.isPending() ? 'h-4 w-4 animate-spin' : 'h-4 w-4'" />
              Marcar como firmado
            </button>
          }
          @if (doc.status === 'firmado' || doc.status === 'borrador') {
            <button appBtn variant="outline" size="sm" class="gap-2" (click)="setStatus('archivado')" [disabled]="updateMutation.isPending()">
              <lucide-icon name="archive" class="h-4 w-4" />
              Archivar
            </button>
          }
          <button appBtn variant="outline" size="sm" (click)="close.emit()" [disabled]="updateMutation.isPending()">Cerrar</button>
        </div>
      }
    </app-modal>
  `,
})
export class DocumentDetailModalComponent {
  private readonly documentsApi = inject(DocumentsApi);
  private readonly queryClient = injectQueryClient();

  readonly document = input<DocumentResponse | null>(null);
  readonly close = output<void>();

  protected readonly documentTypeLabels = DOCUMENT_TYPE_LABELS;
  protected readonly formatDate = formatDate;
  protected readonly formatDateTime = formatDateTime;
  protected readonly actionError = signal<string | null>(null);

  protected readonly asStatus = (s?: string) => (s ?? 'borrador') as DocumentStatus;
  protected readonly asType = (t?: string) => (t ?? 'otro') as DocumentType;

  protected readonly currentVersion = computed(() => {
    const doc = this.document();
    if (!doc?.versions?.length) return null;
    return doc.versions.find((v) => v.id === doc.currentVersionId) ?? doc.versions[doc.versions.length - 1];
  });

  protected readonly validSignatureCount = computed(
    () => (this.document()?.signatures ?? []).filter((s) => s.isValid).length,
  );

  protected readonly updateMutation = injectMutation(() => ({
    mutationFn: (status: DocumentStatus) => this.documentsApi.update(this.document()!.id!, { status }),
    onSuccess: () => {
      this.queryClient.invalidateQueries({ queryKey: ['documents'] });
      this.close.emit();
    },
    onError: () => this.actionError.set('No se pudo actualizar el estado. Intenta de nuevo.'),
  }));

  protected setStatus(status: DocumentStatus): void {
    this.actionError.set(null);
    this.updateMutation.mutate(status);
  }
}
