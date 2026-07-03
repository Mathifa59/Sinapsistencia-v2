import { Component, computed, inject, Injector, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { injectMutation, injectQuery, injectQueryClient } from '@tanstack/angular-query-experimental';
import { LucideAngularModule } from 'lucide-angular';
import { map } from 'rxjs';
import { AuthService } from '../../../core/auth/auth.service';
import { CasesApi, type CaseReportDto } from '../../../core/api/cases.api';
import { BtnDirective } from '../../ui/button.directive';
import { InputDirective, LabelDirective, TextareaDirective, SelectDirective } from '../../ui/field.directives';
import { CaseStatusBadgeComponent, CasePriorityBadgeComponent } from '../../ui/status-badges.component';
import { MlAdvisoryNoteComponent } from '../../ui/ml-advisory-note.component';
import {
  ModalComponent,
  ModalHeaderDirective,
  ModalTitleDirective,
  ModalDescriptionDirective,
  ModalFooterDirective,
} from '../../ui/modal.component';
import { formatDate, formatDateTime } from '../../utils/cn';
import {
  CASE_STATUS_LABELS,
  CASE_PRIORITY_LABELS,
  MEDICAL_SPECIALTIES,
  type CasePriority,
  type CaseStatus,
} from '../../constants';

/** Detalle completo de consulta compartido entre médico y abogado. */
@Component({
  selector: 'app-case-detail',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    LucideAngularModule,
    BtnDirective,
    InputDirective,
    LabelDirective,
    TextareaDirective,
    SelectDirective,
    CaseStatusBadgeComponent,
    CasePriorityBadgeComponent,
    MlAdvisoryNoteComponent,
    ModalComponent,
    ModalHeaderDirective,
    ModalTitleDirective,
    ModalDescriptionDirective,
    ModalFooterDirective,
  ],
  template: `
    @if (detailQuery.isLoading()) {
      <div class="flex items-center justify-center py-20 text-slate-400">
        <lucide-icon name="loader-2" class="h-6 w-6 animate-spin mr-2" />
        <span>Cargando consulta...</span>
      </div>
    } @else if (detailQuery.isError() || !caseData()) {
      <div class="flex flex-col items-center justify-center py-20 text-slate-400">
        <lucide-icon name="alert-triangle" class="h-10 w-10 mb-3" />
        <p class="font-medium">Consulta no encontrada</p>
        <a [routerLink]="backLink()" class="text-blue-600 text-sm mt-2 hover:underline">Volver a consultas</a>
      </div>
    } @else {
      @let c = caseData()!;
      @let detail = detailQuery.data()!;
      <div class="space-y-5 max-w-5xl">
        <app-ml-advisory-note />

        <div class="flex flex-wrap items-center gap-3">
          <a [routerLink]="backLink()">
            <button appBtn variant="outline" size="icon" class="h-8 w-8">
              <lucide-icon name="arrow-left" class="h-4 w-4" />
            </button>
          </a>
          <div class="flex-1 min-w-0">
            <h1 class="text-xl font-bold text-slate-900 truncate">{{ c.title }}</h1>
            <p class="text-sm text-slate-500">{{ c.context?.contextCode ?? ('Consulta #' + (c.id ?? '').slice(0, 8).toUpperCase()) }}</p>
          </div>
          <div class="flex flex-wrap gap-2">
            <app-case-status-badge [status]="asStatus(c.status)" />
            <app-case-priority-badge [priority]="asPriority(c.priority)" />
          </div>
        </div>

        @if (isDoctor()) {
          <div class="flex flex-wrap gap-2">
            @if (canEditCase()) {
              <button appBtn variant="outline" size="sm" class="gap-1.5" (click)="openEditModal()">
                <lucide-icon name="pencil" class="h-3.5 w-3.5" />Editar consulta
              </button>
            }
            @if (c.status === 'respondida') {
              <button appBtn variant="outline" size="sm" class="gap-1.5" (click)="handleClose()">
                <lucide-icon name="archive" class="h-3.5 w-3.5" />Cerrar consulta
              </button>
            }
            <button appBtn variant="outline" size="sm" class="gap-1.5" (click)="openEventModal()">
              <lucide-icon name="calendar-plus" class="h-3.5 w-3.5" />Registrar evento
            </button>
            <button appBtn variant="outline" size="sm" class="gap-1.5" (click)="generateReport()" [disabled]="reportLoading()">
              @if (reportLoading()) {
                <lucide-icon name="loader-2" class="h-3.5 w-3.5 animate-spin" />
              } @else {
                <lucide-icon name="file-text" class="h-3.5 w-3.5" />
              }
              Generar informe
            </button>
            @if (!c.lawyer) {
              <a [routerLink]="['/doctor/lawyers']" [queryParams]="{ caseId: c.id }">
                <button appBtn variant="primary" size="sm" class="gap-1.5">
                  <lucide-icon name="scale" class="h-3.5 w-3.5" />Buscar abogado
                </button>
              </a>
            }
          </div>
        }

        @if (isLawyer() && isAssignedLawyer() && c.status === 'asignada') {
          <div class="flex flex-wrap gap-2">
            <button appBtn variant="primary" size="sm" class="gap-1.5" (click)="startReview()" [disabled]="startReviewMutation.isPending()">
              @if (startReviewMutation.isPending()) {
                <lucide-icon name="loader-2" class="h-3.5 w-3.5 animate-spin" />
              } @else {
                <lucide-icon name="play" class="h-3.5 w-3.5" />
              }
              Iniciar revisión
            </button>
          </div>
        }

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

            @if (detail.classification) {
              @let cls = detail.classification;
              <div class="bg-white rounded-lg border border-slate-200 p-5">
                <h2 class="font-semibold text-slate-900 mb-3 flex items-center gap-2">
                  <lucide-icon name="cpu" class="h-4 w-4 text-blue-500" />
                  Clasificación ML
                </h2>
                <div class="grid sm:grid-cols-2 gap-3 text-sm">
                  @if (cls.caseType) {
                    <div><span class="text-slate-400 text-xs uppercase">Tipo de caso</span><p class="font-medium text-slate-800">{{ cls.caseType }}</p></div>
                  }
                  @if (cls.urgency) {
                    <div><span class="text-slate-400 text-xs uppercase">Urgencia</span><p class="font-medium text-slate-800">{{ cls.urgency }}</p></div>
                  }
                  @if (cls.complexity) {
                    <div><span class="text-slate-400 text-xs uppercase">Complejidad</span><p class="font-medium text-slate-800">{{ cls.complexity }}</p></div>
                  }
                  @if (cls.suggestedSpecialty) {
                    <div><span class="text-slate-400 text-xs uppercase">Especialidad sugerida</span><p class="font-medium text-slate-800">{{ cls.suggestedSpecialty }}</p></div>
                  }
                  @if (cls.confidence != null) {
                    <div><span class="text-slate-400 text-xs uppercase">Confianza</span><p class="font-medium text-slate-800">{{ Math.round(cls.confidence * 100) }}%</p></div>
                  }
                  @if (cls.modelVersion) {
                    <div><span class="text-slate-400 text-xs uppercase">Modelo</span><p class="font-medium text-slate-800">v{{ cls.modelVersion }}</p></div>
                  }
                </div>
              </div>
            }

            @if (c.priorityJustification) {
              <div class="bg-white rounded-lg border border-slate-200 p-5">
                <h2 class="font-semibold text-slate-900 mb-2">Justificación de prioridad</h2>
                <p class="text-sm text-slate-600 leading-relaxed">{{ c.priorityJustification }}</p>
              </div>
            }

            <div class="bg-white rounded-lg border border-slate-200 p-5">
              <h2 class="font-semibold text-slate-900 mb-4 flex items-center gap-2">
                <lucide-icon name="message-square" class="h-4 w-4 text-slate-400" />
                Respuestas legales
              </h2>
              @if ((detail.responses ?? []).length === 0) {
                <p class="text-sm text-slate-400 italic text-center py-4">Aún no hay respuestas legales</p>
              } @else {
                <div class="space-y-4">
                  @for (resp of detail.responses ?? []; track resp.id) {
                    <div class="border border-slate-100 rounded-lg p-4 space-y-2">
                      <div class="flex items-start justify-between gap-2">
                        <div>
                          <p class="font-medium text-slate-800 text-sm">{{ resp.lawyerName ?? 'Abogado' }}</p>
                          <p class="text-xs text-slate-400">{{ formatDateTime(resp.createdAt ?? '') }}</p>
                        </div>
                        @if (resp.isReviewed) {
                          <span class="text-xs font-medium text-emerald-700 bg-emerald-50 border border-emerald-100 px-2 py-0.5 rounded-full">Revisada</span>
                        } @else if (isDoctor()) {
                          <button appBtn variant="outline" size="sm" class="text-xs" (click)="reviewResponse(resp.id!)" [disabled]="reviewMutation.isPending()">
                            Marcar como revisada
                          </button>
                        }
                      </div>
                      <p class="text-sm text-slate-600">{{ resp.content }}</p>
                      @if (resp.recommendations) {
                        <div class="bg-blue-50 rounded-md px-3 py-2 text-sm text-blue-800">
                          <p class="text-xs font-semibold uppercase mb-1">Recomendaciones</p>
                          {{ resp.recommendations }}
                        </div>
                      }
                      @if (resp.observations) {
                        <p class="text-xs text-slate-500"><span class="font-semibold">Observaciones:</span> {{ resp.observations }}</p>
                      }
                    </div>
                  }
                </div>
              }
            </div>

            @if (isLawyer() && isAssignedLawyer() && c.status === 'en_revision') {
              <div class="bg-white rounded-lg border border-slate-200 p-5">
                <h2 class="font-semibold text-slate-900 mb-4 flex items-center gap-2">
                  <lucide-icon name="pen-line" class="h-4 w-4 text-slate-400" />
                  Agregar respuesta legal
                </h2>
                <form [formGroup]="responseForm" (ngSubmit)="submitResponse()" class="space-y-3">
                  <div class="space-y-1.5">
                    <label appLabel>Contenido de la respuesta *</label>
                    <textarea appTextarea rows="4" placeholder="Redacta tu análisis y respuesta legal..." formControlName="content"></textarea>
                  </div>
                  <div class="space-y-1.5">
                    <label appLabel>Recomendaciones</label>
                    <textarea appTextarea rows="2" placeholder="Recomendaciones para el médico..." formControlName="recommendations"></textarea>
                  </div>
                  <div class="space-y-1.5">
                    <label appLabel>Observaciones</label>
                    <textarea appTextarea rows="2" placeholder="Observaciones adicionales..." formControlName="observations"></textarea>
                  </div>
                  @if (responseError()) {
                    <p class="text-xs text-red-600">{{ responseError() }}</p>
                  }
                  <button appBtn type="submit" variant="primary" size="sm" class="gap-2" [disabled]="responseMutation.isPending()">
                    @if (responseMutation.isPending()) {
                      <lucide-icon name="loader-2" class="h-4 w-4 animate-spin" />
                    } @else {
                      <lucide-icon name="send" class="h-4 w-4" />
                    }
                    Enviar respuesta
                  </button>
                </form>
              </div>
            }
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
                <p class="text-sm text-slate-400 italic">Sin abogado asignado</p>
              }
            </div>

            <div class="bg-white rounded-lg border border-slate-200 p-5">
              <div class="flex items-center justify-between mb-4 gap-3 flex-wrap">
                <h3 class="font-semibold text-slate-900 flex items-center gap-2">
                  <lucide-icon name="git-branch" class="h-4 w-4 text-slate-400" />
                  Línea de tiempo
                </h3>
                @if (timelineKinds().length > 0) {
                  <select appSelect class="text-xs py-1 h-auto w-auto min-w-[140px]"
                    [value]="timelineFilter()"
                    (change)="timelineFilter.set($any($event.target).value)">
                    <option value="">Todos los eventos</option>
                    @for (kind of timelineKinds(); track kind) {
                      <option [value]="kind">{{ kind }}</option>
                    }
                  </select>
                }
              </div>
              @if ((detail.timeline ?? []).length === 0) {
                <p class="text-sm text-slate-400 italic text-center py-2">Sin eventos registrados</p>
              } @else if (filteredTimeline().length === 0) {
                <p class="text-sm text-slate-400 italic text-center py-2">Sin eventos para este filtro</p>
              } @else {
                <div class="space-y-3">
                  @for (entry of filteredTimeline(); track entry.id) {
                    <div class="relative pl-4 border-l-2 border-slate-200">
                      <p class="text-sm font-medium text-slate-800">{{ entry.title }}</p>
                      @if (entry.description) {
                        <p class="text-xs text-slate-500 mt-0.5">{{ entry.description }}</p>
                      }
                      <p class="text-xs text-slate-400 mt-1">
                        {{ formatDateTime(entry.occurredAt ?? '') }}
                        @if (entry.actorName) { · {{ entry.actorName }} }
                      </p>
                    </div>
                  }
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

    <!-- Modal editar -->
    <app-modal [open]="editModalOpen()" (close)="editModalOpen.set(false)" class="max-w-lg">
      <div appModalHeader>
        <h2 appModalTitle>Editar consulta</h2>
        <p appModalDescription>Modifica los datos mientras la consulta no tenga abogado asignado.</p>
      </div>
      <form [formGroup]="editForm" (ngSubmit)="submitEdit()" class="space-y-4">
        <div class="space-y-1.5">
          <label appLabel>Título *</label>
          <input appInput formControlName="title" />
        </div>
        <div class="space-y-1.5">
          <label appLabel>Descripción *</label>
          <textarea appTextarea rows="3" formControlName="description"></textarea>
        </div>
        <div class="grid grid-cols-2 gap-4">
          <div class="space-y-1.5">
            <label appLabel>Prioridad</label>
            <select appSelect formControlName="priority">
              <option value="baja">Baja</option>
              <option value="media">Media</option>
              <option value="alta">Alta</option>
              <option value="critica">Crítica</option>
            </select>
          </div>
          <div class="space-y-1.5">
            <label appLabel>Especialidad</label>
            <select appSelect formControlName="medicalSpecialty">
              <option value="">Sin especificar</option>
              @for (s of specialties; track s) {
                <option [value]="s">{{ s }}</option>
              }
            </select>
          </div>
        </div>
        <div class="space-y-1.5">
          <label appLabel>Notas</label>
          <textarea appTextarea rows="2" formControlName="notes"></textarea>
        </div>
        @if (editError()) {
          <p class="text-xs text-red-600">{{ editError() }}</p>
        }
        <div appModalFooter>
          <button appBtn type="button" variant="outline" (click)="editModalOpen.set(false)">Cancelar</button>
          <button appBtn type="submit" variant="primary" [disabled]="editMutation.isPending()">Guardar</button>
        </div>
      </form>
    </app-modal>

    <!-- Modal evento -->
    <app-modal [open]="eventModalOpen()" (close)="eventModalOpen.set(false)" class="max-w-md">
      <div appModalHeader>
        <h2 appModalTitle>Registrar evento</h2>
        <p appModalDescription>Agrega un hito a la línea de tiempo de la consulta.</p>
      </div>
      <form [formGroup]="eventForm" (ngSubmit)="submitEvent()" class="space-y-4">
        <div class="space-y-1.5">
          <label appLabel>Fecha del evento *</label>
          <input appInput type="date" formControlName="eventDate" />
        </div>
        <div class="space-y-1.5">
          <label appLabel>Tipo de evento *</label>
          <input appInput formControlName="eventType" placeholder="Ej: Audiencia, revisión documental" />
        </div>
        <div class="space-y-1.5">
          <label appLabel>Descripción</label>
          <textarea appTextarea rows="3" formControlName="description"></textarea>
        </div>
        @if (eventError()) {
          <p class="text-xs text-red-600">{{ eventError() }}</p>
        }
        <div appModalFooter>
          <button appBtn type="button" variant="outline" (click)="eventModalOpen.set(false)">Cancelar</button>
          <button appBtn type="submit" variant="primary" [disabled]="eventMutation.isPending()">Registrar</button>
        </div>
      </form>
    </app-modal>
  `,
})
export class CaseDetailComponent {
  private readonly route = inject(ActivatedRoute);
  private readonly auth = inject(AuthService);
  private readonly casesApi = inject(CasesApi);
  private readonly queryClient = injectQueryClient();
  private readonly fb = inject(FormBuilder);
  private readonly injector = inject(Injector);

