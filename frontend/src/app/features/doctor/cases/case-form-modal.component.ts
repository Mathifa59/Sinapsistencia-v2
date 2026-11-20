import { NgTemplateOutlet } from '@angular/common';
import { Component, inject, output, signal } from '@angular/core';
import { Router } from '@angular/router';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { injectMutation, injectQueryClient } from '@tanstack/angular-query-experimental';
import { LucideAngularModule } from 'lucide-angular';
import { CasesApi, type CaseReportExtraction, type ExtractedFieldSource, type MlInputSnapshot } from '../../../core/api/cases.api';
import { ModalComponent, ModalHeaderDirective, ModalTitleDirective, ModalDescriptionDirective, ModalFooterDirective } from '../../../shared/ui/modal.component';
import { BtnDirective } from '../../../shared/ui/button.directive';
import { InputDirective, LabelDirective, TextareaDirective, SelectDirective } from '../../../shared/ui/field.directives';
import { MEDICAL_SPECIALTIES } from '../../../shared/constants';
import { formatMlScore } from '../../../shared/utils/ml-score.util';
import {
  RISK_FACTOR_LABELS,
  RISK_MEANING_LABEL,
  RiskExplanationComponent,
} from '../../../shared/ui/risk-explanation.component';

/** Debe coincidir con el máximo del backend y del servicio ML (8 MB). */
const MAX_REPORT_BYTES = 8 * 1024 * 1024;

interface RiskResult {
  riskScore: number;
  riskLevel: 'bajo' | 'moderado' | 'alto' | 'critico';
  /** `risk_factors` tal como lo persiste el backend; lo interpreta RiskExplanationComponent. */
  riskFactorsJson: string | null;
  /** Entradas que el backend realmente evaluó (null en clasificaciones anteriores a esta fotografía). */
  inputSnapshot: MlInputSnapshot | null;
  modelVersion: string;
}

const RISK_LEVELS: Record<RiskResult['riskLevel'], { label: string; chip: string; bar: string; icon: string }> = {
  bajo: { label: 'Riesgo Bajo', chip: 'bg-emerald-400/15 text-emerald-300 ring-emerald-400/30', bar: 'from-emerald-500 to-emerald-400', icon: 'shield-check' },
  moderado: { label: 'Riesgo Moderado', chip: 'bg-amber-400/15 text-amber-300 ring-amber-400/30', bar: 'from-amber-500 to-yellow-400', icon: 'alert-triangle' },
  alto: { label: 'Riesgo Alto', chip: 'bg-orange-400/15 text-orange-300 ring-orange-400/30', bar: 'from-orange-500 to-amber-400', icon: 'shield-alert' },
  critico: { label: 'Riesgo Crítico', chip: 'bg-red-400/15 text-red-300 ring-red-400/30', bar: 'from-red-500 to-orange-400', icon: 'x-circle' },
};

/**
 * Detección heurística de posibles datos personales en texto libre (Ley 29733).
 * No bloquea por sí sola: exige confirmación explícita del médico si hay hallazgos.
 */
const PII_PATTERNS: { label: string; regex: RegExp }[] = [
  { label: 'Posible DNI (número de 8 dígitos)', regex: /\b\d{8}\b/ },
  { label: 'Posible teléfono (celular peruano o +51)', regex: /\b9\d{8}\b|\+51\s?\d{8,9}/ },
  { label: 'Correo electrónico', regex: /[\w.+-]+@[\w-]+\.[\w.]+/ },
  {
    label: 'Posible nombre propio (tras "paciente", "Sr.", "Sra.", etc.)',
    regex: /\b(paciente|sr\.?|sra\.?|señor|señora|don|doña)\s+[A-ZÁÉÍÓÚÑ][a-záéíóúñ]+\s+[A-ZÁÉÍÓÚÑ][a-záéíóúñ]+/,
  },
];

function detectPii(text: string): string[] {
  return PII_PATTERNS.filter((p) => p.regex.test(text)).map((p) => p.label);
}

/**
 * Crear caso + análisis de riesgo automático: al registrar, el modal pasa a una
 * fase de "pipeline" que visualiza el análisis real — variables extraídas del
 * formulario → Random Forest (/api/ml/risk) → score, nivel y desglose por factor.
 * Riesgo alto/crítico dispara la alerta por correo en el backend (HU-31).
 */
