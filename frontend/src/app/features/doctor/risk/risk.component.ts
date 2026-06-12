import { Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { LucideAngularModule } from 'lucide-angular';
import { MlApi } from '../../../core/api/ml.api';
import { BtnDirective } from '../../../shared/ui/button.directive';
import { InputDirective, LabelDirective, TextareaDirective, SelectDirective } from '../../../shared/ui/field.directives';
import { MEDICAL_SPECIALTIES } from '../../../shared/constants';
import { cn } from '../../../shared/utils/cn';

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
  specialtyRiskBaseline: number;
  modelVersion: string;
}

const RISK_LEVEL_CONFIG: Record<RiskResult['riskLevel'], { label: string; color: string; bar: string; icon: string }> = {
  bajo: { label: 'Riesgo Bajo', color: 'bg-emerald-100 text-emerald-800 border-emerald-200', bar: 'bg-emerald-500', icon: 'shield-check' },
  moderado: { label: 'Riesgo Moderado', color: 'bg-yellow-100 text-yellow-800 border-yellow-200', bar: 'bg-yellow-500', icon: 'alert-triangle' },
  alto: { label: 'Riesgo Alto', color: 'bg-orange-100 text-orange-800 border-orange-200', bar: 'bg-orange-500', icon: 'shield-alert' },
  critico: { label: 'Riesgo Crítico', color: 'bg-red-100 text-red-800 border-red-200', bar: 'bg-red-500', icon: 'x-circle' },
};

const FACTOR_LABELS: Record<string, string> = {
  specialty_risk: 'Riesgo por especialidad',
  procedure_complexity: 'Complejidad del procedimiento',
  documentation: 'Estado de la documentación',
  informed_consent: 'Consentimiento informado',
  prior_complaints: 'Historial de quejas',
  time_factor: 'Tiempo desde el incidente',
  priority: 'Prioridad de la consulta',
};