  protected readonly specialties = MEDICAL_SPECIALTIES;
  protected readonly Math = Math;
  protected readonly formatDate = formatDate;
  protected readonly formatDateTime = formatDateTime;
  protected readonly asStatus = (s?: string) => (s ?? 'pendiente') as CaseStatus;
  protected readonly asPriority = (p?: string) => (p ?? 'media') as CasePriority;

  protected readonly editModalOpen = signal(false);
  protected readonly eventModalOpen = signal(false);
  protected readonly editError = signal<string | null>(null);
  protected readonly eventError = signal<string | null>(null);
  protected readonly responseError = signal<string | null>(null);
  protected readonly reportLoading = signal(false);
  protected readonly timelineFilter = signal<string>('');

  private readonly caseId = toSignal(
    this.route.paramMap.pipe(map((params) => params.get('id') ?? '')),
    { initialValue: '', injector: this.injector },
  );

  protected readonly detailQuery = injectQuery(() => ({
    queryKey: ['cases', 'detail', this.caseId()],
    queryFn: () => this.casesApi.getDetail(this.caseId()),
    enabled: !!this.caseId(),
  }));

  protected readonly caseData = computed(() => this.detailQuery.data()?.caseData);

  protected readonly timelineKinds = computed(() => {
    const entries = this.detailQuery.data()?.timeline ?? [];
    return [...new Set(entries.map((e) => e.kind).filter((k): k is string => !!k))];
  });

