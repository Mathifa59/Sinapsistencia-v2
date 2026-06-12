import { Component, computed, inject } from '@angular/core';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { LucideAngularModule } from 'lucide-angular';
import { CasesApi } from '../../../core/api/cases.api';
import { DocumentsApi } from '../../../core/api/documents.api';
import { MlApi } from '../../../core/api/ml.api';
import { StatCardComponent } from '../../../shared/ui/stat-card.component';
import {
  CASE_STATUS_LABELS,
  CASE_PRIORITY_LABELS,
  DOCUMENT_STATUS_LABELS,
  type CaseStatus,
  type CasePriority,
  type DocumentStatus,
} from '../../../shared/constants';

const CASE_STATUS_ORDER: CaseStatus[] = ['pendiente', 'clasificada', 'asignada', 'en_revision', 'respondida', 'cerrada'];
const CASE_PRIORITY_ORDER: CasePriority[] = ['baja', 'media', 'alta', 'critica'];
const DOCUMENT_STATUS_ORDER: DocumentStatus[] = ['borrador', 'pendiente_firma', 'firmado', 'archivado'];

const BAR_COLORS: Record<string, string> = {
  pendiente: 'bg-slate-400',
  clasificada: 'bg-sky-500',
  asignada: 'bg-sky-500',
  en_revision: 'bg-amber-500',
  respondida: 'bg-emerald-500',
  cerrada: 'bg-slate-300',
  baja: 'bg-slate-400',
  media: 'bg-sky-500',
  alta: 'bg-amber-500',
  critica: 'bg-red-500',
  borrador: 'bg-slate-400',
  pendiente_firma: 'bg-amber-500',
  firmado: 'bg-emerald-500',
  archivado: 'bg-slate-300',
};

/** Página de métricas del sistema y estado del modelo ML (HUs de dashboard/reportes). */
@Component({
  selector: 'app-admin-metrics',
  imports: [LucideAngularModule, StatCardComponent],
  template: `
    <div class="space-y-6">
      <div>
        <h1 class="text-2xl font-bold text-slate-900">Métricas y Modelo</h1>
        <p class="text-slate-500 text-sm mt-1">Indicadores generales del sistema y estado del servicio de Machine Learning</p>
      </div>

      <div class="grid grid-cols-2 lg:grid-cols-4 gap-4">
        <app-stat-card title="Consultas totales" [value]="casesQuery.data()?.total ?? 0" icon="activity" color="emerald" description="Flujo de 6 estados" />
        <app-stat-card title="Documentos totales" [value]="documentsQuery.data()?.total ?? 0" icon="folder-open" color="blue" description="Clínicos y legales" />
        <app-stat-card title="Casos críticos" [value]="criticalCases()" icon="alert-triangle" color="red" description="Prioridad crítica" />
        <app-stat-card title="Servicio ML" [value]="mlStatusLabel()" [icon]="mlStatusIcon()" [color]="mlStatusColor()" description="Estado de salud" />
      </div>

      <div class="grid lg:grid-cols-3 gap-6">
        <div class="bg-white rounded-lg border border-slate-200 p-5">
          <h2 class="font-semibold text-slate-900 mb-1">Consultas por estado</h2>
          <p class="text-xs text-slate-400 mb-4">Distribución según el flujo de 6 estados (HU-16)</p>
          @if (casesQuery.isLoading()) {
            <div class="flex items-center justify-center py-10 text-slate-400">
              <lucide-icon name="loader-2" class="h-5 w-5 animate-spin" />
            </div>
          } @else {
            <div class="space-y-3">
              @for (item of caseStatusDistribution(); track item.key) {
                <div>
                  <div class="flex items-center justify-between text-xs mb-1">
                    <span class="font-medium text-slate-600">{{ item.label }}</span>
                    <span class="text-slate-400">{{ item.count }} ({{ item.percent }}%)</span>
                  </div>
                  <div class="h-2 rounded-full bg-slate-100 overflow-hidden">
                    <div class="h-full rounded-full" [class]="item.color" [style.width.%]="item.percent"></div>
                  </div>
                </div>
              }
            </div>
          }
        </div>

        <div class="bg-white rounded-lg border border-slate-200 p-5">
          <h2 class="font-semibold text-slate-900 mb-1">Consultas por prioridad</h2>
          <p class="text-xs text-slate-400 mb-4">Clasificación asignada por el modelo</p>
          @if (casesQuery.isLoading()) {
            <div class="flex items-center justify-center py-10 text-slate-400">
              <lucide-icon name="loader-2" class="h-5 w-5 animate-spin" />
            </div>
          } @else {
            <div class="space-y-3">
              @for (item of casePriorityDistribution(); track item.key) {
                <div>
                  <div class="flex items-center justify-between text-xs mb-1">
                    <span class="font-medium text-slate-600">{{ item.label }}</span>
                    <span class="text-slate-400">{{ item.count }} ({{ item.percent }}%)</span>
                  </div>
                  <div class="h-2 rounded-full bg-slate-100 overflow-hidden">
                    <div class="h-full rounded-full" [class]="item.color" [style.width.%]="item.percent"></div>
                  </div>
                </div>
              }
            </div>
          }
        </div>

        <div class="bg-white rounded-lg border border-slate-200 p-5">
          <h2 class="font-semibold text-slate-900 mb-1">Documentos por estado</h2>
          <p class="text-xs text-slate-400 mb-4">Ciclo de firma documental</p>
          @if (documentsQuery.isLoading()) {
            <div class="flex items-center justify-center py-10 text-slate-400">
              <lucide-icon name="loader-2" class="h-5 w-5 animate-spin" />
            </div>
          } @else {
            <div class="space-y-3">
              @for (item of documentStatusDistribution(); track item.key) {
                <div>
                  <div class="flex items-center justify-between text-xs mb-1">
                    <span class="font-medium text-slate-600">{{ item.label }}</span>
                    <span class="text-slate-400">{{ item.count }} ({{ item.percent }}%)</span>
                  </div>
                  <div class="h-2 rounded-full bg-slate-100 overflow-hidden">
                    <div class="h-full rounded-full" [class]="item.color" [style.width.%]="item.percent"></div>
                  </div>
                </div>
              }
            </div>
          }
        </div>
      </div>

      <div class="bg-white rounded-lg border border-slate-200 p-5">
        <h2 class="font-semibold text-slate-900 mb-1">Consultas por especialidad médica</h2>
        <p class="text-xs text-slate-400 mb-4">Top especialidades con mayor cantidad de consultas registradas</p>
        @if (casesQuery.isLoading()) {
          <div class="flex items-center justify-center py-10 text-slate-400">
            <lucide-icon name="loader-2" class="h-5 w-5 animate-spin" />
          </div>
        } @else if (specialtyDistribution().length === 0) {
          <p class="text-sm text-slate-400 text-center py-6">Sin datos de especialidad disponibles</p>
        } @else {
          <div class="grid sm:grid-cols-2 gap-3">
            @for (item of specialtyDistribution(); track item.key) {
              <div class="flex items-center justify-between gap-3 bg-slate-50 rounded-md px-3 py-2.5">
                <span class="text-sm font-medium text-slate-700 truncate">{{ item.label }}</span>
                <span class="text-sm font-semibold text-slate-900 shrink-0">{{ item.count }}</span>
              </div>
            }
          </div>
        }
      </div>
    </div>
  `,
})
export class AdminMetricsComponent {
  private readonly casesApi = inject(CasesApi);
  private readonly documentsApi = inject(DocumentsApi);
  private readonly mlApi = inject(MlApi);