/** Réplica de app/doctor/risk/page.tsx (POST /api/ml/risk). */
@Component({
  selector: 'app-doctor-risk',
  imports: [ReactiveFormsModule, LucideAngularModule, BtnDirective, InputDirective, LabelDirective, TextareaDirective, SelectDirective],
  template: `
    <div class="max-w-4xl mx-auto space-y-6">
      <div>
        <h1 class="text-2xl font-bold text-slate-900">Evaluación de Riesgo Médico-Legal</h1>
        <p class="text-slate-500 text-sm mt-1">Analiza el nivel de riesgo legal de una consulta basándose en factores clínicos y procesales</p>
      </div>

      <div class="grid lg:grid-cols-2 gap-6 items-start">
        <div class="bg-white rounded-xl border border-slate-200 shadow-sm p-6 space-y-5">
          <h2 class="text-base font-semibold text-slate-900 flex items-center gap-2">
            <lucide-icon name="activity" class="h-4 w-4 text-slate-600" />
            Datos de la consulta
          </h2>

          <form [formGroup]="form" (ngSubmit)="onSubmit()" class="space-y-4">
            <div class="space-y-1.5">
              <label appLabel>Especialidad médica *</label>
              <select appSelect formControlName="specialty">
                <option value="">Selecciona especialidad</option>
                @for (s of specialties; track s) {
                  <option [value]="s">{{ s }}</option>
                }
              </select>
              @if (form.controls.specialty.invalid && form.controls.specialty.touched) {
                <p class="text-xs text-red-600">La especialidad es requerida</p>
              }
            </div>

            <div class="grid grid-cols-2 gap-3">
              <div class="space-y-1.5">
                <label appLabel>Complejidad del procedimiento</label>
                <select appSelect formControlName="procedure_complexity">
                  <option value="baja">Baja</option>
                  <option value="media">Media</option>
                  <option value="alta">Alta</option>
                </select>
              </div>

              <div class="space-y-1.5">
                <label appLabel>Prioridad de la consulta</label>
                <select appSelect formControlName="priority">
                  <option value="baja">Baja</option>
                  <option value="media">Media</option>
                  <option value="alta">Alta</option>
                  <option value="critica">Crítica</option>
                </select>
              </div>
            </div>

            <div class="space-y-1.5">
              <label appLabel>Días transcurridos desde el incidente (opcional)</label>
              <input appInput type="number" min="0" placeholder="Ej: 30" formControlName="time_since_incident_days" />
            </div>

            <div class="space-y-3 py-1">
              <p class="text-sm font-medium text-slate-700">Estado de la consulta</p>
              <div class="space-y-2.5">
                <label class="flex items-center gap-3 cursor-pointer group">
                  <input type="checkbox" formControlName="documentation_complete" class="h-4 w-4 rounded border-slate-300 text-blue-600 focus:ring-blue-500" />
                  <span class="text-sm text-slate-700 group-hover:text-slate-900">Documentación clínica completa</span>
                </label>
                <label class="flex items-center gap-3 cursor-pointer group">
                  <input type="checkbox" formControlName="informed_consent" class="h-4 w-4 rounded border-slate-300 text-blue-600 focus:ring-blue-500" />
                  <span class="text-sm text-slate-700 group-hover:text-slate-900">Consentimiento informado firmado</span>
                </label>
                <label class="flex items-center gap-3 cursor-pointer group">
                  <input type="checkbox" formControlName="has_prior_complaints" class="h-4 w-4 rounded border-slate-300 text-blue-600 focus:ring-blue-500" />
                  <span class="text-sm text-slate-700 group-hover:text-slate-900">Existen quejas previas contra el médico</span>
                </label>
              </div>
            </div>

            <div class="space-y-1.5">
              <label appLabel>Descripción de la consulta (opcional)</label>
              <textarea appTextarea rows="3" placeholder="Describe brevemente el contexto de la consulta..." formControlName="description"></textarea>
            </div>

            @if (error()) {
              <div class="flex items-start gap-2 text-sm text-red-700 bg-red-50 border border-red-100 rounded-md px-3 py-2.5">
                <lucide-icon name="x-circle" class="h-4 w-4 shrink-0 mt-0.5" />
                {{ error() }}
              </div>
            }

            <div class="flex gap-2 pt-1">
              <button appBtn type="submit" variant="primary" class="flex-1 gap-2" [disabled]="isLoading()">
                @if (isLoading()) {
                  <lucide-icon name="loader-2" class="h-4 w-4 animate-spin" />Evaluando...
                } @else {
                  <lucide-icon name="alert-triangle" class="h-4 w-4" />Evaluar riesgo
                }
              </button>
              @if (result()) {
                <button appBtn type="button" variant="secondary" class="gap-2" (click)="handleReset()">
                  <lucide-icon name="refresh-cw" class="h-4 w-4" />Nueva
                </button>
              }
            </div>
          </form>
        </div>

        @if (result() && levelCfg()) {
          @let r = result()!;
          @let cfg = levelCfg()!;
          <div class="space-y-4">
            <div class="bg-white rounded-xl border border-slate-200 shadow-sm p-6">
              <div class="flex items-center justify-between mb-4">
                <h2 class="text-base font-semibold text-slate-900">Resultado</h2>
                <span [class]="cn('flex items-center gap-1.5 text-sm font-semibold px-3 py-1 rounded-full border', cfg.color)">
                  <lucide-icon [name]="cfg.icon" class="h-4 w-4" />
                  {{ cfg.label }}
                </span>
              </div>

              <div class="text-center py-4">
                <p class="text-6xl font-black text-slate-900">
                  {{ Math.round(r.riskScore * 100) }}<span class="text-2xl font-semibold text-slate-400">%</span>
                </p>
                <p class="text-sm text-slate-500 mt-1">Score de riesgo global</p>
              </div>

              <div class="mt-2">
                <div class="flex justify-between text-xs text-slate-400 mb-1.5">
                  <span>Bajo</span><span>Moderado</span><span>Alto</span><span>Crítico</span>
                </div>
                <div class="h-3 rounded-full bg-slate-100 overflow-hidden relative">
                  <div class="absolute inset-0 flex">
                    <div class="flex-1 border-r border-white/60"></div>
                    <div class="flex-1 border-r border-white/60"></div>
                    <div class="flex-1 border-r border-white/60"></div>
                    <div class="flex-1"></div>
                  </div>
                  <div [class]="cn('h-full rounded-full transition-all duration-700', cfg.bar)" [style.width.%]="Math.round(r.riskScore * 100)"></div>
                </div>
                <div class="flex justify-between text-xs text-slate-400 mt-1">
                  <span>0%</span><span>25%</span><span>50%</span><span>75%</span><span>100%</span>
                </div>
              </div>

              <div class="mt-4 pt-4 border-t border-slate-100 flex items-center justify-between text-xs text-slate-400">
                <span>Riesgo base de la especialidad: {{ Math.round(r.specialtyRiskBaseline * 100) }}%</span>
                <span>Modelo v{{ r.modelVersion }}</span>
              </div>
            </div>

            <div class="bg-white rounded-xl border border-slate-200 shadow-sm p-6">
              <h3 class="text-sm font-semibold text-slate-900 mb-4 flex items-center gap-2">
                <lucide-icon name="file-warning" class="h-4 w-4 text-slate-500" />
                Desglose por factor
              </h3>
              <div class="space-y-3.5">
                @for (factor of sortedFactors(r.riskFactors); track factor.name) {
                  <div>
                    <div class="flex items-center justify-between mb-1">
                      <span class="text-xs font-medium text-slate-700">{{ factorLabels[factor.name] }}</span>
                      <span class="text-xs tabular-nums text-slate-500">
                        {{ Math.round(factor.value * 100) }}% <span class="text-slate-400">(peso {{ Math.round(factor.weight * 100) }}%)</span>
                      </span>
                    </div>
                    <div class="h-2 rounded-full bg-slate-100 overflow-hidden">
                      <div class="h-full rounded-full bg-blue-500 transition-all duration-500" [style.width.%]="Math.round(factor.value * 100)"></div>
                    </div>
                    @if (factor.description) {
                      <p class="text-xs text-slate-400 mt-0.5 leading-relaxed">{{ factor.description }}</p>
                    }
                  </div>
                }
              </div>
            </div>
          </div>
        } @else {
          <div class="bg-slate-50 rounded-xl border border-dashed border-slate-200 flex flex-col items-center justify-center p-12 text-center min-h-[320px]">
            <div class="h-12 w-12 rounded-full bg-slate-100 flex items-center justify-center mb-4">
              <lucide-icon name="alert-triangle" class="h-6 w-6 text-slate-400" />
            </div>
            <p class="text-sm font-medium text-slate-600">Sin evaluación aún</p>
            <p class="text-xs text-slate-400 mt-1">Completa el formulario y haz clic en "Evaluar riesgo"</p>
          </div>
        }
      </div>

      @if (result() && result()!.recommendations.length > 0) {
        <div class="bg-white rounded-xl border border-slate-200 shadow-sm p-6">
          <h3 class="text-sm font-semibold text-slate-900 mb-4 flex items-center gap-2">
            <lucide-icon name="info" class="h-4 w-4 text-blue-500" />
            Recomendaciones para mitigar el riesgo
          </h3>
          <ul class="space-y-2.5">
            @for (rec of result()!.recommendations; track rec) {
              <li class="flex items-start gap-2.5 text-sm text-slate-700">
                <lucide-icon name="check-circle-2" class="h-4 w-4 text-emerald-500 mt-0.5 shrink-0" />
                {{ rec }}
              </li>
            }
          </ul>
        </div>
      }
    </div>
  `,
})
export class DoctorRiskComponent {
  private readonly fb = inject(FormBuilder);
  private readonly mlApi = inject(MlApi);