  protected readonly filteredTimeline = computed(() => {
    const entries = this.detailQuery.data()?.timeline ?? [];
    const filter = this.timelineFilter();
    return filter ? entries.filter((e) => e.kind === filter) : entries;
  });

  protected readonly role = this.auth.role;
  protected readonly isDoctor = computed(() => this.role() === 'doctor');
  protected readonly isLawyer = computed(() => this.role() === 'lawyer');

  protected readonly backLink = computed(() => {
    const r = this.role();
    if (r === 'lawyer') return '/lawyer/cases';
    return '/doctor/cases';
  });

  protected readonly isAssignedLawyer = computed(() => {
    const c = this.caseData();
    const userId = this.auth.user()?.id;
    return !!c?.lawyerId && c.lawyerId === userId;
  });

  protected readonly canEditCase = computed(() => {
    const c = this.caseData();
    if (!c || c.lawyer || c.lawyerId) return false;
    const status = c.status;
    return status === 'pendiente' || status === 'clasificada';
  });

  protected readonly editForm = this.fb.nonNullable.group({
    title: ['', [Validators.required, Validators.minLength(3)]],
    description: ['', [Validators.required, Validators.minLength(10)]],
    priority: ['media', Validators.required],
    medicalSpecialty: [''],
    notes: [''],
  });

