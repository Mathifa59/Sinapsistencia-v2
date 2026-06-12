import { Component, computed, inject, signal } from '@angular/core';
import { injectMutation, injectQuery, injectQueryClient } from '@tanstack/angular-query-experimental';
import { LucideAngularModule } from 'lucide-angular';
import { AuthService } from '../../../core/auth/auth.service';
import { MatchingApi } from '../../../core/api/matching.api';
import { BtnDirective } from '../../../shared/ui/button.directive';
import { ContactRequestStatusBadgeComponent } from '../../../shared/ui/status-badges.component';
import { cn, formatDate, getInitials } from '../../../shared/utils/cn';
import { CONTACT_REQUEST_STATUS_LABELS, type ContactRequestStatus } from '../../../shared/constants';

type RequestFilter = ContactRequestStatus | 'todas';

const FILTERS: RequestFilter[] = ['todas', 'pendiente', 'aceptado', 'rechazado'];

/** Réplica de app/lawyer/requests/page.tsx. */
@Component({
  selector: 'app-lawyer-requests',
  imports: [LucideAngularModule, BtnDirective, ContactRequestStatusBadgeComponent],
  template: `
    <div class="space-y-5">
      <div>
        <h1 class="text-2xl font-bold text-slate-900">Solicitudes de contacto</h1>
        <p class="text-slate-500 text-sm mt-1">{{ requestsQuery.data()?.length ?? 0 }} solicitudes recibidas</p>
      </div>

      <div class="flex gap-2 flex-wrap">
        @for (filter of filters; track filter) {
          <button
            type="button"
            [class]="cn('px-3 py-1.5 rounded-full text-xs font-medium border transition-colors', activeFilter() === filter ? 'bg-slate-900 text-white border-slate-900' : 'bg-white text-slate-600 border-slate-200 hover:border-slate-300')"
            (click)="activeFilter.set(filter)"
          >
            {{ filter === 'todas' ? 'Todas' : statusLabels[filter] }}
          </button>
        }
      </div>

      @if (requestsQuery.isLoading()) {
        <div class="flex items-center justify-center py-16 text-slate-400">
          <lucide-icon name="loader-2" class="h-5 w-5 animate-spin mr-2" />
          <span>Cargando solicitudes...</span>
        </div>
      }

      <div class="space-y-4">
        @for (request of filteredRequests(); track request.id) {
          <div class="bg-white rounded-lg border border-slate-200 p-5 space-y-4">
            <div class="flex items-start justify-between gap-3">
              <div class="flex items-center gap-3">
                <div class="h-10 w-10 rounded-full bg-blue-100 flex items-center justify-center font-bold text-blue-700 text-sm shrink-0">
                  {{ getInitials(request.fromDoctor?.fullName ?? '') }}
                </div>
                <div>
                  <p class="font-semibold text-slate-900">{{ request.fromDoctor?.fullName }}</p>
                  <p class="text-xs text-slate-500">{{ request.fromDoctor?.specialty }}</p>
                </div>
              </div>
              <div class="flex items-center gap-2 shrink-0">
                <app-contact-request-status-badge [status]="asStatus(request.status)" />
                <span class="text-xs text-slate-400">{{ formatDate(request.createdAt ?? '') }}</span>
              </div>
            </div>

            @if (request.caseTitle) {
              <div class="bg-slate-50 rounded-md px-4 py-3 text-sm">
                <p class="text-xs font-semibold text-slate-400 uppercase tracking-wider mb-1">Caso relacionado</p>
                <p class="text-slate-700 font-medium">{{ request.caseTitle }}</p>
              </div>
            }

            <p class="text-sm text-slate-600 leading-relaxed">{{ request.message }}</p>

            @if (request.responseMessage) {
              <div class="flex items-start gap-2 bg-emerald-50 border border-emerald-100 rounded-md px-4 py-3">
                <lucide-icon name="send" class="h-4 w-4 text-emerald-600 mt-0.5 shrink-0" />
                <p class="text-sm text-emerald-700">{{ request.responseMessage }}</p>
              </div>
            }

            @if (request.status === 'pendiente') {
              <div class="flex gap-2 pt-1">
                <button appBtn size="sm" variant="primary" class="gap-1.5 flex-1" [disabled]="respondMutation.isPending()" (click)="respond(request.id!, 'aceptado')">
                  <lucide-icon name="check-circle" class="h-4 w-4" />Aceptar
                </button>
                <button appBtn size="sm" variant="outline" class="gap-1.5 flex-1" [disabled]="respondMutation.isPending()" (click)="respond(request.id!, 'rechazado')">
                  <lucide-icon name="x-circle" class="h-4 w-4" />Rechazar
                </button>
              </div>
            }
          </div>
        }

        @if (!requestsQuery.isLoading() && filteredRequests().length === 0) {
          <div class="text-center py-16 text-slate-400">No hay solicitudes</div>
        }
      </div>
    </div>
  `,
})
export class LawyerRequestsComponent {
  private readonly auth = inject(AuthService);
  private readonly matchingApi = inject(MatchingApi);
  private readonly queryClient = injectQueryClient();

  protected readonly filters = FILTERS;
  protected readonly statusLabels = CONTACT_REQUEST_STATUS_LABELS;
  protected readonly activeFilter = signal<RequestFilter>('todas');
  protected readonly cn = cn;
  protected readonly formatDate = formatDate;
  protected readonly getInitials = getInitials;
  protected readonly asStatus = (s?: string) => (s ?? 'pendiente') as ContactRequestStatus;

  private readonly userId = computed(() => this.auth.user()?.id ?? '');

  protected readonly requestsQuery = injectQuery(() => ({
    queryKey: ['matching', 'contact-requests', { lawyerId: this.userId() }],
    queryFn: () => this.matchingApi.contactRequests({ lawyerId: this.userId() }),
    enabled: !!this.userId(),
  }));

  protected readonly filteredRequests = computed(() => {
    const filter = this.activeFilter();
    const requests = this.requestsQuery.data() ?? [];
    if (filter === 'todas') return requests;
    return requests.filter((r) => r.status === filter);
  });

  protected readonly respondMutation = injectMutation(() => ({
    mutationFn: (params: { requestId: string; status: string }) =>
      this.matchingApi.respondContactRequest({ requestId: params.requestId, status: params.status }),
    onSuccess: () => this.queryClient.invalidateQueries({ queryKey: ['matching', 'contact-requests'] }),
  }));

  protected respond(requestId: string, status: string): void {
    this.respondMutation.mutate({ requestId, status });
  }
}