@Component({
  selector: 'app-case-form-modal',
  imports: [
    ReactiveFormsModule,
    NgTemplateOutlet,
    LucideAngularModule,
    ModalComponent,
    ModalHeaderDirective,
    ModalTitleDirective,
    ModalDescriptionDirective,
    ModalFooterDirective,
    BtnDirective,
    InputDirective,
    LabelDirective,
    TextareaDirective,
    SelectDirective,
    RiskExplanationComponent,
  ],
  template: `
    <app-modal [open]="opened()" (close)="handleClose()" class="max-w-xl">
      @if (phase() === 'form') {
        <div appModalHeader>
          <h2 appModalTitle>Nuevo caso</h2>
          <p appModalDescription>Sube la foto de un informe y completa solo lo que falte. El modelo analizará su riesgo y sugerirá la prioridad automáticamente.</p>
        </div>

        <!-- Indicador de pasos -->
        <ol class="mb-4 flex items-center gap-2 text-[11px] font-medium text-slate-500" aria-label="Pasos del formulario">
          @for (s of stepLabels; track s; let i = $index) {
            <li class="flex items-center gap-2">
              <span class="flex h-5 w-5 items-center justify-center rounded-full text-[10px]"
                [class]="i === step() ? 'bg-blue-600 text-white' : i < step() ? 'bg-emerald-500 text-white' : 'bg-slate-200 text-slate-600'">
                {{ i + 1 }}
              </span>
              <span [class]="i === step() ? 'text-slate-900' : ''">{{ s }}</span>
              @if (i < stepLabels.length - 1) { <span class="h-px w-4 bg-slate-200"></span> }
            </li>
          }
        </ol>

        <form [formGroup]="form" (ngSubmit)="onSubmit()" class="space-y-4">
          @switch (step()) {
            <!-- ── Paso 1: informe (opcional) ─────────────────────────── -->
            @case (0) {
              <div class="space-y-3">
                <div class="rounded-lg border border-dashed border-slate-300 bg-slate-50 px-4 py-5 text-center">
                  <lucide-icon name="file-text" class="mx-auto h-7 w-7 text-slate-400" />
                  <p class="mt-2 text-sm font-medium text-slate-700">Foto o captura del informe del caso</p>
                  <p class="mt-1 text-[11px] text-slate-500">
                    PNG, JPG, WEBP o BMP, hasta 8 MB. Se lee con reconocimiento de texto en nuestro propio servidor:
                    la imagen no se guarda y los datos personales (DNI, teléfono, nombres) se omiten.
                  </p>
                  <input #reportInput type="file" accept="image/png,image/jpeg,image/webp,image/bmp" class="hidden"
                    (change)="onReportSelected($event)" />
                  <button appBtn type="button" variant="primary" class="mt-3 gap-2" (click)="reportInput.click()"
                    [disabled]="extractionState() === 'reading'">
                    @if (extractionState() === 'reading') {
                      <lucide-icon name="loader-2" class="h-4 w-4 animate-spin" /> Leyendo informe…
                    } @else {
                      <lucide-icon name="upload" class="h-4 w-4" /> Elegir imagen del informe
                    }
                  </button>
                </div>

                @if (extractionState() === 'done') {
                  <div class="space-y-1 rounded-lg border border-emerald-200 bg-emerald-50 px-4 py-3 text-xs text-emerald-800">
                    <p class="font-semibold">Informe leído</p>
                    <p>{{ extractionSummary() }}</p>
                    <p class="text-emerald-700">Revisa y corrige cada campo en los siguientes pasos: la lectura es un apoyo, no una decisión.</p>
                  </div>
                }
                @if (extractionPii().length > 0) {
                  <div class="rounded-lg border border-amber-300 bg-amber-50 px-4 py-3 text-xs text-amber-800">
                    <p class="font-semibold">Se omitieron datos personales del informe (Ley N.º 29733)</p>
                    <p class="mt-0.5">Tipos omitidos: {{ extractionPii().join(', ') }}. Verifica que el texto no conserve otros datos identificables.</p>
                  </div>
                }
                @if (extractionError()) {
                  <p class="rounded-md border border-red-100 bg-red-50 px-3 py-2 text-xs text-red-600">{{ extractionError() }}</p>
                }
                <p class="text-center text-[11px] text-slate-400">¿No tienes el informe a mano? Puedes omitir este paso y completar los datos a mano.</p>
              </div>
            }

            <!-- ── Paso 2: datos del caso ─────────────────────────────── -->
            @case (1) {
              <div class="space-y-1.5">
                <label appLabel for="case-title">Título del caso * <ng-container [ngTemplateOutlet]="tagTpl" [ngTemplateOutletContext]="{ f: 'title' }" /></label>
                <input appInput id="case-title" placeholder="Ej: Revisión de consentimiento informado — Cirugía" formControlName="title" />
                <p class="text-[11px] text-slate-400">Describe el evento, no al paciente: sin nombres ni datos que lo identifiquen (Ley 29733).</p>
                @if (form.controls.title.invalid && form.controls.title.touched) {
                  <p class="text-xs text-red-500">El título debe tener al menos 3 caracteres</p>
                }
              </div>

              <div class="space-y-1.5">
                <label appLabel for="case-description">Descripción * <ng-container [ngTemplateOutlet]="tagTpl" [ngTemplateOutletContext]="{ f: 'description' }" /></label>
                <textarea appTextarea id="case-description" rows="4" placeholder="Describe el contexto clínico y los aspectos legales relevantes..." formControlName="description"></textarea>
                <p class="text-[11px] text-slate-400">Mientras más específica (qué ocurrió, qué aspecto legal consultas), mejor se recomienda al abogado. Mínimo 10 caracteres.</p>
                @if (form.controls.description.invalid && form.controls.description.touched) {
                  <p class="text-xs text-red-500">La descripción debe tener al menos 10 caracteres</p>
                }
              </div>

              <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div class="space-y-1.5">
                  <label appLabel for="case-specialty">Especialidad médica * <ng-container [ngTemplateOutlet]="tagTpl" [ngTemplateOutletContext]="{ f: 'medicalSpecialty' }" /></label>
                  <select appSelect id="case-specialty" formControlName="medicalSpecialty">
                    <option value="" disabled>Selecciona...</option>
                    @for (s of specialties; track s) {
                      <option [value]="s">{{ s }}</option>
                    }
                  </select>
                  <p class="text-[11px] text-slate-400">Área clínica del caso (donde ocurrió el evento), aunque no sea la tuya.</p>
                  @if (form.controls.medicalSpecialty.invalid && form.controls.medicalSpecialty.touched) {
                    <p class="text-xs text-red-500">Selecciona la especialidad</p>
                  }
                </div>

                <div class="space-y-1.5">
                  <label appLabel for="case-event-type">Tipo de evento <ng-container [ngTemplateOutlet]="tagTpl" [ngTemplateOutletContext]="{ f: 'eventType' }" /></label>
                  <input appInput id="case-event-type" placeholder="Ej: Cirugía, consulta, diagnóstico" formControlName="eventType" />
                  <p class="text-[11px] text-slate-400">Qué tipo de situación originó el caso.</p>
                </div>
              </div>

              <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div class="space-y-1.5">
                  <label appLabel>Complejidad del procedimiento * <ng-container [ngTemplateOutlet]="tagTpl" [ngTemplateOutletContext]="{ f: 'procedureComplexity' }" /></label>
                  <select appSelect formControlName="procedureComplexity">
                    <option value="" disabled>Selecciona...</option>
                    <option value="baja">Baja</option>
                    <option value="media">Media</option>
                    <option value="alta">Alta</option>
                  </select>
                  <p class="text-[11px] text-slate-400">Alta: cirugía mayor, UCI, riesgo vital. Baja: control de rutina, limpieza de oído.</p>
                  @if (form.controls.procedureComplexity.invalid && form.controls.procedureComplexity.touched) {
                    <p class="text-xs text-red-500">Selecciona la complejidad del procedimiento</p>
                  }
                </div>

                <div class="space-y-1.5">
                  <label appLabel>Urgencia percibida *</label>
                  <select appSelect formControlName="perceivedUrgency">
                    <option value="baja">Baja</option>
                    <option value="media">Media</option>
                    <option value="alta">Alta</option>
                    <option value="critica">Crítica</option>
                  </select>
                  <p class="text-[11px] text-slate-400">Tu criterio como médico; nunca se lee del informe. La prioridad final la sugiere el modelo según el riesgo.</p>
                </div>
              </div>
            }

            <!-- ── Paso 3: factores del riesgo y contexto ─────────────── -->
            @case (2) {
              <div class="rounded-lg border border-blue-100 bg-blue-50/50 px-4 py-3 space-y-3">
                <p class="flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wider text-blue-700">
                  <lucide-icon name="cpu" class="h-3.5 w-3.5" />
                  Factores del análisis de riesgo
                </p>

                <div class="grid grid-cols-1 sm:grid-cols-3 gap-3">
                  <div class="space-y-1.5">
                    <label appLabel for="f-docs">Documentación clínica completa * <ng-container [ngTemplateOutlet]="tagTpl" [ngTemplateOutletContext]="{ f: 'documentationComplete' }" /></label>
                    <select appSelect id="f-docs" formControlName="documentationComplete">
                      <option value="" disabled>Selecciona...</option>
                      <option value="si">Sí</option>
                      <option value="no">No</option>
                    </select>
                  </div>
                  <div class="space-y-1.5">
                    <label appLabel for="f-consent">Consentimiento informado firmado * <ng-container [ngTemplateOutlet]="tagTpl" [ngTemplateOutletContext]="{ f: 'informedConsent' }" /></label>
                    <select appSelect id="f-consent" formControlName="informedConsent">
                      <option value="" disabled>Selecciona...</option>
                      <option value="si">Sí</option>
                      <option value="no">No</option>
                    </select>
                  </div>
                  <div class="space-y-1.5">
                    <label appLabel for="f-complaints">¿Quejas previas? * <ng-container [ngTemplateOutlet]="tagTpl" [ngTemplateOutletContext]="{ f: 'hasPriorComplaints' }" /></label>
                    <select appSelect id="f-complaints" formControlName="hasPriorComplaints">
                      <option value="" disabled>Selecciona...</option>
                      <option value="si">Sí</option>
                      <option value="no">No</option>
                    </select>
                  </div>
                </div>
                <p class="text-[11px] text-slate-500">Estas tres respuestas alimentan al modelo: si el informe no las dice con claridad, no se asumen; responde tú.</p>
                @if ((form.controls.documentationComplete.invalid && form.controls.documentationComplete.touched)
                  || (form.controls.informedConsent.invalid && form.controls.informedConsent.touched)
                  || (form.controls.hasPriorComplaints.invalid && form.controls.hasPriorComplaints.touched)) {
                  <p class="text-xs text-red-500">Responde las tres preguntas</p>
                }
              </div>

              <div class="border-t border-slate-100 pt-4 space-y-3">
                <p class="text-xs font-semibold text-slate-500 uppercase tracking-wider">
                  Contexto simulado (sin datos identificables — Ley 29733)
                </p>

                <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
                  <div class="space-y-1.5">
                    <label appLabel for="context-age">Edad de referencia <ng-container [ngTemplateOutlet]="tagTpl" [ngTemplateOutletContext]="{ f: 'ageReference' }" /></label>
                    <input appInput id="context-age" type="number" min="0" max="120" placeholder="Ej: 45" formControlName="ageReference" />
                  </div>
                  <div class="space-y-1.5">
                    <label appLabel for="context-date">Fecha del evento <ng-container [ngTemplateOutlet]="tagTpl" [ngTemplateOutletContext]="{ f: 'eventDate' }" /></label>
                    <input appInput id="context-date" type="date" [max]="today" formControlName="eventDate" />
                  </div>
                </div>

                <div class="space-y-1.5">
                  <label appLabel for="context-summary">Resumen del contexto</label>
                  <textarea appTextarea id="context-summary" rows="2" placeholder="Resumen anonimizado del caso..." formControlName="summary"></textarea>
                </div>

                <div class="space-y-1.5">
                  <label appLabel for="context-factors">Factores relevantes <ng-container [ngTemplateOutlet]="tagTpl" [ngTemplateOutletContext]="{ f: 'relevantFactors' }" /></label>
                  <input appInput id="context-factors" placeholder="Separados por coma: ej. cirugía electiva, poca experiencia del equipo" formControlName="relevantFactors" />
                </div>

                <div class="space-y-1.5">
                  <label appLabel for="case-notes">Notas adicionales <ng-container [ngTemplateOutlet]="tagTpl" [ngTemplateOutletContext]="{ f: 'notes' }" /></label>
                  <textarea appTextarea id="case-notes" rows="2" placeholder="Observaciones o información adicional relevante..." formControlName="notes"></textarea>
                </div>
              </div>
            }
          }

          @if (piiFindings().length > 0) {
            <div class="space-y-2 rounded-lg border border-amber-300 bg-amber-50 px-4 py-3">
              <p class="flex items-center gap-1.5 text-xs font-semibold text-amber-800">
                <lucide-icon name="shield-alert" class="h-4 w-4" />
                Posibles datos personales detectados (Ley N.º 29733)
              </p>
              <ul class="list-disc space-y-0.5 pl-5 text-xs text-amber-800">
                @for (f of piiFindings(); track f) {
                  <li>{{ f }}</li>
                }
              </ul>
              <p class="text-xs text-amber-700">
                Anonimiza el texto (usa el contexto simulado) o confirma que no se trata de datos reales.
              </p>
              <label class="flex items-start gap-2 text-xs font-medium text-amber-900">
                <input type="checkbox" [checked]="piiConfirmed()" (change)="piiConfirmed.set($any($event.target).checked)"
                  class="mt-0.5 h-4 w-4 rounded border-amber-300 text-amber-600 focus:ring-amber-500" />
                Confirmo que el texto no contiene datos personales reales de pacientes
              </label>
            </div>
          }

          @if (serverError()) {
            <p class="text-xs text-red-600 bg-red-50 border border-red-100 rounded-md px-3 py-2">{{ serverError() }}</p>
          }

          <div appModalFooter>
            <button appBtn type="button" variant="outline" (click)="handleClose()" [disabled]="createMutation.isPending()">
              Cancelar
            </button>
            @if (step() > 0) {
              <button appBtn type="button" variant="outline" (click)="prevStep()" [disabled]="createMutation.isPending()">
                Atrás
              </button>
            }
            @if (step() < lastStep) {
              <button appBtn type="button" variant="primary" (click)="nextStep()" [disabled]="extractionState() === 'reading'">
                {{ step() === 0 && extractionState() !== 'done' ? 'Omitir y completar a mano' : 'Siguiente' }}
              </button>
            } @else {
              <button appBtn type="submit" variant="primary" class="gap-2" [disabled]="createMutation.isPending()">
                @if (createMutation.isPending()) {
                  <lucide-icon name="loader-2" class="h-4 w-4 animate-spin" />
                }
                Crear y analizar
              </button>
            }
          </div>
        </form>

        <!-- Etiqueta de origen del campo (solo si se leyó un informe) -->
        <ng-template #tagTpl let-f="f">
          @if (tagFor(f); as t) {
            <span class="ml-1 rounded-full px-1.5 py-0.5 text-[10px] font-medium normal-case" [class]="t.cls">{{ t.text }}</span>
          }
        </ng-template>
      } @else {
        <!-- ── Fase de análisis: pipeline del modelo ─────────────────────── -->
        <div appModalHeader>
          <h2 appModalTitle class="flex items-center gap-2">
            <span class="flex h-8 w-8 items-center justify-center rounded-lg bg-gradient-to-br from-blue-500 to-cyan-500 text-white">
              <lucide-icon name="cpu" class="h-4 w-4" />
            </span>
            Análisis de riesgo del caso
          </h2>
          <p appModalDescription>El modelo evalúa los factores del caso para estimar su riesgo médico-legal.</p>
        </div>

        <div class="relative overflow-hidden rounded-xl bg-slate-900 p-5 text-white">
          <div class="pointer-events-none absolute -top-20 -right-16 h-56 w-56 rounded-full bg-blue-600/25 blur-3xl"></div>
          <div class="pointer-events-none absolute -bottom-24 -left-10 h-56 w-56 rounded-full bg-cyan-500/15 blur-3xl"></div>

          <div class="relative space-y-4">
            <!-- Paso 1: registro -->
            <div class="flex items-center gap-3 transition-all duration-500"
              [class]="stage() >= 1 ? 'opacity-100 translate-y-0' : 'opacity-0 translate-y-2'">
              <span class="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-emerald-400/15 text-emerald-300 ring-1 ring-inset ring-emerald-400/30">
                <lucide-icon name="check-circle-2" class="h-4 w-4" />
              </span>
              <p class="text-sm font-medium">Caso registrado en el sistema</p>
            </div>

            <!-- Paso 2: variables -->
            <div class="transition-all duration-500"
              [class]="stage() >= 2 ? 'opacity-100 translate-y-0' : 'opacity-0 translate-y-2'">
              <div class="flex items-center gap-3">
                <span class="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-emerald-400/15 text-emerald-300 ring-1 ring-inset ring-emerald-400/30">
                  <lucide-icon name="check-circle-2" class="h-4 w-4" />
                </span>
                <p class="text-sm font-medium">Variables extraídas del caso</p>
              </div>
              @if (stage() >= 2) {
                @if (inputs(); as vars) {
                  <div class="mt-2.5 ml-10 flex flex-wrap gap-1.5">
                    <span class="rounded-full bg-white/10 px-2.5 py-0.5 text-[11px] text-slate-200 ring-1 ring-inset ring-white/10">{{ vars.specialty }}</span>
                    <span class="rounded-full bg-white/10 px-2.5 py-0.5 text-[11px] text-slate-200 ring-1 ring-inset ring-white/10">Complejidad {{ vars.complexity }}</span>
                    <span class="rounded-full bg-white/10 px-2.5 py-0.5 text-[11px] text-slate-200 ring-1 ring-inset ring-white/10">Urgencia percibida {{ vars.priority }}</span>
                    <span class="rounded-full px-2.5 py-0.5 text-[11px] ring-1 ring-inset"
                      [class]="vars.documentation ? 'bg-emerald-400/10 text-emerald-300 ring-emerald-400/20' : 'bg-red-400/10 text-red-300 ring-red-400/20'">
                      Documentación {{ vars.documentation ? 'completa' : 'incompleta' }}
                    </span>
                    <span class="rounded-full px-2.5 py-0.5 text-[11px] ring-1 ring-inset"
                      [class]="vars.consent ? 'bg-emerald-400/10 text-emerald-300 ring-emerald-400/20' : 'bg-red-400/10 text-red-300 ring-red-400/20'">
                      Consentimiento {{ vars.consent ? 'firmado' : 'ausente' }}
                    </span>
                    @if (vars.priorComplaints) {
                      <span class="rounded-full bg-red-400/10 px-2.5 py-0.5 text-[11px] text-red-300 ring-1 ring-inset ring-red-400/20">Quejas previas</span>
                    }
                    @if (vars.daysSince != null) {
                      <span class="rounded-full bg-white/10 px-2.5 py-0.5 text-[11px] text-slate-200 ring-1 ring-inset ring-white/10">{{ vars.daysSince }} días desde el evento</span>
                    } @else {
                      <span class="rounded-full bg-white/10 px-2.5 py-0.5 text-[11px] text-slate-200 ring-1 ring-inset ring-white/10">Sin fecha de evento · se asumió 0 días</span>
                    }
                  </div>
                } @else if (inputsUnavailable()) {
                  <p class="mt-2.5 ml-10 text-[11px] text-slate-400">Entrada histórica no disponible.</p>
                }
              }
            </div>

            <!-- Paso 3: Random Forest -->
            <div class="flex items-center gap-3 transition-all duration-500"
              [class]="stage() >= 3 ? 'opacity-100 translate-y-0' : 'opacity-0 translate-y-2'">
              @if (stage() === 3) {
                <span class="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-blue-400/15 text-blue-300 ring-1 ring-inset ring-blue-400/30">
                  <lucide-icon name="loader-2" class="h-4 w-4 animate-spin" />
                </span>
                <div>
                  <p class="text-sm font-medium">Ejecutando Random Forest…</p>
                  <p class="text-[11px] text-slate-400">Clasificación de riesgo sobre {{ factorCount }} factores ponderados</p>
                </div>
              } @else if (stage() >= 4) {
                <span class="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-emerald-400/15 text-emerald-300 ring-1 ring-inset ring-emerald-400/30">
                  <lucide-icon name="check-circle-2" class="h-4 w-4" />
                </span>
                <p class="text-sm font-medium">
                  Random Forest completado
                  @if (risk(); as r) { <span class="text-[11px] text-slate-400">· modelo {{ r.modelVersion }}</span> }
                </p>
              }
            </div>

            <!-- Resultado -->
            @if (stage() >= 4) {
              <div class="border-t border-white/10 pt-4 transition-all duration-700"
                [class]="stage() >= 4 ? 'opacity-100 translate-y-0' : 'opacity-0 translate-y-3'">
                @if (risk(); as r) {
                  <div class="flex flex-wrap items-center justify-between gap-3">
                    <div>
                      <p class="text-[11px] uppercase tracking-wider text-slate-400">Score de riesgo global</p>
                      <p class="text-4xl font-bold tracking-tight">{{ formatMlScore(r.riskScore) ?? '—' }}</p>
                    </div>
                    <span class="inline-flex items-center gap-1.5 rounded-full px-3.5 py-1.5 text-sm font-semibold ring-1 ring-inset"
                      [class]="levels[r.riskLevel].chip">
                      <lucide-icon [name]="levels[r.riskLevel].icon" class="h-4 w-4" />
                      {{ levels[r.riskLevel].label }}
                    </span>
                  </div>
                  <div class="mt-3 h-2 overflow-hidden rounded-full bg-white/10">
                    <div class="h-full rounded-full bg-gradient-to-r transition-all duration-1000"
                      [class]="levels[r.riskLevel].bar"
                      [style.width.%]="Math.round(r.riskScore * 100)"></div>
                  </div>
                  <p class="mt-2 text-[11px] text-slate-400">{{ riskMeaningLabel }}</p>
                  <p class="mt-2 text-[11px] text-slate-400">
                    Prioridad del caso sugerida por el modelo:
                    <span class="font-semibold capitalize text-slate-200">{{ createdPriority() }}</span>
                    · puedes ajustarla editando el caso (revisión humana).
                  </p>

                  <div class="mt-4">
                    <app-risk-explanation variant="dark" [riskFactorsJson]="r.riskFactorsJson" [inputSnapshot]="r.inputSnapshot" />
                  </div>

                  @if (r.riskLevel === 'alto' || r.riskLevel === 'critico') {
                    <div class="mt-4 flex items-start gap-2 rounded-lg bg-amber-400/10 px-3.5 py-2.5 ring-1 ring-inset ring-amber-400/25">
                      <lucide-icon name="zap" class="mt-0.5 h-4 w-4 shrink-0 text-amber-300" />
                      <p class="text-xs leading-relaxed text-amber-200">
                        Riesgo {{ r.riskLevel }}: se disparó una <span class="font-semibold">alerta automática</span> al equipo legal.
                      </p>
                    </div>
                  }
                } @else {
                  <div class="flex items-start gap-2 rounded-lg bg-white/5 px-3.5 py-2.5 ring-1 ring-inset ring-white/10">
                    <lucide-icon name="info" class="mt-0.5 h-4 w-4 shrink-0 text-slate-400" />
                    <p class="text-xs leading-relaxed text-slate-300">
                      El servicio ML no está disponible; el caso fue clasificado con el sistema de reglas de respaldo.
                      Prioridad asignada: <span class="font-semibold capitalize">{{ createdPriority() }}</span>.
                    </p>
                  </div>
                }

                <p class="mt-4 border-t border-white/10 pt-3 text-[11px] leading-relaxed text-slate-500">
                  {{ advisoryNote }}
                </p>
              </div>
            }
          </div>
        </div>

        @if (stage() >= 4) {
          <div class="mt-5 flex justify-end gap-2">
            <button appBtn type="button" variant="outline" (click)="handleClose()">Cerrar</button>
            <button appBtn type="button" variant="primary" class="gap-1.5" (click)="goToCase()">
              Ver caso <lucide-icon name="arrow-right" class="h-4 w-4" />
            </button>
          </div>
        }
      }
    </app-modal>
  `,
})
export class CaseFormModalComponent {
  private readonly fb = inject(FormBuilder);
  private readonly casesApi = inject(CasesApi);
  private readonly router = inject(Router);
  private readonly queryClient = injectQueryClient();