  protected readonly eventForm = this.fb.nonNullable.group({
    eventDate: ['', Validators.required],
    eventType: ['', Validators.required],
    description: [''],
  });

  protected readonly responseForm = this.fb.nonNullable.group({
    content: ['', [Validators.required, Validators.minLength(10)]],
    recommendations: [''],
    observations: [''],
  });

  protected readonly editMutation = injectMutation(() => ({
    mutationFn: () => {
      const v = this.editForm.getRawValue();
      return this.casesApi.edit(this.caseId(), {
        title: v.title.trim(),
        description: v.description.trim(),
        priority: v.priority,
        medicalSpecialty: v.medicalSpecialty || undefined,
        notes: v.notes.trim() || undefined,
      });
    },
    onSuccess: () => {
      this.queryClient.invalidateQueries({ queryKey: ['cases'] });
      this.editModalOpen.set(false);
      this.editError.set(null);
    },
    onError: () => this.editError.set('No se pudo guardar la consulta.'),
  }));

  protected readonly eventMutation = injectMutation(() => ({
    mutationFn: () => {
      const v = this.eventForm.getRawValue();
      return this.casesApi.addEvent(this.caseId(), {
        eventDate: v.eventDate,
        eventType: v.eventType.trim(),
        description: v.description.trim() || undefined,
      });
    },
    onSuccess: () => {
      this.queryClient.invalidateQueries({ queryKey: ['cases', 'detail', this.caseId()] });
      this.eventModalOpen.set(false);
      this.eventForm.reset();
      this.eventError.set(null);
    },
    onError: () => this.eventError.set('No se pudo registrar el evento.'),
  }));

