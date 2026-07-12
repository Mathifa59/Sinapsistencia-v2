import { Component, computed, inject, Injector, signal, viewChild } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { injectMutation, injectQuery, injectQueryClient } from '@tanstack/angular-query-experimental';
import { LucideAngularModule } from 'lucide-angular';
import { map } from 'rxjs';
import { AuthService } from '../../../core/auth/auth.service';
import { CasesApi, type CaseReportDto } from '../../../core/api/cases.api';
import { BtnDirective } from '../../ui/button.directive';
import { ReasonModalComponent } from '../../ui/reason-modal.component';
import { InputDirective, LabelDirective, TextareaDirective, SelectDirective } from '../../ui/field.directives';
import {
  ModalComponent,
  ModalHeaderDirective,
  ModalTitleDirective,
  ModalDescriptionDirective,
  ModalFooterDirective,
} from '../../ui/modal.component';
import { formatDate, formatDateTime, getInitials } from '../../utils/cn';
import {
  CASE_STATUS_LABELS,
  CASE_PRIORITY_LABELS,
  MEDICAL_SPECIALTIES,
  type CasePriority,
  type CaseStatus,
} from '../../constants';

/** Punto de color del chip de estado (sobre el hero oscuro). */
const STATUS_DOTS: Record<CaseStatus, string> = {
  pendiente: 'bg-slate-400',
  clasificada: 'bg-blue-400',
  asignada: 'bg-cyan-400',
  en_revision: 'bg-amber-400 animate-pulse',
  respondida: 'bg-emerald-400',
  cerrada: 'bg-slate-500',
};

/** Punto de color del chip de prioridad (sobre el hero oscuro). */
const PRIORITY_DOTS: Record<CasePriority, string> = {
  baja: 'bg-emerald-400',
  media: 'bg-sky-400',
  alta: 'bg-amber-400',
  critica: 'bg-red-400',
};