  protected readonly opened = signal(false);
  readonly closed = output<void>();

  protected readonly specialties = MEDICAL_SPECIALTIES;
  protected readonly serverError = signal<string | null>(null);
  protected readonly Math = Math;
  protected readonly formatMlScore = formatMlScore;
  protected readonly levels = RISK_LEVELS;
  protected readonly riskMeaningLabel = RISK_MEANING_LABEL;
  protected readonly factorCount = Object.keys(RISK_FACTOR_LABELS).length;

  /** Nota ética HU-43. */
  protected readonly advisoryNote =
    'Las recomendaciones del sistema son un apoyo a la decisión, no una decisión definitiva: ' +
    'la revisión humana por el profesional es siempre necesaria.';

  // ── Estado del pipeline de análisis ─────────────────────────────────────
  protected readonly phase = signal<'form' | 'analyzing'>('form');
  protected readonly stage = signal(0);
  protected readonly risk = signal<RiskResult | null>(null);
  protected readonly inputs = signal<{
    specialty: string; complexity: string; priority: string;
    documentation: boolean; consent: boolean; priorComplaints: boolean; daysSince: number | null;
  } | null>(null);
  protected readonly createdId = signal<string>('');
  protected readonly createdPriority = signal<string>('media');
  /** H-05: true cuando la clasificación persistida no trae inputSnapshot (fotografía legacy ausente). */
  protected readonly inputsUnavailable = signal(false);
  private timers: ReturnType<typeof setTimeout>[] = [];

