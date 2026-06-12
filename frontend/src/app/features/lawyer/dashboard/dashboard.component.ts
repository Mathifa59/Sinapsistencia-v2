import { Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { injectMutation, injectQuery, injectQueryClient } from '@tanstack/angular-query-experimental';
import { LucideAngularModule } from 'lucide-angular';
import { AuthService } from '../../../core/auth/auth.service';
import { MatchingApi } from '../../../core/api/matching.api';
import { StatCardComponent } from '../../../shared/ui/stat-card.component';
import { BtnDirective } from '../../../shared/ui/button.directive';
import { ContactRequestStatusBadgeComponent, CasePriorityBadgeComponent } from '../../../shared/ui/status-badges.component';
import { BadgeDirective } from '../../../shared/ui/badge.directive';
import { formatDateTime } from '../../../shared/utils/cn';
import type { CasePriority, ContactRequestStatus } from '../../../shared/constants';

interface RelevantCase {
  id: string;
  title: string;
  description: string;
  status: string;
  priority: string;
  doctorId: string;
  doctor?: { id: string; fullName: string; specialty: string };
  createdAt: string;
}

interface RelevantCasesResponse {
  data: RelevantCase[];
  medicalAreas: string[];
}

/** Réplica de app/lawyer/dashboard/page.tsx. */
@Component({
  selector: 'app-lawyer-dashboard',
  imports: [RouterLink, LucideAngularModule, StatCardComponent, BtnDirective, ContactRequestStatusBadgeComponent, CasePriorityBadgeComponent, BadgeDirective],
  template: `
    <div class="space-y-6">
      <div>
        <h1 class="text-2xl font-bold text-slate-900">Dashboard Legal</h1>
        <p class="text-slate-500 text-sm mt-1">Gestiona tus solicitudes y casos</p>
      </div>

      <div class="grid grid-cols-2 lg:grid-cols-4 gap-4">
        <app-stat-card title="Solicitudes nuevas" [value]="pendingRequests().length" icon="bell" color="amber" description="Requieren respuesta" />
        <app-stat-card title="Casos activos" [value]="acceptedRequests().length" icon="briefcase" color="blue" description="En seguimiento" />
        <app-stat-card title="Médicos disponibles" [value]="doctorsQuery.data()?.length ?? 0" icon="users" color="emerald" description="Perfil compatible" />
        <app-stat-card title="Valoración promedio" value="4.8" icon="star" color="slate" description="Basado en historial" />
      </div>

      <div class="grid lg:grid-cols-2 gap-6">
        <div class="bg-white rounded-lg border border-slate-200">
          <div class="flex items-center justify-between px-5 py-4 border-b border-slate-100">
            <h2 class="font-semibold text-slate-900">Solicitudes pendientes</h2>
            <a routerLink="/lawyer/requests" class="text-xs text-blue-600 hover:underline flex items-center gap-1">
              Ver todas <lucide-icon name="arrow-right" class="h-3 w-3" />
            </a>
          </div>
          @if (contactRequestsQuery.isLoading()) {
            <div class="flex items-center justify-center py-10 text-slate-400">
              <lucide-icon name="loader-2" class="h-5 w-5 animate-spin mr-2" />
            </div>
          } @else if (pendingRequests().length === 0) {
            <div class="px-5 py-10 text-center text-slate-400 text-sm">No hay solicitudes pendientes</div>
          } @else {
            <div class="divide-y divide-slate-50">
              @for (request of pendingRequests(); track request.id) {
                <div class="px-5 py-4 space-y-3">
                  <div class="flex items-start justify-between gap-3">
                    <div>
                      <p class="text-sm font-medium text-slate-800">{{ request.fromDoctor?.fullName }}</p>
                      <p class="text-xs text-slate-500">{{ request.fromDoctor?.specialty }} · {{ request.fromDoctor?.hospital }}</p>
                    </div>
                    <app-contact-request-status-badge [status]="asReqStatus(request.status)" />
                  </div>
                  <p class="text-xs text-slate-600 line-clamp-2">{{ request.message }}</p>
                  <p class="text-xs text-slate-400">{{ formatDateTime(request.createdAt ?? '') }}</p>
                  <div class="flex gap-2">
                    <button appBtn size="sm" variant="primary" class="gap-1.5 text-xs flex-1" [disabled]="respondMutation.isPending()" (click)="respond(request.id!, 'aceptado')">
                      <lucide-icon name="check-circle" class="h-3.5 w-3.5" />Aceptar
                    </button>
                    <button appBtn size="sm" variant="outline" class="gap-1.5 text-xs flex-1" [disabled]="respondMutation.isPending()" (click)="respond(request.id!, 'rechazado')">
                      <lucide-icon name="x-circle" class="h-3.5 w-3.5" />Rechazar
                    </button>
                  </div>
                </div>
              }
            </div>
          }
        </div>

        <div class="bg-white rounded-lg border border-slate-200">
          <div class="flex items-center justify-between px-5 py-4 border-b border-slate-100">
            <h2 class="font-semibold text-slate-900">Casos en seguimiento</h2>
          </div>
          @if (acceptedRequests().length === 0) {
            <div class="px-5 py-10 text-center text-slate-400 text-sm">No hay casos activos</div>
          } @else {
            <div class="divide-y divide-slate-50">
              @for (request of acceptedRequests(); track request.id) {
                <div class="px-5 py-4">
                  <div class="flex items-start justify-between gap-3 mb-2">
                    <div>
                      <p class="text-sm font-medium text-slate-800">{{ request.caseTitle ?? 'Caso sin título' }}</p>
                      <p class="text-xs text-slate-500">{{ request.fromDoctor?.fullName }}</p>
                    </div>
                    <span appBadge variant="success">Activo</span>
                  </div>
                  @if (request.responseMessage) {
                    <p class="text-xs text-slate-500 flex items-start gap-1.5">
                      <lucide-icon name="clock" class="h-3.5 w-3.5 text-slate-400 mt-0.5 shrink-0" />
                      {{ request.responseMessage }}
                    </p>
                  }
                </div>
              }
            </div>
          }
        </div>
      </div>

      <div class="bg-white rounded-lg border border-slate-200">
        <div class="flex items-center justify-between px-5 py-4 border-b border-slate-100">
          <div>
            <h2 class="font-semibold text-slate-900 flex items-center gap-2">
              <lucide-icon name="alert-triangle" class="h-4 w-4 text-amber-500" />
              Casos que podrían interesarte
            </h2>
            @if (medicalAreas().length > 0) {
              <p class="text-xs text-slate-400 mt-1">Basado en tus áreas: {{ medicalAreas().join(', ') }}</p>
            }
          </div>
        </div>
        @if (relevantCasesQuery.isLoading()) {
          <div class="flex items-center justify-center py-10 text-slate-400">
            <lucide-icon name="loader-2" class="h-5 w-5 animate-spin mr-2" />
          </div>
        } @else if (relevantCases().length === 0) {
          <div class="px-5 py-10 text-center text-slate-400 text-sm">
            @if (medicalAreas().length === 0) {
              Completa tu perfil con áreas médicas de interés para ver casos relevantes
            } @else {
              No hay casos disponibles en tus áreas en este momento
            }
          </div>
        } @else {
          <div class="divide-y divide-slate-50">
            @for (c of relevantCases(); track c.id) {
              <div class="px-5 py-4">
                <div class="flex items-start justify-between gap-3 mb-2">
                  <div class="min-w-0 flex-1">
                    <p class="text-sm font-medium text-slate-800 truncate">{{ c.title }}</p>
                    @if (c.doctor) {
                      <span class="text-xs text-slate-500 flex items-center gap-1 mt-1">
                        <lucide-icon name="stethoscope" class="h-3 w-3" />
                        {{ c.doctor.fullName }} · {{ c.doctor.specialty }}
                      </span>
                    }
                  </div>
                  <app-case-priority-badge [priority]="asPriority(c.priority)" />
                </div>
                <p class="text-xs text-slate-500 line-clamp-2">{{ c.description }}</p>
              </div>
            }
          </div>
        }
      </div>
    </div>
  `,
})
export class LawyerDashboardComponent {
  private readonly auth = inject(AuthService);
  private readonly matchingApi = inject(MatchingApi);
  private readonly queryClient = injectQueryClient();

  protected readonly formatDateTime = formatDateTime;
  protected readonly asReqStatus = (s?: string) => (s ?? 'pendiente') as ContactRequestStatus;
  protected readonly asPriority = (s?: string) => (s ?? 'media') as CasePriority;

  private readonly userId = computed(() => this.auth.user()?.id ?? '');

  protected readonly contactRequestsQuery = injectQuery(() => ({
    queryKey: ['matching', 'contact-requests', { lawyerId: this.userId() }],
    queryFn: () => this.matchingApi.contactRequests({ lawyerId: this.userId() }),
    enabled: !!this.userId(),
  }));

  protected readonly doctorsQuery = injectQuery(() => ({
    queryKey: ['matching', 'doctors'],
    queryFn: () => this.matchingApi.doctors(),
  }));

  protected readonly relevantCasesQuery = injectQuery(() => ({
    queryKey: ['matching', 'relevant-cases', this.userId()],
    queryFn: () => this.matchingApi.relevantCases(this.userId()) as Promise<unknown> as Promise<RelevantCasesResponse>,
    enabled: !!this.userId(),
  }));

  protected readonly pendingRequests = computed(() => (this.contactRequestsQuery.data() ?? []).filter((r) => r.status === 'pendiente'));
  protected readonly acceptedRequests = computed(() => (this.contactRequestsQuery.data() ?? []).filter((r) => r.status === 'aceptado'));
  protected readonly relevantCases = computed(() => this.relevantCasesQuery.data()?.data ?? []);
  protected readonly medicalAreas = computed(() => this.relevantCasesQuery.data()?.medicalAreas ?? []);

  protected readonly respondMutation = injectMutation(() => ({
    mutationFn: (params: { requestId: string; status: string }) =>
      this.matchingApi.respondContactRequest({ requestId: params.requestId, status: params.status }),
    onSuccess: () => this.queryClient.invalidateQueries({ queryKey: ['matching', 'contact-requests'] }),
  }));

  protected respond(requestId: string, status: string): void {
    this.respondMutation.mutate({ requestId, status });
  }
}