  protected readonly specialties = MEDICAL_SPECIALTIES;
  protected readonly factorLabels = FACTOR_LABELS;
  protected readonly cn = cn;
  protected readonly Math = Math;

  protected readonly isLoading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly result = signal<RiskResult | null>(null);

  protected readonly levelCfg = computed(() => {
    const r = this.result();
    return r ? RISK_LEVEL_CONFIG[r.riskLevel] ?? RISK_LEVEL_CONFIG.moderado : null;
  });

  protected readonly form = this.fb.nonNullable.group({
    specialty: ['', Validators.required],
    procedure_complexity: ['media'],
    priority: ['media'],
    time_since_incident_days: [''],
    documentation_complete: [true],
    informed_consent: [true],
    has_prior_complaints: [false],
    description: [''],
  });

  protected sortedFactors(factors: RiskFactor[]): RiskFactor[] {
    return [...factors].sort((a, b) => b.contribution - a.contribution);
  }

  protected async onSubmit(): Promise<void> {
    this.error.set(null);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    this.isLoading.set(true);
    try {
      const payload = {
        specialty: v.specialty,
        procedure_complexity: v.procedure_complexity,
        priority: v.priority,
        documentation_complete: v.documentation_complete,
        informed_consent: v.informed_consent,
        has_prior_complaints: v.has_prior_complaints,
        time_since_incident_days: v.time_since_incident_days ? Number(v.time_since_incident_days) : undefined,
        description: v.description || '',
      };
      const res = await this.mlApi.risk(payload);
      this.result.set(res as unknown as RiskResult);
    } catch {
      this.error.set('No se pudo conectar con el servicio de evaluación. Verifica que el servicio ML esté activo (puerto 8000).');
    } finally {
      this.isLoading.set(false);
    }
  }

  protected handleReset(): void {
    this.form.reset({
      specialty: '',
      procedure_complexity: 'media',
      priority: 'media',
      time_since_incident_days: '',
      documentation_complete: true,
      informed_consent: true,
      has_prior_complaints: false,
      description: '',
    });
    this.result.set(null);
    this.error.set(null);
  }
}