  /** Ley 29733: hallazgos de posibles datos personales en los campos libres. */
  protected readonly piiFindings = signal<string[]>([]);
  protected readonly piiConfirmed = signal(false);

  constructor() {
    this.form.valueChanges.subscribe((v) => {
      const text = [v.title, v.description, v.notes, v.summary].filter(Boolean).join(' ');
      const findings = detectPii(text);
      this.piiFindings.set(findings);
      if (findings.length === 0) this.piiConfirmed.set(false);
    });
  }

  protected readonly form = this.fb.nonNullable.group({
    title: ['', [Validators.required, Validators.minLength(3)]],
    description: ['', [Validators.required, Validators.minLength(10)]],
    medicalSpecialty: ['', Validators.required],
    eventType: [''],
    perceivedUrgency: ['media', Validators.required],
    procedureComplexity: ['', Validators.required],
    notes: [''],
    // Sí/No explícitos: alimentan al clasificador, por eso no se asume ningún valor por omisión.
    documentationComplete: ['', Validators.required],
    informedConsent: ['', Validators.required],
    hasPriorComplaints: ['', Validators.required],
    ageReference: [''],
    eventDate: [''],
    summary: [''],
    relevantFactors: [''],
  });

  // ── Pasos del formulario y lectura del informe ──────────────────────────
  protected readonly stepLabels = ['Informe', 'Datos del caso', 'Factores y contexto'];
  protected readonly lastStep = this.stepLabels.length - 1;
  protected readonly step = signal(0);
  protected readonly today = new Date().toISOString().slice(0, 10);

