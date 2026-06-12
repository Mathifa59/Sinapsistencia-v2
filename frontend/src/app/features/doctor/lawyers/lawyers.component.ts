import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { injectMutation, injectQuery, injectQueryClient } from '@tanstack/angular-query-experimental';
import { LucideAngularModule } from 'lucide-angular';
import { AuthService } from '../../../core/auth/auth.service';
import { MatchingApi } from '../../../core/api/matching.api';
import { BtnDirective } from '../../../shared/ui/button.directive';
import { InputDirective } from '../../../shared/ui/field.directives';
import { BadgeDirective } from '../../../shared/ui/badge.directive';
import { cn, getInitials } from '../../../shared/utils/cn';

/** Réplica de app/doctor/lawyers/page.tsx (Abogados sugeridos, con recomendaciones ML). */
@Component({
  selector: 'app-doctor-lawyers',
  imports: [FormsModule, LucideAngularModule, BtnDirective, InputDirective, BadgeDirective],
  template: `
    <div class="space-y-5">
      <div>
        <h1 class="text-2xl font-bold text-slate-900">Abogados sugeridos</h1>
        <p class="text-slate-500 text-sm mt-1">Profesionales compatibles con tu perfil y consultas activas</p>
      </div>

      <div class="relative">
        <lucide-icon name="search" class="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-slate-400" />
        <input appInput placeholder="Buscar por nombre o especialidad..." class="pl-9" [(ngModel)]="search" />
      </div>

      @if (lawyersQuery.isLoading()) {
        <div class="flex items-center justify-center py-16 text-slate-400">
          <lucide-icon name="loader-2" class="h-5 w-5 animate-spin mr-2" />
          <span>Cargando abogados...</span>
        </div>
      }

      <div class="grid md:grid-cols-2 gap-4">
        @for (lawyer of filteredLawyers(); track lawyer.id) {
          @let matchScore = getMatchScore(lawyer.id);
          @let matchReasons = getMatchReasons(lawyer.id);
          @let alreadyRequested = hasActiveRequest(lawyer.id);
          @let isHighMatch = matchScore !== undefined && matchScore >= 80;

          <div [class]="cn('bg-white rounded-lg border p-5 space-y-4', isHighMatch ? 'border-amber-300 ring-1 ring-amber-200 bg-amber-50/30' : 'border-slate-200')">
            <div class="flex items-start gap-4">
              <div class="h-12 w-12 rounded-full bg-slate-900 flex items-center justify-center shrink-0 text-white font-bold text-sm">
                {{ getInitials(lawyer.fullName ?? '') }}
              </div>
              <div class="flex-1 min-w-0">
                <div class="flex items-start justify-between gap-2">
                  <div>
                    <p class="font-semibold text-slate-900">{{ lawyer.fullName }}</p>
                    <p class="text-xs text-slate-500">CAB: {{ lawyer.cab }}</p>
                  </div>
                  @if (matchScore !== undefined) {
                    <div [class]="cn('flex items-center gap-1 text-xs font-bold px-2.5 py-1 rounded-full shrink-0', isHighMatch ? 'bg-amber-100 text-amber-800 border border-amber-300' : 'bg-blue-50 text-blue-700 border border-blue-100')">
                      @if (isHighMatch) {
                        <lucide-icon name="star" class="h-3 w-3 text-amber-500 fill-amber-500" />
                      }
                      <lucide-icon name="scale" class="h-3 w-3" />
                      {{ matchScore }}% match
                    </div>
                  }
                </div>
              </div>
            </div>

            <div>
              <div class="flex flex-wrap gap-1.5 mb-3">
                @for (specialty of lawyer.specialties ?? []; track specialty) {
                  <span appBadge variant="secondary" class="text-xs">{{ specialty }}</span>
                }
              </div>
              <div class="flex items-center gap-4 text-xs text-slate-500">
                <span class="flex items-center gap-1">
                  <lucide-icon name="star" class="h-3.5 w-3.5 text-amber-400 fill-amber-400" />
                  {{ lawyer.rating ?? 0 }} / 5.0
                </span>
                <span>{{ lawyer.yearsExperience ?? 0 }} años de experiencia</span>
                <span>{{ lawyer.resolvedCases ?? 0 }} casos</span>
              </div>
            </div>

            @if (matchReasons.length > 0) {
              <div class="bg-slate-50 rounded-md p-3">
                <p class="text-xs font-semibold text-slate-500 mb-2 uppercase tracking-wider">Por qué es compatible</p>
                <ul class="space-y-1">
                  @for (reason of matchReasons; track reason) {
                    <li class="flex items-start gap-1.5 text-xs text-slate-600">
                      <lucide-icon name="check-circle" class="h-3.5 w-3.5 text-emerald-500 mt-0.5 shrink-0" />
                      {{ reason }}
                    </li>
                  }
                </ul>
              </div>
            }

            @if (lawyer.bio) {
              <p class="text-xs text-slate-500 leading-relaxed line-clamp-2">{{ lawyer.bio }}</p>
            }

            <button
              appBtn
              [variant]="alreadyRequested ? 'secondary' : 'primary'"
              size="sm"
              class="w-full gap-2"
              (click)="sendContactRequest(lawyer)"
              [disabled]="alreadyRequested || contactMutation.isPending()"
            >
              @if (alreadyRequested) {
                <lucide-icon name="check-circle" class="h-4 w-4" />Solicitud enviada
              } @else {
                <lucide-icon name="send" class="h-4 w-4" />Solicitar contacto
              }
            </button>
          </div>
        }
      </div>
    </div>
  `,
})
export class DoctorLawyersComponent {
  private readonly auth = inject(AuthService);
  private readonly matchingApi = inject(MatchingApi);
  private readonly queryClient = injectQueryClient();

