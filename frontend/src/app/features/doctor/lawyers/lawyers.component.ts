import { Component, computed, effect, inject, Injector, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { injectMutation, injectQuery, injectQueryClient } from '@tanstack/angular-query-experimental';
import { LucideAngularModule } from 'lucide-angular';
import { map } from 'rxjs';
import { AuthService } from '../../../core/auth/auth.service';
import { CasesApi } from '../../../core/api/cases.api';
import { MatchingApi } from '../../../core/api/matching.api';
import { BtnDirective } from '../../../shared/ui/button.directive';
import { InputDirective, SelectDirective } from '../../../shared/ui/field.directives';
import { BadgeDirective } from '../../../shared/ui/badge.directive';
import { CasePriorityBadgeComponent } from '../../../shared/ui/status-badges.component';
import { MlAdvisoryNoteComponent } from '../../../shared/ui/ml-advisory-note.component';
import { cn, getInitials } from '../../../shared/utils/cn';
import type { CasePriority } from '../../../shared/constants';

/** Abogados sugeridos: el médico elige la consulta a vincular antes de solicitar contacto. */
@Component({
  selector: 'app-doctor-lawyers',
  imports: [
    FormsModule,
    RouterLink,
    LucideAngularModule,
    BtnDirective,
    InputDirective,
    SelectDirective,
    BadgeDirective,
    CasePriorityBadgeComponent,
    MlAdvisoryNoteComponent,
  ],
  template: `
    <div class="space-y-5">
      <div>
        <h1 class="text-2xl font-bold text-slate-900">Abogados sugeridos</h1>
        <p class="text-slate-500 text-sm mt-1">Elige el caso a asignar y solicita contacto con un abogado compatible</p>
      </div>


      <div class="bg-white rounded-lg border border-slate-200 p-5 space-y-3">
        <div class="flex items-center gap-2">
          <lucide-icon name="briefcase" class="h-4 w-4 text-blue-600" />
          <h2 class="font-semibold text-slate-900">Caso a asignar</h2>
        </div>

        @if (openCasesQuery.isLoading()) {
          <p class="text-sm text-slate-400">Cargando casos...</p>
        } @else if (openCases().length === 0) {
          <div class="rounded-md border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-900">
            No tienes casos pendientes de asignar. Crea uno en
            <a routerLink="/doctor/cases" class="font-medium underline">Mis Casos</a>
            antes de buscar abogado.
          </div>
        } @else {
          <select
            appSelect
            class="w-full"
            [ngModel]="selectedCaseId()"
            (ngModelChange)="onCaseSelected($event)"
          >
            @for (c of openCases(); track c.id) {
              <option [value]="c.id">{{ c.title }} — {{ c.context?.contextCode ?? 'sin código' }}</option>
            }
          </select>

          @if (selectedCase(); as selected) {
            <div class="flex flex-wrap items-center gap-2 text-sm text-slate-600">
              <app-case-priority-badge [priority]="asPriority(selected.priority)" />
              <span class="text-slate-400">·</span>
              <span>{{ selected.medicalSpecialty || 'Sin especialidad' }}</span>
            </div>
          }
        }
      </div>

      <div class="relative">
        <lucide-icon name="search" class="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-slate-400" />
        <input appInput placeholder="Buscar por nombre o especialidad..." class="pl-9" [(ngModel)]="search" />
      </div>

      @if (!selectedCaseId() && openCases().length > 0) {
        <p class="text-sm text-amber-700 bg-amber-50 border border-amber-200 rounded-md px-4 py-3">
          Selecciona un caso para ver recomendaciones y enviar la solicitud de contacto.
        </p>
      }

      @if (lawyersQuery.isLoading()) {
        <div class="flex items-center justify-center py-16 text-slate-400">
          <lucide-icon name="loader-2" class="h-5 w-5 animate-spin mr-2" />
          <span>Cargando abogados...</span>
        </div>
      }

      <!-- ── Pipeline de matching (TF-IDF + similitud coseno) ─────────────── -->
      @if (selectedCaseId() && matchStage() < 4) {
        <div class="relative overflow-hidden rounded-2xl bg-slate-900 p-5 text-white lg:p-6">
          <div class="pointer-events-none absolute -top-20 -right-16 h-56 w-56 rounded-full bg-blue-600/25 blur-3xl"></div>
          <div class="pointer-events-none absolute -bottom-24 -left-10 h-56 w-56 rounded-full bg-cyan-500/15 blur-3xl"></div>
          <div class="relative">
            <div class="mb-4 flex items-center gap-3">
              <span class="flex h-9 w-9 items-center justify-center rounded-lg bg-gradient-to-br from-blue-500 to-cyan-500 text-white">
                <lucide-icon name="scale" class="h-4.5 w-4.5" />
              </span>
              <div>
                <p class="font-semibold">Buscando al abogado más compatible</p>
                <p class="font-mono text-[11px] text-slate-400">score = 0.7·cos(θ) + 0.3·desempeño · cos(θ) = (A·B) / (‖A‖·‖B‖)</p>
              </div>
            </div>

            @if (selectedCase(); as sc) {
              <div class="mb-4 flex flex-wrap gap-1.5">
                @if (sc.context?.contextCode) {
                  <span class="rounded-md bg-white/10 px-2 py-0.5 font-mono text-[11px] tracking-wider text-cyan-300 ring-1 ring-inset ring-white/10">{{ sc.context?.contextCode }}</span>
                }
                <span class="rounded-full bg-white/10 px-2.5 py-0.5 text-[11px] text-slate-200 ring-1 ring-inset ring-white/10">{{ sc.medicalSpecialty || sc.context?.medicalArea || 'Medicina General' }}</span>
                <span class="rounded-full bg-white/10 px-2.5 py-0.5 text-[11px] capitalize text-slate-200 ring-1 ring-inset ring-white/10">Prioridad {{ sc.priority }}</span>
              </div>
            }

            <div class="space-y-3">
              <div class="flex items-start gap-3">
                @if (matchStage() > 1) {
                  <span class="flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-emerald-400/15 text-emerald-300 ring-1 ring-inset ring-emerald-400/30"><lucide-icon name="check-circle-2" class="h-3.5 w-3.5" /></span>
                } @else {
                  <span class="flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-blue-400/15 text-blue-300 ring-1 ring-inset ring-blue-400/30"><lucide-icon name="loader-2" class="h-3.5 w-3.5 animate-spin" /></span>
                }
                <div [class]="matchStage() >= 1 ? 'opacity-100' : 'opacity-40'">
                  <p class="text-sm font-medium">Vectorización TF-IDF del caso</p>
                  <p class="text-[11px] text-slate-400">Los términos del caso se convierten en un vector ponderado por frecuencia</p>
                </div>
              </div>

              <div class="flex items-start gap-3">
                @if (matchStage() > 2) {
                  <span class="flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-emerald-400/15 text-emerald-300 ring-1 ring-inset ring-emerald-400/30"><lucide-icon name="check-circle-2" class="h-3.5 w-3.5" /></span>
                } @else if (matchStage() === 2) {
                  <span class="flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-blue-400/15 text-blue-300 ring-1 ring-inset ring-blue-400/30"><lucide-icon name="loader-2" class="h-3.5 w-3.5 animate-spin" /></span>
                } @else {
                  <span class="flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-white/5 text-slate-500 ring-1 ring-inset ring-white/10"><lucide-icon name="circle" class="h-3 w-3" /></span>
                }
                <div [class]="matchStage() >= 2 ? 'opacity-100' : 'opacity-40'">
                  <p class="text-sm font-medium">Similitud coseno contra {{ (lawyersQuery.data() ?? []).length }} abogados</p>
                  <p class="text-[11px] text-slate-400">El vector del caso se compara con las especialidades y áreas de cada perfil</p>
                </div>
              </div>

              <div class="flex items-start gap-3">
                @if (matchStage() > 3) {
                  <span class="flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-emerald-400/15 text-emerald-300 ring-1 ring-inset ring-emerald-400/30"><lucide-icon name="check-circle-2" class="h-3.5 w-3.5" /></span>
                } @else if (matchStage() === 3) {
                  <span class="flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-blue-400/15 text-blue-300 ring-1 ring-inset ring-blue-400/30"><lucide-icon name="loader-2" class="h-3.5 w-3.5 animate-spin" /></span>
                } @else {
                  <span class="flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-white/5 text-slate-500 ring-1 ring-inset ring-white/10"><lucide-icon name="circle" class="h-3 w-3" /></span>
                }
                <div [class]="matchStage() >= 3 ? 'opacity-100' : 'opacity-40'">
                  <p class="text-sm font-medium">Ranking por score compuesto</p>
                  <p class="text-[11px] text-slate-400">Similitud (70%) + desempeño verificable: rating, casos resueltos y experiencia (30%)</p>
                </div>
              </div>
            </div>
          </div>
        </div>
      }

      @if (showLawyerCards()) {
      <div class="grid md:grid-cols-2 gap-4">
        @for (lawyer of filteredLawyers(); track lawyer.id; let i = $index) {
          @let lawyerUserId = lawyer.userId ?? lawyer.id;
          @let matchScore = getMatchScore(lawyerUserId);
          @let matchReasons = getMatchReasons(lawyerUserId);
          @let alreadyRequested = hasActiveRequest(lawyerUserId);
          @let pendingId = pendingRequestId(lawyerUserId);
          @let isTopPick = i === 0 && matchScore !== undefined && matchScore > 0;

          <div [class]="cn('rounded-lg border p-5 space-y-4', isTopPick ? 'bg-amber-50 border-amber-400 ring-2 ring-amber-300 shadow-md shadow-amber-100' : 'bg-white border-slate-200')">

            @if (isTopPick) {
              <div class="flex items-center gap-1.5 text-xs font-bold text-amber-700 bg-amber-100 border border-amber-300 rounded-full px-3 py-1 w-fit">
                <lucide-icon name="trophy" class="h-3.5 w-3.5 text-amber-500" />
                Mejor match para tu caso
              </div>
            }

            <div class="flex items-start gap-4">
              <div [class]="cn('h-12 w-12 rounded-full flex items-center justify-center shrink-0 text-white font-bold text-sm', isTopPick ? 'bg-amber-600' : 'bg-slate-900')">
                {{ getInitials(lawyer.fullName ?? '') }}
              </div>
              <div class="flex-1 min-w-0">
                <div class="flex items-start justify-between gap-2">
                  <div>
                    <p class="font-semibold text-slate-900">{{ lawyer.fullName }}</p>
                    <p class="text-xs text-slate-500">CAB: {{ lawyer.cab }}</p>
                  </div>
                </div>
              </div>
            </div>

            @if (matchScore !== undefined && matchScore > 0) {
              <div>
                <div class="mb-1 flex items-center justify-between text-[11px]">
                  <span class="font-medium text-slate-400">Compatibilidad con tu caso</span>
                  <span class="font-bold text-slate-700">{{ matchScore }}%</span>
                </div>
                <div class="h-1.5 overflow-hidden rounded-full bg-slate-100">
                  <div class="h-full rounded-full bg-gradient-to-r from-blue-500 to-cyan-400 transition-all duration-700" [style.width.%]="matchScore"></div>
                </div>
              </div>
            }

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
              <div [class]="cn('rounded-md p-3', isTopPick ? 'bg-amber-100/60' : 'bg-slate-50')">
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
              [disabled]="!selectedCaseId() || alreadyRequested || contactMutation.isPending()"
            >
              @if (alreadyRequested) {
                <lucide-icon name="check-circle" class="h-4 w-4" />Solicitud enviada
              } @else {
                <lucide-icon name="send" class="h-4 w-4" />Solicitar contacto
              }
            </button>

            @if (pendingId) {
              <button
                appBtn
                variant="ghost"
                size="sm"
                class="w-full gap-2 text-red-600 hover:text-red-700 hover:bg-red-50"
                (click)="cancelRequest(pendingId)"
                [disabled]="cancelMutation.isPending()"
              >
                <lucide-icon name="x" class="h-4 w-4" />Cancelar solicitud
              </button>
            }
          </div>
        }
      </div>
      }

      @if (contactSuccess()) {
        <p class="text-sm text-emerald-700 bg-emerald-50 border border-emerald-200 rounded-md px-4 py-3">
          Solicitud enviada. El abogado verá el caso «{{ selectedCase()?.title }}» al aceptar.
        </p>
      }
      @if (contactError()) {
        <p class="text-sm text-red-700 bg-red-50 border border-red-200 rounded-md px-4 py-3">{{ contactError() }}</p>
      }

      <app-ml-advisory-note />
    </div>
  `,
})
export class DoctorLawyersComponent {
  private readonly auth = inject(AuthService);
  private readonly casesApi = inject(CasesApi);
  private readonly matchingApi = inject(MatchingApi);
  private readonly queryClient = injectQueryClient();
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly injector = inject(Injector);

  protected readonly search = signal('');
  protected readonly selectedCaseId = signal('');
  protected readonly contactSuccess = signal(false);
  protected readonly contactError = signal<string | null>(null);

  /** Etapa de la animación del matching: 1 vectorización · 2 coseno · 3 ranking · 4 listo. */
  protected readonly matchStage = signal(0);
  protected readonly showLawyerCards = computed(() => !this.selectedCaseId() || this.matchStage() >= 4);
  private matchTimers: ReturnType<typeof setTimeout>[] = [];
  private lastAnimatedCase = '';
  protected readonly getInitials = getInitials;
  protected readonly cn = cn;
  protected readonly asPriority = (p?: string) => (p ?? 'media') as CasePriority;

  private readonly userId = computed(() => this.auth.user()?.id ?? '');

  private readonly caseIdFromRoute = toSignal(
    this.route.queryParamMap.pipe(map((params) => params.get('caseId') ?? '')),
    { initialValue: '', injector: this.injector },
  );

  protected readonly openCasesQuery = injectQuery(() => ({
    queryKey: ['cases', 'open-for-lawyer', this.userId()],
    queryFn: () => this.casesApi.list({ doctorId: this.userId(), pageSize: 100 }),
    enabled: !!this.userId(),
  }));

  protected readonly openCases = computed(() =>
    (this.openCasesQuery.data()?.data ?? []).filter(
      (c) =>
        !c.lawyer &&
        !c.lawyerId &&
        (c.status === 'pendiente' || c.status === 'clasificada'),
    ),
  );

  protected readonly selectedCase = computed(() =>
    this.openCases().find((c) => c.id === this.selectedCaseId()),
  );

  constructor() {
    effect(() => {
      const fromRoute = this.caseIdFromRoute();
      const cases = this.openCases();
      if (fromRoute && cases.some((c) => c.id === fromRoute)) {
        this.selectedCaseId.set(fromRoute);
        return;
      }
      if (!this.selectedCaseId() && cases.length > 0) {
        this.selectedCaseId.set(cases[0].id ?? '');
      }
    });

    // Cada vez que cambia el caso seleccionado, corre la animación del matching.
    effect(() => {
      const caseId = this.selectedCaseId();
      if (!caseId || caseId === this.lastAnimatedCase) return;
      this.lastAnimatedCase = caseId;
      this.runMatchAnimation();
    });
  }

  private runMatchAnimation(): void {
    this.matchTimers.forEach(clearTimeout);
    this.matchTimers = [];
    this.matchStage.set(1);
    this.matchTimers.push(setTimeout(() => this.matchStage.set(2), 1000));
    this.matchTimers.push(setTimeout(() => this.matchStage.set(3), 2100));
    this.matchTimers.push(setTimeout(() => this.matchStage.set(4), 2900));
  }

  protected readonly lawyersQuery = injectQuery(() => ({
    queryKey: ['matching', 'lawyers'],
    queryFn: () => this.matchingApi.lawyers(),
  }));

  protected readonly recommendationsQuery = injectQuery(() => ({
    queryKey: ['matching', 'recommendations', this.userId(), this.selectedCaseId()],
    queryFn: () => this.matchingApi.recommendations(this.userId(), this.selectedCaseId() || undefined),
    enabled: !!this.userId() && !!this.selectedCaseId(),
  }));

  protected readonly contactRequestsQuery = injectQuery(() => ({
    queryKey: ['matching', 'contact-requests', { doctorId: this.userId() }],
    queryFn: () => this.matchingApi.contactRequests({ doctorId: this.userId() }),
    enabled: !!this.userId(),
  }));

  protected readonly filteredLawyers = computed(() => {
    const term = this.search().trim().toLowerCase();
    const lawyers = this.lawyersQuery.data() ?? [];
    const recs = this.recommendationsQuery.data()?.recommendations ?? [];
    const hasRecs = recs.length > 0;

    const scoreMap = new Map<string, number>();
    for (const rec of recs) {
      const id = rec.lawyer?.userId ?? rec.lawyer?.id;
      if (id && rec.score != null) scoreMap.set(id, rec.score);
    }

    let result = lawyers.filter((l) => {
      if (!hasRecs) return true;
      const id = l.userId ?? l.id;
      return id ? (scoreMap.get(id) ?? 0) > 0 : false;
    });

    result = [...result].sort((a, b) => {
      const aScore = scoreMap.get(a.userId ?? a.id ?? '') ?? 0;
      const bScore = scoreMap.get(b.userId ?? b.id ?? '') ?? 0;
      return bScore - aScore;
    });

    if (!term) return result;
    return result.filter(
      (l) =>
        (l.fullName ?? '').toLowerCase().includes(term) ||
        (l.specialties ?? []).some((s) => s.toLowerCase().includes(term)),
    );
  });

  protected onCaseSelected(caseId: string): void {
    this.selectedCaseId.set(caseId);
    this.contactSuccess.set(false);
    this.contactError.set(null);
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { caseId: caseId || null },
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });
  }

  protected getMatchScore(lawyerUserId?: string): number | undefined {
    return this.recommendationsQuery.data()?.recommendations?.find(
      (r) => (r.lawyer?.userId ?? r.lawyer?.id) === lawyerUserId,
    )?.score;
  }

  protected getMatchReasons(lawyerUserId?: string): string[] {
    return (
      this.recommendationsQuery.data()?.recommendations?.find(
        (r) => (r.lawyer?.userId ?? r.lawyer?.id) === lawyerUserId,
      )?.reasons ?? []
    );
  }

  protected hasActiveRequest(lawyerUserId?: string): boolean {
    return (this.contactRequestsQuery.data() ?? []).some(
      (r) => r.toLawyerId === lawyerUserId && (r.status === 'pendiente' || r.status === 'aceptado'),
    );
  }

  /** ID de la solicitud PENDIENTE con ese abogado (cancelable), si existe. */
  protected pendingRequestId(lawyerUserId?: string): string | undefined {
    return (this.contactRequestsQuery.data() ?? []).find(
      (r) => r.toLawyerId === lawyerUserId && r.status === 'pendiente',
    )?.id;
  }

  protected readonly contactMutation = injectMutation(() => ({
    mutationFn: (payload: { toLawyerUserId: string; caseId: string; caseTitle: string }) =>
      this.matchingApi.createContactRequest({
        fromDoctorId: this.userId(),
        toLawyerId: payload.toLawyerUserId,
        caseId: payload.caseId,
        message: `Hola, me gustaría contactarte para revisar el caso «${payload.caseTitle}».`,
      }),
    onSuccess: () => {
      this.contactSuccess.set(true);
      this.contactError.set(null);
      this.queryClient.invalidateQueries({ queryKey: ['matching', 'contact-requests'] });
    },
    onError: () => {
      this.contactSuccess.set(false);
      this.contactError.set('No se pudo enviar la solicitud. Verifica que no exista una pendiente con ese abogado.');
    },
  }));

  protected readonly cancelMutation = injectMutation(() => ({
    mutationFn: (requestId: string) => this.matchingApi.cancelContactRequest(requestId),
    onSuccess: () => {
      this.contactSuccess.set(false);
      this.contactError.set(null);
      this.queryClient.invalidateQueries({ queryKey: ['matching', 'contact-requests'] });
    },
    onError: () => {
      this.contactError.set('No se pudo cancelar la solicitud. Intenta de nuevo.');
    },
  }));

  protected cancelRequest(requestId?: string): void {
    if (!requestId) return;
    this.contactSuccess.set(false);
    this.contactError.set(null);
    this.cancelMutation.mutate(requestId);
  }

  protected sendContactRequest(lawyer: { id?: string; userId?: string; fullName?: string }): void {
    const caseId = this.selectedCaseId();
    const selected = this.selectedCase();
    const toLawyerUserId = lawyer.userId ?? lawyer.id;
    if (!toLawyerUserId || !caseId || !selected) return;
    this.contactSuccess.set(false);
    this.contactError.set(null);
    this.contactMutation.mutate({
      toLawyerUserId,
      caseId,
      caseTitle: selected.title ?? 'Caso',
    });
  }
}