  protected readonly extractionState = signal<'idle' | 'reading' | 'done'>('idle');
  protected readonly extractionError = signal<string | null>(null);
  protected readonly extractionPii = signal<string[]>([]);
  protected readonly extractionSummary = signal('');
  /** Origen de cada campo propuesto desde el informe (vacío si no se leyó ninguno). */
  protected readonly sources = signal<Partial<Record<string, ExtractedFieldSource>>>({});

  /** Controles que deben ser válidos para avanzar de cada paso. */
  private readonly stepControls: string[][] = [
    [],
    ['title', 'description', 'medicalSpecialty', 'procedureComplexity', 'perceivedUrgency'],
    ['documentationComplete', 'informedConsent', 'hasPriorComplaints'],
  ];

  protected tagFor(field: string): { text: string; cls: string } | null {
    if (this.extractionState() !== 'done') return null;
    switch (this.sources()[field]) {
      case 'detected':
        return { text: 'Leído del informe', cls: 'bg-emerald-100 text-emerald-700' };
      case 'inferred':
        return { text: 'Sugerido: confírmalo', cls: 'bg-amber-100 text-amber-700' };
      case 'missing':
        return { text: 'No está en el informe: complétalo', cls: 'bg-slate-100 text-slate-600' };
      default:
        return null;
    }
  }

