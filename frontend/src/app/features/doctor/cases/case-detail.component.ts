import { Component, computed, inject, Injector } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { LucideAngularModule } from 'lucide-angular';
import { map } from 'rxjs';
import { CasesApi } from '../../../core/api/cases.api';
import { DocumentsApi } from '../../../core/api/documents.api';
import { BtnDirective } from '../../../shared/ui/button.directive';
import { CaseStatusBadgeComponent, CasePriorityBadgeComponent } from '../../../shared/ui/status-badges.component';
import { formatDate, formatDateTime } from '../../../shared/utils/cn';
import type { CasePriority, CaseStatus } from '../../../shared/constants';

/** Réplica de app/doctor/cases/[id]/page.tsx: sin panel Paciente, con Contexto simulado (Ley 29733). */
@Component({
  selector: 'app-doctor-case-detail',
  imports: [RouterLink, LucideAngularModule, BtnDirective, CaseStatusBadgeComponent, CasePriorityBadgeComponent],
  template: `
    @if (caseQuery.isLoading()) {
      <div class="flex items-center justify-center py-20 text-slate-400">
        <lucide-icon name="loader-2" class="h-6 w-6 animate-spin mr-2" />
        <span>Cargando consulta...</span>
      </div>
    } @else if (caseQuery.isError() || !caseQuery.data()) {
      <div class="flex flex-col items-center justify-center py-20 text-slate-400">
        <lucide-icon name="alert-triangle" class="h-10 w-10 mb-3" />
        <p class="font-medium">Consulta no encontrada</p>
        <a routerLink="/doctor/cases" class="text-blue-600 text-sm mt-2 hover:underline">Volver a consultas</a>
      </div>
    } @else {
      @let c = caseQuery.data()!;
      <div class="space-y-5 max-w-4xl">
        <div class="flex items-center gap-3">
          <a routerLink="/doctor/cases">
            <button appBtn variant="outline" size="icon" class="h-8 w-8">
              <lucide-icon name="arrow-left" class="h-4 w-4" />
            </button>
          </a>
          <div class="flex-1 min-w-0">
            <h1 class="text-xl font-bold text-slate-900 truncate">{{ c.title }}</h1>
            <p class="text-sm text-slate-500">{{ c.context?.contextCode ?? ('Consulta #' + (c.id ?? '').slice(0, 8).toUpperCase()) }}</p>
          </div>
          <div class="flex gap-2">
            <app-case-status-badge [status]="asStatus(c.status)" />
            <app-case-priority-badge [priority]="asPriority(c.priority)" />
          </div>
        </div>

        <div class="grid lg:grid-cols-3 gap-5">
          <div class="lg:col-span-2 space-y-4">
            <div class="bg-white rounded-lg border border-slate-200 p-5">
              <h2 class="font-semibold text-slate-900 mb-3">Descripción de la consulta</h2>
              <p class="text-sm text-slate-600 leading-relaxed">{{ c.description }}</p>
              @if (c.notes) {
                <div class="mt-4 pt-4 border-t border-slate-100">
                  <p class="text-xs font-semibold text-slate-500 mb-1 uppercase tracking-wider">Notas internas</p>
                  <p class="text-sm text-slate-600">{{ c.notes }}</p>
                </div>
              }
            </div>

            <div class="bg-white rounded-lg border border-slate-200 p-5">
              <div class="flex items-center justify-between mb-3">
                <h2 class="font-semibold text-slate-900">Documentos de la consulta</h2>
                <a routerLink="/doctor/documents">
                  <button appBtn variant="outline" size="sm" class="gap-1.5 text-xs">
                    <lucide-icon name="file-text" class="h-3.5 w-3.5" />
                    Ver documentos
                  </button>
                </a>
              </div>
              @if (caseDocuments().length === 0) {
                <p class="text-sm text-slate-400 italic py-4 text-center">No hay documentos adjuntos aún</p>
              } @else {
                <div class="space-y-2">
                  @for (doc of caseDocuments(); track doc.id) {
                    <div class="flex items-center gap-3 p-3 rounded-md border border-slate-100">
                      <lucide-icon name="file-text" class="h-4 w-4 text-slate-400" />
                      <span class="text-sm text-slate-700">{{ doc.title }}</span>
                    </div>
                  }
                </div>
              }
            </div>
          </div>

          <div class="space-y-4">
            @if (c.context) {
              <div class="bg-white rounded-lg border border-slate-200 p-5">
                <h3 class="font-semibold text-slate-900 mb-3 flex items-center gap-2">
                  <lucide-icon name="shield" class="h-4 w-4 text-slate-400" />
                  Contexto simulado
                </h3>
                <div class="space-y-1.5 text-sm">
                  @if (c.context.contextCode) {
                    <p class="font-medium text-slate-800">{{ c.context.contextCode }}</p>
                  }
                  @if (c.context.medicalArea) {
                    <p class="text-slate-500">Área: {{ c.context.medicalArea }}</p>
                  }
                  @if (c.context.ageReference) {
                    <p class="text-slate-500">Edad de referencia: {{ c.context.ageReference }}</p>
                  }
                  @if (c.context.eventDate) {
                    <p class="text-slate-500">Fecha del evento: {{ formatDate(c.context.eventDate) }}</p>
                  }
                  @if (c.context.summary) {
                    <p class="text-slate-600 mt-2">{{ c.context.summary }}</p>
                  }
                  @if (c.context.relevantFactors?.length) {
                    <div class="flex flex-wrap gap-1.5 mt-2">
                      @for (factor of c.context.relevantFactors; track factor) {
                        <span class="text-xs bg-slate-100 text-slate-600 rounded-full px-2 py-0.5">{{ factor }}</span>
                      }
                    </div>
                  }
                </div>
              </div>
            }

            <div class="bg-white rounded-lg border border-slate-200 p-5">
              <h3 class="font-semibold text-slate-900 mb-3 flex items-center gap-2">
                <lucide-icon name="scale" class="h-4 w-4 text-slate-400" />
                Abogado asignado
              </h3>
              @if (c.lawyer) {
                <div class="space-y-1.5 text-sm">
                  <p class="font-medium text-slate-800">{{ c.lawyer.fullName }}</p>
                  @if (c.lawyer.email) {
                    <p class="text-slate-500">{{ c.lawyer.email }}</p>
                  }
                </div>
              } @else {
                <div class="text-center py-2">
                  <p class="text-sm text-slate-400 mb-3">Sin abogado asignado</p>
                  <a routerLink="/doctor/lawyers">
                    <button appBtn variant="outline" size="sm" class="text-xs w-full">Buscar abogado</button>
                  </a>
                </div>
              }
            </div>

            <div class="bg-white rounded-lg border border-slate-200 p-5">
              <h3 class="font-semibold text-slate-900 mb-3 flex items-center gap-2">
                <lucide-icon name="calendar" class="h-4 w-4 text-slate-400" />
                Fechas
              </h3>
              <div class="space-y-2 text-sm">
                <div>
                  <p class="text-xs text-slate-400 uppercase tracking-wider">Creado</p>
                  <p class="text-slate-700">{{ formatDate(c.createdAt ?? '') }}</p>
                </div>
                <div>
                  <p class="text-xs text-slate-400 uppercase tracking-wider">Actualizado</p>
                  <p class="text-slate-700">{{ formatDateTime(c.updatedAt ?? '') }}</p>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    }
  `,
})
export class DoctorCaseDetailComponent {
  private readonly route = inject(ActivatedRoute);
  private readonly casesApi = inject(CasesApi);
  private readonly documentsApi = inject(DocumentsApi);
  private readonly injector = inject(Injector);

  protected readonly formatDate = formatDate;
  protected readonly formatDateTime = formatDateTime;
  protected readonly asStatus = (s?: string) => (s ?? 'pendiente') as CaseStatus;
  protected readonly asPriority = (p?: string) => (p ?? 'media') as CasePriority;

  private readonly caseId = toSignal(
    this.route.paramMap.pipe(map((params) => params.get('id') ?? '')),
    { initialValue: '', injector: this.injector },
  );

  protected readonly caseQuery = injectQuery(() => ({
    queryKey: ['cases', this.caseId()],
    queryFn: () => this.casesApi.get(this.caseId()),
    enabled: !!this.caseId(),
  }));

  protected readonly documentsQuery = injectQuery(() => ({
    queryKey: ['documents', { pageSize: 100 }],
    queryFn: () => this.documentsApi.list({ pageSize: 100 }),
  }));

  protected readonly caseDocuments = computed(() =>
    (this.documentsQuery.data()?.data ?? []).filter((doc) => doc.caseId === this.caseId()),
  );
}