  protected readonly search = signal('');
  protected readonly getInitials = getInitials;
  protected readonly cn = cn;

  private readonly userId = computed(() => this.auth.user()?.id ?? '');

  protected readonly lawyersQuery = injectQuery(() => ({
    queryKey: ['matching', 'lawyers'],
    queryFn: () => this.matchingApi.lawyers(),
  }));

  protected readonly recommendationsQuery = injectQuery(() => ({
    queryKey: ['matching', 'recommendations', this.userId()],
    queryFn: () => this.matchingApi.recommendations(this.userId()),
    enabled: !!this.userId(),
  }));

  protected readonly contactRequestsQuery = injectQuery(() => ({
    queryKey: ['matching', 'contact-requests', { doctorId: this.userId() }],
    queryFn: () => this.matchingApi.contactRequests({ doctorId: this.userId() }),
    enabled: !!this.userId(),
  }));

  protected readonly filteredLawyers = computed(() => {
    const term = this.search().trim().toLowerCase();
    const lawyers = this.lawyersQuery.data() ?? [];
    if (!term) return lawyers;
    return lawyers.filter(
      (l) =>
        (l.fullName ?? '').toLowerCase().includes(term) ||
        (l.specialties ?? []).some((s) => s.toLowerCase().includes(term)),
    );
  });

  protected getMatchScore(lawyerId?: string): number | undefined {
    return this.recommendationsQuery.data()?.recommendations?.find((r) => r.lawyer?.id === lawyerId)?.score;
  }

  protected getMatchReasons(lawyerId?: string): string[] {
    return this.recommendationsQuery.data()?.recommendations?.find((r) => r.lawyer?.id === lawyerId)?.reasons ?? [];
  }

  protected hasActiveRequest(lawyerId?: string): boolean {
    return (this.contactRequestsQuery.data() ?? []).some(
      (r) => r.toLawyerId === lawyerId && (r.status === 'pendiente' || r.status === 'aceptado'),
    );
  }

  protected readonly contactMutation = injectMutation(() => ({
    mutationFn: (toLawyerId: string) =>
      this.matchingApi.createContactRequest({
        fromDoctorId: this.userId(),
        toLawyerId,
        message: `Hola, me gustaría contactarte para revisar una consulta.`,
      }),
    onSuccess: () => this.queryClient.invalidateQueries({ queryKey: ['matching', 'contact-requests'] }),
  }));

  protected sendContactRequest(lawyer: { id?: string; fullName?: string }): void {
    if (!lawyer.id) return;
    this.contactMutation.mutate(lawyer.id);
  }
}