  protected nextStep(): void {
    const controls = this.stepControls[this.step()] ?? [];
    const invalid = controls.filter((name) => this.form.get(name)?.invalid);
    if (invalid.length > 0) {
      invalid.forEach((name) => this.form.get(name)?.markAsTouched());
      return;
    }
    this.serverError.set(null);
    this.step.update((s) => Math.min(s + 1, this.lastStep));
  }

  protected prevStep(): void {
    this.step.update((s) => Math.max(s - 1, 0));
  }

  protected async onReportSelected(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = ''; // permite volver a elegir el mismo archivo
    if (!file) return;

    this.extractionError.set(null);
    if (!file.type.startsWith('image/')) {
      this.extractionError.set('Elige una imagen (PNG, JPG, WEBP o BMP).');
      return;
    }
    if (file.size > MAX_REPORT_BYTES) {
      this.extractionError.set('La imagen supera el máximo de 8 MB.');
      return;
    }

    this.extractionState.set('reading');
    try {
      const result = await this.casesApi.extractFromReport(file);
      this.applyExtraction(result);
      this.extractionState.set('done');
    } catch (err) {
      // El formulario sigue disponible a mano: la lectura del informe es opcional.
      this.extractionState.set('idle');
      const message = err instanceof Error ? err.message : '';
      this.extractionError.set(message || 'No se pudo leer el informe. Completa el formulario a mano.');
    }
  }