  protected readonly responseMutation = injectMutation(() => ({
    mutationFn: () => {
      const v = this.responseForm.getRawValue();
      return this.casesApi.addResponse(this.caseId(), {
        content: v.content.trim(),
        recommendations: v.recommendations.trim() || undefined,
        observations: v.observations.trim() || undefined,
      });
    },
    onSuccess: () => {
      this.queryClient.invalidateQueries({ queryKey: ['cases', 'detail', this.caseId()] });
      this.responseForm.reset();
      this.responseError.set(null);
    },
    onError: () => this.responseError.set('No se pudo enviar la respuesta.'),
  }));

  protected readonly reviewMutation = injectMutation(() => ({
    mutationFn: (responseId: string) => this.casesApi.reviewResponse(this.caseId(), responseId),
    onSuccess: () => this.queryClient.invalidateQueries({ queryKey: ['cases', 'detail', this.caseId()] }),
  }));

  protected readonly startReviewMutation = injectMutation(() => ({
    mutationFn: () => this.casesApi.startReview(this.caseId()),
    onSuccess: () => this.queryClient.invalidateQueries({ queryKey: ['cases'] }),
  }));

  protected openEditModal(): void {
    const c = this.caseData();
    if (!c) return;
    this.editForm.reset({
      title: c.title ?? '',
      description: c.description ?? '',
      priority: c.priority ?? 'media',
      medicalSpecialty: c.medicalSpecialty ?? '',
      notes: c.notes ?? '',
    });
    this.editError.set(null);
    this.editModalOpen.set(true);
  }