/** Detalle completo de caso compartido entre médico y abogado. */
@Component({
  selector: 'app-case-detail',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    LucideAngularModule,
    BtnDirective,
    ReasonModalComponent,
    InputDirective,
    LabelDirective,
    TextareaDirective,
    SelectDirective,
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
        <span>Cargando caso...</span>
      </div>
    } @else if (detailQuery.isError() || !caseData()) {
      <div class="flex flex-col items-center justify-center py-20 text-slate-400">
        <lucide-icon name="alert-triangle" class="h-10 w-10 mb-3" />
        <p class="font-medium">Caso no encontrado</p>
        <a [routerLink]="backLink()" class="text-blue-600 text-sm mt-2 hover:underline">Volver a casos</a>
      </div>
    } @else {
      @let c = caseData()!;
      @let detail = detailQuery.data()!;
      <div class="space-y-5">
        <!-- ── Hero del caso ─────────────────────────────────────────────── -->
        <div class="relative overflow-hidden rounded-2xl bg-slate-900 text-white shadow-xl shadow-slate-900/10">
          <div class="pointer-events-none absolute -top-24 -right-20 h-72 w-72 rounded-full bg-blue-600/25 blur-3xl"></div>
          <div class="pointer-events-none absolute -bottom-32 left-1/4 h-72 w-72 rounded-full bg-cyan-500/15 blur-3xl"></div>

          <div class="relative p-5 lg:p-7">
            <div class="flex items-start gap-4">
              <a
                [routerLink]="backLink()"
                class="group mt-1 inline-flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-white/10 ring-1 ring-inset ring-white/15 transition hover:bg-white/20"
              >
                <lucide-icon name="arrow-left" class="h-4 w-4 text-slate-200 transition-transform group-hover:-translate-x-0.5" />
              </a>
              <div class="min-w-0 flex-1">
                <div class="flex flex-wrap items-center gap-2">
                  <span class="rounded-md bg-white/10 px-2 py-0.5 font-mono text-[11px] tracking-widest text-cyan-300 ring-1 ring-inset ring-white/10">
                    {{ caseCode(c) }}
                  </span>
                  <span class="inline-flex items-center gap-1.5 rounded-full bg-white/10 px-3 py-1 text-xs font-medium text-slate-100 ring-1 ring-inset ring-white/15">
                    <span class="h-1.5 w-1.5 rounded-full" [class]="statusDots[asStatus(c.status)]"></span>
                    {{ statusLabels[asStatus(c.status)] }}
                  </span>
                  <span class="inline-flex items-center gap-1.5 rounded-full bg-white/10 px-3 py-1 text-xs font-medium text-slate-100 ring-1 ring-inset ring-white/15">
                    <span class="h-1.5 w-1.5 rounded-full" [class]="priorityDots[asPriority(c.priority)]"></span>
                    Prioridad {{ priorityLabels[asPriority(c.priority)] }}
                  </span>
                </div>
                <h1 class="mt-3 text-2xl font-bold tracking-tight break-words lg:text-3xl">{{ c.title }}</h1>
                <div class="mt-3 flex flex-wrap items-center gap-x-5 gap-y-1.5 text-xs text-slate-400">
                  @if (c.medicalSpecialty || c.context?.medicalArea) {
                    <span class="flex items-center gap-1.5">
                      <lucide-icon name="stethoscope" class="h-3.5 w-3.5" />
                      {{ c.medicalSpecialty || c.context?.medicalArea }}
                    </span>
                  }
                  <span class="flex items-center gap-1.5">
                    <lucide-icon name="calendar" class="h-3.5 w-3.5" />
                    Creado {{ formatDate(c.createdAt ?? '') }}
                  </span>
                  <span class="flex items-center gap-1.5">
                    <lucide-icon name="clock" class="h-3.5 w-3.5" />
                    Actualizado {{ formatDateTime(c.updatedAt ?? '') }}
                  </span>
                  @if (c.lawyer) {
                    <span class="flex items-center gap-1.5 text-cyan-300/90">
                      <lucide-icon name="scale" class="h-3.5 w-3.5" />
                      {{ c.lawyer.fullName }}
                    </span>
                  }
                </div>
              </div>
            </div>

            @if (isDoctor() || (isLawyer() && isAssignedLawyer() && c.status === 'asignada')) {
              <div class="mt-5 flex flex-wrap items-center gap-2 border-t border-white/10 pt-4">
                @if (isDoctor()) {
                  @if (canEditCase()) {
                    <button type="button" [class]="heroGhostBtn" (click)="openEditModal()">
                      <lucide-icon name="pencil" class="h-3.5 w-3.5" />Editar caso
                    </button>
                  }
                  <button type="button" [class]="heroGhostBtn" (click)="openEventModal()">
                    <lucide-icon name="calendar-plus" class="h-3.5 w-3.5" />Registrar evento
                  </button>
                  <button type="button" [class]="heroGhostBtn" (click)="generateReport()" [disabled]="reportLoading()">
                    @if (reportLoading()) {
                      <lucide-icon name="loader-2" class="h-3.5 w-3.5 animate-spin" />
                    } @else {
                      <lucide-icon name="file-text" class="h-3.5 w-3.5" />
                    }
                    Generar informe
                  </button>
                  @if (c.status === 'respondida') {
                    <button type="button" [class]="heroGhostBtn" (click)="handleClose()">
                      <lucide-icon name="archive" class="h-3.5 w-3.5" />Cerrar caso
                    </button>
                  }
                  @if (!c.lawyer) {
                    <a [routerLink]="['/doctor/lawyers']" [queryParams]="{ caseId: c.id }" [class]="heroPrimaryBtn">
                      <lucide-icon name="scale" class="h-3.5 w-3.5" />Buscar abogado
                    </a>
                  }
                }
                @if (isLawyer() && isAssignedLawyer() && c.status === 'asignada') {
                  <button type="button" [class]="heroPrimaryBtn" (click)="startReview()" [disabled]="startReviewMutation.isPending()">
                    @if (startReviewMutation.isPending()) {
                      <lucide-icon name="loader-2" class="h-3.5 w-3.5 animate-spin" />
                    } @else {
                      <lucide-icon name="play" class="h-3.5 w-3.5" />
                    }
                    Iniciar revisión
                  </button>
                }
              </div>
            }
          </div>
        </div>

        <!-- ── Contenido ─────────────────────────────────────────────────── -->
        <div class="grid gap-5 lg:grid-cols-3">
          <div class="space-y-5 lg:col-span-2 min-w-0">
            <!-- Descripción -->
            <div class="rounded-xl border border-slate-200 bg-white p-5">
              <h2 class="mb-3 flex items-center gap-2.5 font-semibold text-slate-900">
                <span class="flex h-8 w-8 items-center justify-center rounded-lg bg-slate-100 text-slate-500">
                  <lucide-icon name="file-text" class="h-4 w-4" />
                </span>
                Descripción del caso
              </h2>
              <p class="text-sm leading-relaxed text-slate-600">{{ c.description }}</p>
              @if (c.priorityJustification) {
                <div class="mt-4 rounded-lg border-l-2 border-amber-400 bg-amber-50/70 px-4 py-3">
                  <p class="mb-1 text-[11px] font-semibold uppercase tracking-wider text-amber-700">Justificación de prioridad</p>
                  <p class="text-sm leading-relaxed text-amber-900/80">{{ c.priorityJustification }}</p>
                </div>
              }
              @if (c.notes) {
                <div class="mt-4 border-t border-slate-100 pt-4">
                  <p class="mb-1 text-[11px] font-semibold uppercase tracking-wider text-slate-400">Notas internas</p>
                  <p class="text-sm text-slate-600">{{ c.notes }}</p>
                </div>
              }
            </div>

            <!-- Clasificación del modelo -->
            @if (detail.classification) {
              @let cls = detail.classification;
              <div class="rounded-xl border border-slate-200 bg-white p-5">
                <div class="mb-4 flex flex-wrap items-center justify-between gap-2">
                  <h2 class="flex items-center gap-2.5 font-semibold text-slate-900">
                    <span class="flex h-8 w-8 items-center justify-center rounded-lg bg-gradient-to-br from-blue-500 to-cyan-500 text-white">
                      <lucide-icon name="cpu" class="h-4 w-4" />
                    </span>
                    Clasificación del modelo
                  </h2>
                  @if (cls.modelVersion) {
                    <span class="rounded-full bg-slate-100 px-2.5 py-0.5 font-mono text-[11px] text-slate-500">IA · v{{ cls.modelVersion }}</span>
                  }
                </div>
                <div class="grid grid-cols-2 gap-3 lg:grid-cols-4">
                  @if (cls.caseType) {
                    <div class="rounded-lg border border-slate-100 bg-slate-50 px-3 py-2.5">
                      <p class="text-[11px] uppercase tracking-wider text-slate-400">Tipo de caso</p>
                      <p class="mt-0.5 text-sm font-semibold capitalize text-slate-800">{{ cls.caseType }}</p>
                    </div>
                  }
                  @if (cls.urgency) {
                    <div class="rounded-lg border border-slate-100 bg-slate-50 px-3 py-2.5">
                      <p class="text-[11px] uppercase tracking-wider text-slate-400">Urgencia</p>
                      <p class="mt-0.5 text-sm font-semibold capitalize text-slate-800">{{ cls.urgency }}</p>
                    </div>
                  }
                  @if (cls.complexity) {
                    <div class="rounded-lg border border-slate-100 bg-slate-50 px-3 py-2.5">
                      <p class="text-[11px] uppercase tracking-wider text-slate-400">Complejidad</p>
                      <p class="mt-0.5 text-sm font-semibold capitalize text-slate-800">{{ cls.complexity }}</p>
                    </div>
                  }
                  @if (cls.suggestedSpecialty) {
                    <div class="rounded-lg border border-slate-100 bg-slate-50 px-3 py-2.5">
                      <p class="text-[11px] uppercase tracking-wider text-slate-400">Especialidad sugerida</p>
                      <p class="mt-0.5 text-sm font-semibold text-slate-800">{{ cls.suggestedSpecialty }}</p>
                    </div>
                  }
                </div>
                @if (cls.confidence != null) {
                  <div class="mt-4">
                    <div class="mb-1.5 flex items-center justify-between text-xs">
                      <span class="font-medium text-slate-500">Confianza del modelo</span>
                      <span class="font-bold text-slate-800">{{ Math.round(cls.confidence * 100) }}%</span>
                    </div>
                    <div class="h-2 overflow-hidden rounded-full bg-slate-100">
                      <div
                        class="h-full rounded-full bg-gradient-to-r from-blue-500 to-cyan-400 transition-all"
                        [style.width.%]="Math.round(cls.confidence * 100)"
                      ></div>
                    </div>
                  </div>
                }
                <p class="mt-4 flex items-start gap-1.5 border-t border-slate-100 pt-3 text-[11px] leading-relaxed text-slate-400">
                  <lucide-icon name="info" class="mt-px h-3.5 w-3.5 shrink-0" />
                  {{ advisoryNote }}
                </p>
              </div>
            }

            <!-- Respuestas legales -->
            <div class="rounded-xl border border-slate-200 bg-white p-5">
              <div class="mb-4 flex items-center justify-between gap-2">
                <h2 class="flex items-center gap-2.5 font-semibold text-slate-900">
                  <span class="flex h-8 w-8 items-center justify-center rounded-lg bg-slate-100 text-slate-500">
                    <lucide-icon name="message-square" class="h-4 w-4" />
                  </span>
                  Respuestas legales
                </h2>
                @if ((detail.responses ?? []).length > 0) {
                  <span class="rounded-full bg-slate-100 px-2.5 py-0.5 text-[11px] font-semibold text-slate-500">
                    {{ (detail.responses ?? []).length }}
                  </span>
                }
              </div>
              @if ((detail.responses ?? []).length === 0) {
                <div class="rounded-lg border border-dashed border-slate-200 py-8 text-center">
                  <lucide-icon name="message-square" class="mx-auto mb-2 h-8 w-8 text-slate-300" />
                  <p class="text-sm text-slate-400">Aún no hay respuestas legales</p>
                </div>
              } @else {
                <div class="space-y-4">
                  @for (resp of detail.responses ?? []; track resp.id) {
                    <div class="overflow-hidden rounded-xl border border-slate-200">
                      <div class="flex flex-wrap items-center justify-between gap-3 border-b border-slate-100 bg-slate-50/70 px-4 py-3">
                        <div class="flex items-center gap-3">
                          <div class="flex h-9 w-9 items-center justify-center rounded-full bg-slate-900 text-xs font-bold text-white">
                            {{ getInitials(resp.lawyerName ?? 'AB') }}
                          </div>
                          <div>
                            <p class="text-sm font-semibold text-slate-800">{{ resp.lawyerName ?? 'Abogado' }}</p>
                            <p class="text-[11px] text-slate-400">{{ formatDateTime(resp.createdAt ?? '') }}</p>
                          </div>
                        </div>
                        @if (resp.isReviewed) {
                          <span class="inline-flex items-center gap-1 rounded-full border border-emerald-100 bg-emerald-50 px-2.5 py-0.5 text-xs font-medium text-emerald-700">
                            <lucide-icon name="check-circle-2" class="h-3.5 w-3.5" />Revisada
                          </span>
                        } @else if (isDoctor()) {
                          <button appBtn variant="outline" size="sm" class="text-xs" (click)="reviewResponse(resp.id!)" [disabled]="reviewMutation.isPending()">
                            Marcar como revisada
                          </button>
                        }
                      </div>
                      <div class="space-y-3 px-4 py-3.5">
                        <p class="text-sm leading-relaxed text-slate-600">{{ resp.content }}</p>
                        @if (resp.recommendations) {
                          <div class="rounded-lg border-l-2 border-blue-400 bg-blue-50/70 px-3.5 py-2.5">
                            <p class="mb-1 flex items-center gap-1 text-[11px] font-semibold uppercase tracking-wider text-blue-700">
                              <lucide-icon name="zap" class="h-3 w-3" />Recomendaciones
                            </p>
                            <p class="text-sm text-blue-900/80">{{ resp.recommendations }}</p>
                          </div>
                        }
                        @if (resp.observations) {
                          <p class="text-xs text-slate-500"><span class="font-semibold">Observaciones:</span> {{ resp.observations }}</p>
                        }
                      </div>
                    </div>
                  }
                </div>
              }
            </div>

            <!-- Agregar respuesta (abogado) -->
            @if (isLawyer() && isAssignedLawyer() && c.status === 'en_revision') {
              <div class="rounded-xl border border-slate-200 bg-white p-5">
                <h2 class="mb-4 flex items-center gap-2.5 font-semibold text-slate-900">
                  <span class="flex h-8 w-8 items-center justify-center rounded-lg bg-gradient-to-br from-blue-500 to-cyan-500 text-white">
                    <lucide-icon name="pen-line" class="h-4 w-4" />
                  </span>
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

          <!-- ── Columna lateral ─────────────────────────────────────────── -->
          <div class="space-y-5 min-w-0">
            @if (c.context) {
              <div class="rounded-xl border border-slate-200 bg-white p-5">
                <h3 class="mb-3 flex items-center gap-2.5 font-semibold text-slate-900">
                  <span class="flex h-8 w-8 items-center justify-center rounded-lg bg-slate-100 text-slate-500">
                    <lucide-icon name="shield" class="h-4 w-4" />
                  </span>
                  Contexto simulado
                </h3>
                <div class="space-y-2 text-sm">
                  @if (c.context.contextCode) {
                    <span class="inline-block rounded-md bg-slate-100 px-2 py-0.5 font-mono text-xs tracking-wider text-slate-600">
                      {{ c.context.contextCode }}
                    </span>
                  }
                  @if (c.context.medicalArea) {
                    <div class="flex items-center gap-2">
                      <lucide-icon name="activity" class="h-3.5 w-3.5 shrink-0 text-slate-400" />
                      <span class="text-slate-500">Área:</span>
                      <span class="font-medium text-slate-700">{{ c.context.medicalArea }}</span>
                    </div>
                  }
                  @if (c.context.ageReference) {
                    <div class="flex items-center gap-2">
                      <lucide-icon name="user" class="h-3.5 w-3.5 shrink-0 text-slate-400" />
                      <span class="text-slate-500">Edad de referencia:</span>
                      <span class="font-medium text-slate-700">{{ c.context.ageReference }}</span>
                    </div>
                  }
                  @if (c.context.eventDate) {
                    <div class="flex items-center gap-2">
                      <lucide-icon name="calendar" class="h-3.5 w-3.5 shrink-0 text-slate-400" />
                      <span class="text-slate-500">Fecha del evento:</span>
                      <span class="font-medium text-slate-700">{{ formatDate(c.context.eventDate) }}</span>
                    </div>
                  }
                  @if (c.context.summary) {
                    <p class="border-t border-slate-100 pt-3 leading-relaxed text-slate-600">{{ c.context.summary }}</p>
                  }
                  @if (c.context.relevantFactors?.length) {
                    <div class="flex flex-wrap gap-1.5 pt-1">
                      @for (factor of c.context.relevantFactors; track factor) {
                        <span class="rounded-full bg-slate-100 px-2 py-0.5 text-xs text-slate-600">{{ factor }}</span>
                      }
                    </div>
                  }
                </div>
              </div>
            }

            <div class="rounded-xl border border-slate-200 bg-white p-5">
              <h3 class="mb-3 flex items-center gap-2.5 font-semibold text-slate-900">
                <span class="flex h-8 w-8 items-center justify-center rounded-lg bg-slate-100 text-slate-500">
                  <lucide-icon name="scale" class="h-4 w-4" />
                </span>
                Abogado asignado
              </h3>
              @if (c.lawyer) {
                <div class="flex items-center gap-3">
                  <div class="flex h-11 w-11 shrink-0 items-center justify-center rounded-full bg-gradient-to-br from-blue-500 to-cyan-500 text-sm font-bold text-white">
                    {{ getInitials(c.lawyer.fullName ?? '') }}
                  </div>
                  <div class="min-w-0">
                    <p class="truncate text-sm font-semibold text-slate-800">{{ c.lawyer.fullName }}</p>
                    @if (c.lawyer.email) {
                      <p class="truncate text-xs text-slate-500">{{ c.lawyer.email }}</p>
                    }
                  </div>
                </div>
              } @else {
                <div class="rounded-lg border border-dashed border-slate-200 py-5 text-center">
                  <lucide-icon name="scale" class="mx-auto mb-1.5 h-6 w-6 text-slate-300" />
                  <p class="text-sm text-slate-400">Sin abogado asignado</p>
                  @if (isDoctor()) {
                    <a
                      [routerLink]="['/doctor/lawyers']"
                      [queryParams]="{ caseId: c.id }"
                      class="mt-1 inline-block text-xs font-medium text-blue-600 hover:underline"
                    >
                      Buscar abogado →
                    </a>
                  }
                </div>
              }
            </div>

            <!-- Línea de tiempo -->
            <div class="rounded-xl border border-slate-200 bg-white p-5">
              <div class="mb-4 flex flex-wrap items-center justify-between gap-3">
                <h3 class="flex items-center gap-2.5 font-semibold text-slate-900">
                  <span class="flex h-8 w-8 items-center justify-center rounded-lg bg-slate-100 text-slate-500">
                    <lucide-icon name="git-branch" class="h-4 w-4" />
                  </span>
                  Línea de tiempo
                </h3>
                @if (timelineKinds().length > 0) {
                  <select appSelect class="h-auto w-auto min-w-[130px] rounded-lg py-1 text-xs"
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
                <div class="rounded-lg border border-dashed border-slate-200 py-6 text-center">
                  <lucide-icon name="git-branch" class="mx-auto mb-1.5 h-6 w-6 text-slate-300" />
                  <p class="text-sm text-slate-400">Sin eventos registrados</p>
                </div>
              } @else if (filteredTimeline().length === 0) {
                <p class="py-2 text-center text-sm italic text-slate-400">Sin eventos para este filtro</p>
              } @else {
                <div class="relative">
                  <div class="absolute bottom-2 left-[13px] top-2 w-px bg-gradient-to-b from-blue-400 via-slate-200 to-transparent"></div>
                  <div class="space-y-5">
                    @for (entry of filteredTimeline(); track entry.id; let first = $first) {
                      <div class="relative flex gap-3.5">
                        <div
                          class="relative z-10 mt-0.5 flex h-7 w-7 shrink-0 items-center justify-center rounded-full ring-4 ring-white"
                          [class]="first ? 'bg-gradient-to-br from-blue-500 to-cyan-500 text-white shadow-md shadow-blue-200' : 'bg-slate-100 text-slate-500'"
                        >
                          <lucide-icon [name]="timelineIcon(entry.kind)" class="h-3.5 w-3.5" />
                        </div>
                        <div class="min-w-0 flex-1 pb-0.5">
                          <div class="flex flex-wrap items-center gap-x-2 gap-y-1">
                            <p class="text-sm font-semibold text-slate-800">{{ entry.title }}</p>
                            @if (entry.kind) {
                              <span class="rounded-full bg-slate-100 px-2 py-px text-[10px] font-medium uppercase tracking-wide text-slate-500">
                                {{ entry.kind }}
                              </span>
                            }
                          </div>
                          @if (entry.description) {
                            <p class="mt-1 text-xs leading-relaxed text-slate-500">{{ entry.description }}</p>
                          }
                          <p class="mt-1.5 flex items-center gap-1 text-[11px] text-slate-400">
                            <lucide-icon name="clock" class="h-3 w-3" />
                            {{ formatDateTime(entry.occurredAt ?? '') }}
                            @if (entry.actorName) { <span>· {{ entry.actorName }}</span> }
                          </p>
                        </div>
                      </div>
                    }
                  </div>
                </div>
              }
            </div>
          </div>
        </div>
      </div>
    }

    <!-- Modal editar -->
    <app-modal [open]="editModalOpen()" (close)="editModalOpen.set(false)" class="max-w-lg">
      <div appModalHeader>
        <h2 appModalTitle>Editar caso</h2>
        <p appModalDescription>Modifica los datos mientras el caso no tenga abogado asignado.</p>
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
        <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
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
        <p appModalDescription>Agrega un hito a la línea de tiempo del caso.</p>
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

    <app-reason-modal
      title="Cerrar caso"
      description="El caso pasará a estado cerrado y el motivo quedará registrado en su historial."
      label="Motivo del cierre *"
      placeholder="Ej: Caso resuelto con respuesta legal satisfactoria"
      confirmLabel="Cerrar caso"
      (confirmed)="confirmClose($event)"
    />
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
  protected readonly getInitials = getInitials;
  protected readonly asStatus = (s?: string) => (s ?? 'pendiente') as CaseStatus;
  protected readonly asPriority = (p?: string) => (p ?? 'media') as CasePriority;

  protected readonly statusDots = STATUS_DOTS;
  protected readonly priorityDots = PRIORITY_DOTS;
  protected readonly statusLabels = CASE_STATUS_LABELS;
  protected readonly priorityLabels = CASE_PRIORITY_LABELS;

  /** Nota ética HU-43 — vive al pie de la tarjeta de clasificación ML. */
  protected readonly advisoryNote =
    'Las recomendaciones del sistema son un apoyo a la decisión, no una decisión definitiva: ' +
    'la elección del profesional y la revisión humana por el abogado son siempre necesarias.';

  /** Botones del hero (fondo oscuro). */
  protected readonly heroGhostBtn =
    'inline-flex items-center gap-1.5 rounded-lg bg-white/10 px-3.5 py-2 text-xs font-semibold text-slate-100 ' +
    'ring-1 ring-inset ring-white/15 transition hover:bg-white/20 disabled:opacity-50';
  protected readonly heroPrimaryBtn =
    'inline-flex items-center gap-1.5 rounded-lg bg-gradient-to-r from-blue-500 to-cyan-500 px-4 py-2 text-xs ' +
    'font-semibold text-white shadow-lg shadow-blue-950/40 transition hover:from-blue-400 hover:to-cyan-400 disabled:opacity-50';

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

  /** Código visible del caso (contexto o fragmento del id). */
  protected caseCode(c: { context?: { contextCode?: string } | null; id?: string }): string {
    return c.context?.contextCode ?? 'CASO #' + (c.id ?? '').slice(0, 8).toUpperCase();
  }

  /** Icono del hito según su tipo (best-effort sobre el texto del kind). */
  protected timelineIcon(kind?: string): string {
    const k = (kind ?? '').toLowerCase();
    if (k.includes('asigna')) return 'scale';
    if (k.includes('respuesta') || k.includes('legal')) return 'message-square';
    if (k.includes('cierre') || k.includes('cerrad')) return 'archive';
    if (k.includes('revis')) return 'search';
    if (k.includes('registro') || k.includes('creac') || k.includes('consulta')) return 'file-text';
    if (k.includes('sistema')) return 'zap';
    return 'calendar';
  }

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
    onError: () => this.editError.set('No se pudo guardar el caso.'),
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

  protected readonly closeModal = viewChild.required(ReasonModalComponent);

  protected handleClose(): void {
    this.closeModal().show();
  }

  protected confirmClose(reason: string): void {
    this.casesApi.close(this.caseId(), reason).then(() => {
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
      <!DOCTYPE html><html><head><meta charset="utf-8"><title>Informe — ${c?.title ?? 'Caso'}</title>
      <style>body{font-family:system-ui,sans-serif;padding:2rem;color:#1e293b;max-width:800px;margin:0 auto}
      h1{font-size:1.5rem;margin-bottom:0.25rem}h2{font-size:1rem;margin-top:1.5rem;border-bottom:1px solid #e2e8f0;padding-bottom:0.25rem}
      p,li{font-size:0.875rem;line-height:1.6}.meta{color:#64748b;font-size:0.75rem}.badge{display:inline-block;padding:2px 8px;border-radius:4px;background:#f1f5f9;font-size:0.75rem}
      </style></head><body>
      <h1>${c?.title ?? 'Caso'}</h1>
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