  /** Vuelca la propuesta en el formulario. Nunca toca la urgencia percibida (juicio del médico). */
  private applyExtraction(result: CaseReportExtraction): void {
    const f = result.fields;
    const patch: Record<string, string> = {};
    const sources: Record<string, ExtractedFieldSource> = {};
    let detected = 0;
    let inferred = 0;
    let missing = 0;

    const take = <K extends keyof CaseReportExtraction['fields']>(
      name: K,
      toForm: (value: NonNullable<NonNullable<CaseReportExtraction['fields'][K]>['value']>) => string | null,
    ) => {
      const field = f[name];
      const source = field?.source ?? 'missing';
      sources[name] = source;
      if (source === 'missing' || field?.value == null) {
        sources[name] = 'missing';
        missing++;
        return;
      }
      const mapped = toForm(field.value as NonNullable<NonNullable<CaseReportExtraction['fields'][K]>['value']>);
      if (mapped == null || mapped === '') {
        sources[name] = 'missing';
        missing++;
        return;
      }
      patch[name] = mapped;
      if (source === 'detected') detected++;
      else inferred++;
    };

    take('title', (v) => v);
    take('description', (v) => v);
    take('medicalSpecialty', (v) => (this.specialties.includes(v) ? v : null));
    take('eventType', (v) => v);
    take('procedureComplexity', (v) => v);
    take('eventDate', (v) => v);
    take('ageReference', (v) => String(v));
    take('documentationComplete', (v) => (v ? 'si' : 'no'));
    take('informedConsent', (v) => (v ? 'si' : 'no'));
    take('hasPriorComplaints', (v) => (v ? 'si' : 'no'));
    take('notes', (v) => v);
    take('relevantFactors', (v) => v.join(', '));

    this.form.patchValue(patch);
    this.sources.set(sources);
    this.extractionPii.set(result.piiRedacted ?? []);
    this.extractionSummary.set(
      `${detected} campos leídos del informe, ${inferred} sugeridos para confirmar y ${missing} por completar a mano.`,
    );
  }