  protected readonly casesQuery = injectQuery(() => ({
    queryKey: ['cases', { pageSize: 100 }],
    queryFn: () => this.casesApi.list({ pageSize: 100 }),
  }));

  protected readonly documentsQuery = injectQuery(() => ({
    queryKey: ['documents', { pageSize: 100 }],
    queryFn: () => this.documentsApi.list({ pageSize: 100 }),
  }));

  protected readonly mlHealthQuery = injectQuery(() => ({
    queryKey: ['ml', 'health'],
    queryFn: () => this.mlApi.health(),
    retry: false,
  }));

  protected readonly mlStatusLabel = computed(() => {
    if (this.mlHealthQuery.isLoading()) return '...';
    if (this.mlHealthQuery.isError()) return 'Inactivo';
    const status = this.mlHealthQuery.data()?.['status'];
    return status === 'ok' || status === 'healthy' ? 'Activo' : 'Inactivo';
  });

  protected readonly mlStatusIcon = computed(() => (this.mlStatusLabel() === 'Activo' ? 'check-circle' : 'x-circle'));
  protected readonly mlStatusColor = computed(() => (this.mlStatusLabel() === 'Activo' ? 'emerald' : 'red'));

  protected readonly criticalCases = computed(
    () => (this.casesQuery.data()?.data ?? []).filter((c) => c.priority === 'critica').length,
  );

  protected readonly caseStatusDistribution = computed(() => {
    const cases = this.casesQuery.data()?.data ?? [];
    const total = cases.length || 1;
    return CASE_STATUS_ORDER.map((status) => {
      const count = cases.filter((c) => c.status === status).length;
      return { key: status, label: CASE_STATUS_LABELS[status], count, percent: Math.round((count / total) * 100), color: BAR_COLORS[status] };
    });
  });

  protected readonly casePriorityDistribution = computed(() => {
    const cases = this.casesQuery.data()?.data ?? [];
    const total = cases.length || 1;
    return CASE_PRIORITY_ORDER.map((priority) => {
      const count = cases.filter((c) => c.priority === priority).length;
      return { key: priority, label: CASE_PRIORITY_LABELS[priority], count, percent: Math.round((count / total) * 100), color: BAR_COLORS[priority] };
    });
  });

  protected readonly documentStatusDistribution = computed(() => {
    const docs = this.documentsQuery.data()?.data ?? [];
    const total = docs.length || 1;
    return DOCUMENT_STATUS_ORDER.map((status) => {
      const count = docs.filter((d) => d.status === status).length;
      return { key: status, label: DOCUMENT_STATUS_LABELS[status], count, percent: Math.round((count / total) * 100), color: BAR_COLORS[status] };
    });
  });

  protected readonly specialtyDistribution = computed(() => {
    const cases = this.casesQuery.data()?.data ?? [];
    const counts = new Map<string, number>();
    for (const c of cases) {
      const specialty = c.medicalSpecialty;
      if (!specialty) continue;
      counts.set(specialty, (counts.get(specialty) ?? 0) + 1);
    }
    return [...counts.entries()]
      .map(([key, count]) => ({ key, label: key, count }))
      .sort((a, b) => b.count - a.count)
      .slice(0, 6);
  });
}