  protected openEventModal(): void {
    this.eventError.set(null);
    this.eventModalOpen.set(true);
  }

  protected submitEdit(): void {
    if (this.editForm.invalid) {
      this.editForm.markAllAsTouched();
      return;
    }
    this.editMutation.mutate();
  }

  protected submitEvent(): void {
    if (this.eventForm.invalid) {
      this.eventForm.markAllAsTouched();
      return;
    }
    this.eventMutation.mutate();
  }

  protected submitResponse(): void {
    if (this.responseForm.invalid) {
      this.responseForm.markAllAsTouched();
      return;
    }
    this.responseMutation.mutate();
  }

  protected reviewResponse(responseId: string): void {
    this.reviewMutation.mutate(responseId);
  }

  protected startReview(): void {
    this.startReviewMutation.mutate();
  }

  protected handleClose(): void {
    const reason = window.prompt('Indica el motivo del cierre de la consulta:');
    if (!reason?.trim()) return;
    this.casesApi.close(this.caseId(), reason.trim()).then(() => {
      this.queryClient.invalidateQueries({ queryKey: ['cases'] });
    });
  }

  protected async generateReport(): Promise<void> {
    this.reportLoading.set(true);
    try {
      const report = await this.casesApi.getReport(this.caseId());
      this.openPrintWindow(report);
    } finally {
      this.reportLoading.set(false);
    }
  }

  private openPrintWindow(report: CaseReportDto): void {
    const c = report.caseData;
    const html = `
      <!DOCTYPE html><html><head><meta charset="utf-8"><title>Informe — ${c?.title ?? 'Consulta'}</title>
      <style>body{font-family:system-ui,sans-serif;padding:2rem;color:#1e293b;max-width:800px;margin:0 auto}
      h1{font-size:1.5rem;margin-bottom:0.25rem}h2{font-size:1rem;margin-top:1.5rem;border-bottom:1px solid #e2e8f0;padding-bottom:0.25rem}
      p,li{font-size:0.875rem;line-height:1.6}.meta{color:#64748b;font-size:0.75rem}.badge{display:inline-block;padding:2px 8px;border-radius:4px;background:#f1f5f9;font-size:0.75rem}
      </style></head><body>
      <h1>${c?.title ?? 'Consulta'}</h1>
      <p class="meta">Generado: ${formatDateTime(report.generatedAt ?? new Date().toISOString())}</p>
      <p><span class="badge">${CASE_STATUS_LABELS[this.asStatus(c?.status)]}</span>
      <span class="badge">${CASE_PRIORITY_LABELS[this.asPriority(c?.priority)]}</span></p>
      <h2>Descripción</h2><p>${c?.description ?? '—'}</p>
      ${c?.priorityJustification ? `<h2>Justificación de prioridad</h2><p>${c.priorityJustification}</p>` : ''}
      ${report.classification ? `<h2>Clasificación ML</h2><p>Tipo: ${report.classification.caseType ?? '—'} · Urgencia: ${report.classification.urgency ?? '—'} · Complejidad: ${report.classification.complexity ?? '—'}</p>` : ''}
      ${(report.responses ?? []).length ? `<h2>Respuestas legales</h2>${(report.responses ?? []).map((r) => `<div style="margin-bottom:1rem;border:1px solid #e2e8f0;padding:0.75rem;border-radius:6px"><strong>${r.lawyerName ?? 'Abogado'}</strong><p>${r.content ?? ''}</p></div>`).join('')}` : ''}
      ${(report.timeline ?? []).length ? `<h2>Línea de tiempo</h2><ul>${(report.timeline ?? []).map((t) => `<li><strong>${t.title}</strong> — ${formatDateTime(t.occurredAt ?? '')}</li>`).join('')}</ul>` : ''}
      ${report.advisoryNote ? `<p style="margin-top:2rem;font-size:0.75rem;color:#92400e;background:#fffbeb;padding:0.75rem;border-radius:6px">${report.advisoryNote}</p>` : ''}
      </body></html>`;
    const win = window.open('', '_blank');
    if (win) {
      win.document.write(html);
      win.document.close();
      win.focus();
      win.print();
    }
  }
}