  protected readonly createMutation = injectMutation(() => ({
    mutationFn: () => {
      const v = this.form.getRawValue();
      // El código de referencia ya no se pide: lo asigna el sistema. El área médica del
      // contexto es la especialidad del caso (un solo dato, no dos campos).
      const context = v.medicalSpecialty
        ? {
            ageReference: v.ageReference ? Number(v.ageReference) : undefined,
            medicalArea: v.medicalSpecialty,
            eventDate: v.eventDate || undefined,
            summary: v.summary.trim() || undefined,
            relevantFactors: v.relevantFactors.trim()
              ? v.relevantFactors.split(',').map((f) => f.trim()).filter(Boolean)
              : undefined,
          }
        : undefined;

      const body = {
        title: v.title.trim(),
        description: v.description.trim(),
        priority: v.perceivedUrgency,
        medicalSpecialty: v.medicalSpecialty,
        eventType: v.eventType.trim() || undefined,
        perceivedUrgency: v.perceivedUrgency,
        procedureComplexity: v.procedureComplexity,
        documentationComplete: v.documentationComplete === 'si',
        informedConsent: v.informedConsent === 'si',
        hasPriorComplaints: v.hasPriorComplaints === 'si',
        notes: v.notes.trim() || undefined,
        context,
      };
      return this.casesApi.create(body as Parameters<typeof this.casesApi.create>[0]);
    },
    onSuccess: (created) => {
      this.queryClient.invalidateQueries({ queryKey: ['cases'] });
      this.createdId.set(created.id ?? '');
      this.createdPriority.set(created.priority ?? this.form.getRawValue().perceivedUrgency);
      this.startAnalysis();
    },
    onError: (err: Error) => this.serverError.set(err.message),
  }));

  /**
   * Pipeline de análisis: el backend ya clasificó con el RF al crear; aquí solo se anima
   * la lectura del resultado PERSISTIDO (una sola fuente de verdad). H-05: las variables
   * del paso 2 se leen de `inputSnapshot` (lo que el backend REALMENTE evaluó), nunca se
   * recomputan con el reloj/heurística del navegador.
   */
  private startAnalysis(): void {
    const v = this.form.getRawValue();
    const urgency = v.perceivedUrgency || 'media';

    this.phase.set('analyzing');
    this.stage.set(1);
    const startedAt = Date.now();
    this.timers.push(setTimeout(() => this.stage.set(2), 700));
    this.timers.push(setTimeout(() => this.stage.set(3), 1600));

    const finish = (result: RiskResult | null) => {
      const wait = Math.max(0, 2800 - (Date.now() - startedAt));
      this.timers.push(setTimeout(() => {
        this.risk.set(result);
        this.stage.set(4);
      }, wait));
    };

    this.casesApi
      .getDetail(this.createdId())
      .then((detail) => {
        this.createdPriority.set(detail.caseData?.priority ?? urgency);

        const snapshot = detail.classification?.inputSnapshot;
        const snapshotInputs = snapshot?.inputs;
        this.inputsUnavailable.set(!snapshotInputs);
        this.inputs.set(
          snapshotInputs
            ? {
                specialty: snapshotInputs.specialty ?? '—',
                complexity: snapshotInputs.procedure_complexity ?? '—',
                priority: snapshotInputs.priority ?? '—',
                documentation: !!snapshotInputs.documentation_complete,
                consent: !!snapshotInputs.informed_consent,
                priorComplaints: !!snapshotInputs.has_prior_complaints,
                daysSince: snapshotInputs.time_since_incident_days ?? null,
              }
            : null,
        );

        const cls = detail.classification;
        if (cls?.riskScore != null && cls.riskLevel) {
          finish({
            riskScore: Number(cls.riskScore),
            riskLevel: cls.riskLevel as RiskResult['riskLevel'],
            riskFactorsJson: cls.riskFactorsJson ?? null,
            inputSnapshot: cls.inputSnapshot ?? null,
            modelVersion: cls.modelVersion ?? 'rf',
          });
        } else {
          finish(null);
        }
      })
      .catch(() => finish(null));
  }

  protected goToCase(): void {
    const id = this.createdId();
    this.resetAll();
    if (id) void this.router.navigate(['/doctor/cases', id]);
  }

  show(): void {
    this.resetAll(false);
    this.opened.set(true);
  }

  protected onSubmit(): void {
    this.serverError.set(null);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    if (this.piiFindings().length > 0 && !this.piiConfirmed()) {
      this.serverError.set(
        'Se detectaron posibles datos personales. Anonimiza el texto o marca la confirmación (Ley 29733).');
      return;
    }
    this.createMutation.mutate();
  }

  protected handleClose(): void {
    this.resetAll();
  }

  private resetAll(close = true): void {
    this.timers.forEach(clearTimeout);
    this.timers = [];
    this.form.reset({
      perceivedUrgency: 'media',
      procedureComplexity: '',
      medicalSpecialty: '',
      documentationComplete: '',
      informedConsent: '',
      hasPriorComplaints: '',
    });
    this.step.set(0);
    this.extractionState.set('idle');
    this.extractionError.set(null);
    this.extractionPii.set([]);
    this.extractionSummary.set('');
    this.sources.set({});
    this.serverError.set(null);
    this.phase.set('form');
    this.stage.set(0);
    this.risk.set(null);
    this.inputs.set(null);
    this.inputsUnavailable.set(false);
    this.piiFindings.set([]);
    this.piiConfirmed.set(false);
    if (close) {
      this.opened.set(false);
      this.closed.emit();
    }
  }
}
