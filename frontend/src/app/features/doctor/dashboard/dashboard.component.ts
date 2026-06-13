import { Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { LucideAngularModule } from 'lucide-angular';
import { AuthService } from '../../../core/auth/auth.service';
import { CasesApi } from '../../../core/api/cases.api';
import { DocumentsApi } from '../../../core/api/documents.api';
import { MatchingApi } from '../../../core/api/matching.api';
import { StatCardComponent } from '../../../shared/ui/stat-card.component';
import { CaseStatusBadgeComponent, CasePriorityBadgeComponent, DocumentStatusBadgeComponent } from '../../../shared/ui/status-badges.component';
import type { CasePriority, CaseStatus, DocumentStatus } from '../../../shared/constants';

/** Réplica de app/doctor/dashboard/page.tsx (sin datos de paciente). */
@Component({
  selector: 'app-doctor-dashboard',
  imports: [
    RouterLink,
    LucideAngularModule,
    StatCardComponent,
    CaseStatusBadgeComponent,
    CasePriorityBadgeComponent,
    DocumentStatusBadgeComponent,
  ],
  template: `
    <div class="space-y-6">
      <div>
        <h1 class="text-2xl font-bold text-slate-900">Dashboard Médico</h1>
        <p class="text-slate-500 text-sm mt-1">Resumen de tu actividad clínico-legal</p>
      </div>

      <div class="grid grid-cols-2 lg:grid-cols-4 gap-4">
        <app-stat-card title="Consultas activas" [value]="activeCases().length" icon="briefcase" color="blue" description="En seguimiento" />
        <app-stat-card title="Docs. pendientes" [value]="pendingDocs().length" icon="file-text" color="amber" description="Sin firma o en borrador" />
        <app-stat-card title="Abogados sugeridos" [value]="recommendationsCount()" icon="scale" color="emerald" description="Compatibles con tu perfil" />
        <app-stat-card title="Total de consultas" [value]="casesQuery.data()?.total ?? 0" icon="alert-triangle" color="slate" description="Histórico completo" />
      </div>

      <div class="grid lg:grid-cols-2 gap-6">
        <div class="bg-white rounded-lg border border-slate-200">
          <div class="flex items-center justify-between px-5 py-4 border-b border-slate-100">
            <h2 class="font-semibold text-slate-900">Consultas recientes</h2>
            <a routerLink="/doctor/cases" class="text-xs text-blue-600 hover:underline flex items-center gap-1">
              Ver todas <lucide-icon name="arrow-right" class="h-3 w-3" />
            </a>
          </div>
          <div class="divide-y divide-slate-50">
            @for (c of recentCases(); track c.id) {
              <a [routerLink]="['/doctor/cases', c.id]" class="flex items-start gap-3 px-5 py-4 hover:bg-slate-50 transition-colors">
                <div class="min-w-0 flex-1">
                  <p class="text-sm font-medium text-slate-800 truncate">{{ c.title }}</p>
                  <p class="text-xs text-slate-400 mt-0.5">{{ c.context?.contextCode ?? 'Sin código' }}</p>
                </div>
                <div class="flex flex-col items-end gap-1 shrink-0">
                  <app-case-status-badge [status]="asCaseStatus(c.status)" />
                  <app-case-priority-badge [priority]="asCasePriority(c.priority)" />
                </div>
              </a>
            }
            @if (recentCases().length === 0) {
              <p class="px-5 py-8 text-center text-sm text-slate-400">No hay consultas</p>
            }
          </div>
        </div>

        <div class="bg-white rounded-lg border border-slate-200">
          <div class="flex items-center justify-between px-5 py-4 border-b border-slate-100">
            <h2 class="font-semibold text-slate-900">Documentos recientes</h2>
            <a routerLink="/doctor/documents" class="text-xs text-blue-600 hover:underline flex items-center gap-1">
              Ver todos <lucide-icon name="arrow-right" class="h-3 w-3" />
            </a>
          </div>
          <div class="divide-y divide-slate-50">
            @for (doc of recentDocs(); track doc.id) {
              <div class="flex items-center gap-3 px-5 py-4">
                <div class="min-w-0 flex-1">
                  <p class="text-sm font-medium text-slate-800 truncate">{{ doc.title }}</p>
                  <p class="text-xs text-slate-400 mt-0.5">{{ doc.type }}</p>
                </div>
                <app-document-status-badge [status]="asDocumentStatus(doc.status)" />
              </div>
            }
            @if (recentDocs().length === 0) {
              <p class="px-5 py-8 text-center text-sm text-slate-400">No hay documentos</p>
            }
          </div>
        </div>
      </div>
    </div>
  `,
})
export class DoctorDashboardComponent {
  private readonly auth = inject(AuthService);
  private readonly casesApi = inject(CasesApi);
  private readonly documentsApi = inject(DocumentsApi);
  private readonly matchingApi = inject(MatchingApi);

  protected readonly asCaseStatus = (s?: string) => (s ?? 'pendiente') as CaseStatus;
  protected readonly asCasePriority = (p?: string) => (p ?? 'media') as CasePriority;
  protected readonly asDocumentStatus = (s?: string) => (s ?? 'borrador') as DocumentStatus;

  private readonly userId = computed(() => this.auth.user()?.id ?? '');

  protected readonly casesQuery = injectQuery(() => ({
    queryKey: ['cases', { doctorId: this.userId() }],
    queryFn: () => this.casesApi.list({ doctorId: this.userId() }),
    enabled: !!this.userId(),
  }));

  protected readonly documentsQuery = injectQuery(() => ({
    queryKey: ['documents', { authorId: this.userId() }],
    queryFn: () => this.documentsApi.list({ authorId: this.userId(), pageSize: 50 }),
    enabled: !!this.userId(),
  }));

  protected readonly recommendationsQuery = injectQuery(() => ({
    queryKey: ['matching', 'recommendations', this.userId()],
    queryFn: () => this.matchingApi.recommendations(this.userId()),
    enabled: !!this.userId(),
  }));

  protected readonly recentCases = computed(() => (this.casesQuery.data()?.data ?? []).slice(0, 4));
  protected readonly recentDocs = computed(() => (this.documentsQuery.data()?.data ?? []).slice(0, 4));
  protected readonly recommendationsCount = computed(
    () => this.recommendationsQuery.data()?.recommendations?.length ?? 0,
  );

  protected readonly activeCases = computed(() =>
    (this.casesQuery.data()?.data ?? []).filter((c) => c.status !== 'cerrada'),
  );
  protected readonly pendingDocs = computed(() =>
    (this.documentsQuery.data()?.data ?? []).filter(
      (d) => d.status === 'pendiente_firma' || d.status === 'borrador',
    ),
  );
}
