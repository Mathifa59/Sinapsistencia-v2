import { Component, inject, output, signal } from '@angular/core';
import { Router } from '@angular/router';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { injectMutation, injectQueryClient } from '@tanstack/angular-query-experimental';
import { LucideAngularModule } from 'lucide-angular';
import { CasesApi } from '../../../core/api/cases.api';
import { MlApi } from '../../../core/api/ml.api';
import { ModalComponent, ModalHeaderDirective, ModalTitleDirective, ModalDescriptionDirective, ModalFooterDirective } from '../../../shared/ui/modal.component';
import { BtnDirective } from '../../../shared/ui/button.directive';
import { InputDirective, LabelDirective, TextareaDirective, SelectDirective } from '../../../shared/ui/field.directives';
import { MEDICAL_SPECIALTIES } from '../../../shared/constants';

interface RiskFactor {
  name: string;
  weight: number;
  value: number;
  contribution: number;
  description: string;
}

interface RiskResult {
  riskScore: number;
  riskLevel: 'bajo' | 'moderado' | 'alto' | 'critico';
  riskFactors: RiskFactor[];
  recommendations: string[];
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

const FACTOR_LABELS: Record<string, string> = {
  specialty_risk: 'Riesgo por especialidad',
  procedure_complexity: 'Complejidad del procedimiento',
  documentation: 'Estado de la documentación',
  informed_consent: 'Consentimiento informado',
  prior_complaints: 'Historial de quejas',
  time_factor: 'Tiempo desde el incidente',
  priority: 'Prioridad del caso',
};

/**
 * Crear caso + análisis de riesgo automático: al registrar, el modal pasa a una
 * fase de "pipeline" que visualiza el análisis real — variables extraídas del
 * formulario → Random Forest (/api/ml/risk) → score, nivel y desglose por factor.
 * Riesgo alto/crítico dispara la alerta n8n en el backend (HU-31).
 */
@Component({
  selector: 'app-case-form-modal',
  imports: [
    ReactiveFormsModule,
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
  ],
  template: `
    <app-modal [open]="opened()" (close)="handleClose()" class="max-w-xl">
      @if (phase() === 'form') {
        <div appModalHeader>
          <h2 appModalTitle>Nuevo caso</h2>
          <p appModalDescription>Registra un nuevo caso clínico-legal. Al crearlo, el sistema analizará su riesgo automáticamente.</p>
        </div>

        <form [formGroup]="form" (ngSubmit)="onSubmit()" class="space-y-4">
          <div class="space-y-1.5">
            <label appLabel for="case-title">Título del caso *</label>
            <input appInput id="case-title" placeholder="Ej: Revisión de consentimiento informado — Cirugía" formControlName="title" />
            @if (form.controls.title.invalid && form.controls.title.touched) {
              <p class="text-xs text-red-500">El título debe tener al menos 3 caracteres</p>
            }
          </div>

          <div class="space-y-1.5">
            <label appLabel for="case-description">Descripción *</label>
            <textarea appTextarea id="case-description" rows="3" placeholder="Describe el contexto clínico y los aspectos legales relevantes..." formControlName="description"></textarea>
            @if (form.controls.description.invalid && form.controls.description.touched) {
              <p class="text-xs text-red-500">La descripción debe tener al menos 10 caracteres</p>
            }
          </div>

          <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div class="space-y-1.5">
              <label appLabel>Prioridad *</label>
              <select appSelect formControlName="priority">
                <option value="baja">Baja</option>
                <option value="media">Media</option>
                <option value="alta">Alta</option>
                <option value="critica">Crítica</option>
              </select>
            </div>

            <div class="space-y-1.5">
              <label appLabel>Especialidad médica</label>
              <select appSelect formControlName="medicalSpecialty">
                <option value="">Sin especificar</option>
                @for (s of specialties; track s) {
                  <option [value]="s">{{ s }}</option>
                }
              </select>
            </div>
          </div>

          <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div class="space-y-1.5">
              <label appLabel for="case-event-type">Tipo de evento</label>
              <input appInput id="case-event-type" placeholder="Ej: Cirugía, consulta, diagnóstico" formControlName="eventType" />
            </div>

            <div class="space-y-1.5">
              <label appLabel>Urgencia percibida</label>
              <select appSelect formControlName="perceivedUrgency">
                <option value="">Sin especificar</option>
                <option value="baja">Baja</option>
                <option value="media">Media</option>
                <option value="alta">Alta</option>
                <option value="critica">Crítica</option>
              </select>
            </div>
          </div>

          <!-- Factores que alimentan el modelo de riesgo -->
          <div class="rounded-lg border border-blue-100 bg-blue-50/50 px-4 py-3 space-y-2">
            <p class="flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wider text-blue-700">
              <lucide-icon name="cpu" class="h-3.5 w-3.5" />
              Factores del análisis de riesgo
            </p>
            <label class="flex items-center gap-2 text-sm text-slate-700">
              <input type="checkbox" formControlName="documentationComplete" class="h-4 w-4 rounded border-slate-300 text-blue-600 focus:ring-blue-500" />
              Documentación clínica completa
            </label>
            <label class="flex items-center gap-2 text-sm text-slate-700">
              <input type="checkbox" formControlName="informedConsent" class="h-4 w-4 rounded border-slate-300 text-blue-600 focus:ring-blue-500" />
              Consentimiento informado firmado
            </label>
            <label class="flex items-center gap-2 text-sm text-slate-700">
              <input type="checkbox" formControlName="hasPriorComplaints" class="h-4 w-4 rounded border-slate-300 text-blue-600 focus:ring-blue-500" />
              Existen quejas previas
            </label>
          </div>

          <div class="space-y-1.5">
            <label appLabel for="case-notes">Notas adicionales</label>
            <textarea appTextarea id="case-notes" rows="2" placeholder="Observaciones o información adicional relevante..." formControlName="notes"></textarea>
          </div>

          <div class="border-t border-slate-100 pt-4 space-y-3">
            <p class="text-xs font-semibold text-slate-500 uppercase tracking-wider">
              Contexto simulado (sin datos identificables — Ley 29733)
            </p>

            <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <div class="space-y-1.5">
                <label appLabel for="context-code">Código de referencia</label>
                <input appInput id="context-code" placeholder="Auto (Caso-XXXXXXXX)" formControlName="contextCode" />
              </div>
              <div class="space-y-1.5">
                <label appLabel for="context-age">Edad de referencia</label>
                <input appInput id="context-age" type="number" min="0" placeholder="Ej: 45" formControlName="ageReference" />
              </div>
            </div>

            <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <div class="space-y-1.5">
                <label appLabel for="context-area">Área médica</label>
                <input appInput id="context-area" placeholder="Ej: Cirugía General" formControlName="medicalArea" />
              </div>
              <div class="space-y-1.5">
                <label appLabel for="context-date">Fecha del evento</label>
                <input appInput id="context-date" type="date" formControlName="eventDate" />
              </div>
            </div>

            <div class="space-y-1.5">
              <label appLabel for="context-summary">Resumen del contexto</label>
              <textarea appTextarea id="context-summary" rows="2" placeholder="Resumen anonimizado del caso..." formControlName="summary"></textarea>
            </div>

            <div class="space-y-1.5">
              <label appLabel for="context-factors">Factores relevantes</label>
              <input appInput id="context-factors" placeholder="Separados por coma: ej. cirugía electiva, consentimiento firmado" formControlName="relevantFactors" />
            </div>
          </div>

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
            <button appBtn type="submit" variant="primary" class="gap-2" [disabled]="createMutation.isPending()">
              @if (createMutation.isPending()) {
                <lucide-icon name="loader-2" class="h-4 w-4 animate-spin" />
              }
              Crear y analizar
            </button>
          </div>
        </form>
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
              @if (stage() >= 2 && inputs(); as vars) {
                <div class="mt-2.5 ml-10 flex flex-wrap gap-1.5">
                  <span class="rounded-full bg-white/10 px-2.5 py-0.5 text-[11px] text-slate-200 ring-1 ring-inset ring-white/10">{{ vars.specialty }}</span>
                  <span class="rounded-full bg-white/10 px-2.5 py-0.5 text-[11px] text-slate-200 ring-1 ring-inset ring-white/10">Complejidad {{ vars.complexity }}</span>
                  <span class="rounded-full bg-white/10 px-2.5 py-0.5 text-[11px] text-slate-200 ring-1 ring-inset ring-white/10">Prioridad {{ vars.priority }}</span>
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
                  }
                </div>
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
                      <p class="text-4xl font-bold tracking-tight">{{ Math.round(r.riskScore * 100) }}<span class="text-lg text-slate-400">%</span></p>
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

                  @if (topFactors(r).length > 0) {
                    <p class="mb-2 mt-4 text-[11px] font-semibold uppercase tracking-wider text-slate-400">Factores que más pesaron</p>
                    <div class="space-y-2">
                      @for (f of topFactors(r); track f.name) {
                        <div>
                          <div class="flex items-center justify-between text-xs">
                            <span class="text-slate-300">{{ factorLabels[f.name] ?? f.name }}</span>
                            <span class="font-semibold text-slate-200">{{ Math.round(f.value * 100) }}% <span class="font-normal text-slate-500">(peso {{ Math.round(f.weight * 100) }}%)</span></span>
                          </div>
                          <div class="mt-1 h-1.5 overflow-hidden rounded-full bg-white/10">
                            <div class="h-full rounded-full bg-gradient-to-r from-blue-500 to-cyan-400 transition-all duration-700"
                              [style.width.%]="Math.round(f.value * 100)"></div>
                          </div>
                        </div>
                      }
                    </div>
                  }

                  @if (r.riskLevel === 'alto' || r.riskLevel === 'critico') {
                    <div class="mt-4 flex items-start gap-2 rounded-lg bg-amber-400/10 px-3.5 py-2.5 ring-1 ring-inset ring-amber-400/25">
                      <lucide-icon name="zap" class="mt-0.5 h-4 w-4 shrink-0 text-amber-300" />
                      <p class="text-xs leading-relaxed text-amber-200">
                        Riesgo {{ r.riskLevel }}: se disparó una <span class="font-semibold">alerta automática</span> al equipo legal (n8n).
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
  private readonly mlApi = inject(MlApi);
  private readonly router = inject(Router);
  private readonly queryClient = injectQueryClient();

  protected readonly opened = signal(false);
  readonly closed = output<void>();

  protected readonly specialties = MEDICAL_SPECIALTIES;
  protected readonly serverError = signal<string | null>(null);
  protected readonly Math = Math;
  protected readonly levels = RISK_LEVELS;
  protected readonly factorLabels = FACTOR_LABELS;
  protected readonly factorCount = Object.keys(FACTOR_LABELS).length;

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
    priority: ['media', Validators.required],
    medicalSpecialty: [''],
    eventType: [''],
    perceivedUrgency: [''],
    notes: [''],
    documentationComplete: [true],
    informedConsent: [true],
    hasPriorComplaints: [false],
    contextCode: [''],
    ageReference: [''],
    medicalArea: [''],
    eventDate: [''],
    summary: [''],
    relevantFactors: [''],
  });

  protected readonly createMutation = injectMutation(() => ({
    mutationFn: () => {
      const v = this.form.getRawValue();
      const context = v.medicalArea.trim()
        ? {
            contextCode: v.contextCode.trim() || undefined,
            ageReference: v.ageReference ? Number(v.ageReference) : undefined,
            medicalArea: v.medicalArea.trim(),
            eventDate: v.eventDate || undefined,
            summary: v.summary.trim() || undefined,
            relevantFactors: v.relevantFactors.trim()
              ? v.relevantFactors.split(',').map((f) => f.trim()).filter(Boolean)
              : undefined,
          }
        : undefined;

      return this.casesApi.create({
        title: v.title.trim(),
        description: v.description.trim(),
        priority: v.priority,
        medicalSpecialty: v.medicalSpecialty || undefined,
        eventType: v.eventType.trim() || undefined,
        perceivedUrgency: v.perceivedUrgency || undefined,
        notes: v.notes.trim() || undefined,
        context,
      });
    },
    onSuccess: (created) => {
      this.queryClient.invalidateQueries({ queryKey: ['cases'] });
      this.createdId.set(created.id ?? '');
      this.createdPriority.set(created.priority ?? this.form.getRawValue().priority);
      this.startAnalysis();
    },
    onError: (err: Error) => this.serverError.set(err.message),
  }));

  /** Pipeline de análisis: variables reales del formulario → RF real (/api/ml/risk). */
  private startAnalysis(): void {
    const v = this.form.getRawValue();
    const daysSince = v.eventDate
      ? Math.max(0, Math.floor((Date.now() - new Date(v.eventDate).getTime()) / 86_400_000))
      : null;
    const complexity = v.priority === 'critica' || v.priority === 'alta' ? 'alta' : v.priority === 'media' ? 'media' : 'baja';

    this.inputs.set({
      specialty: v.medicalSpecialty || v.medicalArea.trim() || 'Medicina General',
      complexity,
      priority: v.priority,
      documentation: v.documentationComplete,
      consent: v.informedConsent,
      priorComplaints: v.hasPriorComplaints,
      daysSince,
    });

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

    this.mlApi
      .risk({
        specialty: v.medicalSpecialty || v.medicalArea.trim() || 'Medicina General',
        procedure_complexity: complexity,
        priority: v.priority,
        documentation_complete: v.documentationComplete,
        informed_consent: v.informedConsent,
        has_prior_complaints: v.hasPriorComplaints,
        time_since_incident_days: daysSince ?? undefined,
        description: v.description || '',
      })
      .then((res) => finish(res as unknown as RiskResult))
      .catch(() => finish(null));
  }

  protected topFactors(r: RiskResult): RiskFactor[] {
    return [...(r.riskFactors ?? [])].sort((a, b) => b.contribution - a.contribution).slice(0, 4);
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
    this.form.reset({ priority: 'media', documentationComplete: true, informedConsent: true, hasPriorComplaints: false });
    this.serverError.set(null);
    this.phase.set('form');
    this.stage.set(0);
    this.risk.set(null);
    this.inputs.set(null);
    this.piiFindings.set([]);
    this.piiConfirmed.set(false);
    if (close) {
      this.opened.set(false);
      this.closed.emit();
    }
  }
}
