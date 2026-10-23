import { Component, computed, input } from '@angular/core';
import { LucideAngularModule } from 'lucide-angular';
import type { MlInputSnapshot } from '../../core/api/cases.api';

/** Qué mide el porcentaje de riesgo (etiqueta corta junto al número). */
export const RISK_MEANING_LABEL = 'Estimación de prioridad legal según el modelo, no gravedad clínica.';

/**
 * Qué entra y qué no entra al cálculo. Verificado contra el código: el RF solo recibe las 7 variables
 * estructuradas (CaseClassificationService + ml-service/app/risk/model.py); el texto libre del caso solo
 * alimenta el matching de abogados.
 */
export const RISK_INPUTS_NOTE =
  'Este cálculo se basa en los campos estructurados del formulario: especialidad, complejidad del procedimiento, ' +
  'urgencia percibida, documentación, consentimiento, quejas previas y tiempo transcurrido desde el evento. ' +
  'El título, la descripción y el tipo de evento no forman parte de este cálculo; sí se usan para recomendar abogados.';

/** Etiquetas de los 7 factores que devuelve el API (`risk_factors[].name`). El orden es el de respaldo. */
export const RISK_FACTOR_LABELS: Record<string, string> = {
  specialty_risk: 'Riesgo por especialidad',
  procedure_complexity: 'Complejidad del procedimiento',
  priority: 'Urgencia percibida',
  documentation: 'Estado de la documentación',
  informed_consent: 'Consentimiento informado',
  prior_complaints: 'Historial de quejas',
  time_factor: 'Tiempo desde el incidente',
};

/** El API describe este factor como "Prioridad asignada a la consulta", pero lo que entra al modelo es la urgencia declarada. */
const HINT_OVERRIDES: Record<string, string> = {
  priority: 'Declarada por el médico en el formulario',
};

interface ApiFactor {
  name?: unknown;
  weight?: unknown;
  value?: unknown;
  contribution?: unknown;
  description?: unknown;
}

interface FactorRow {
  name: string;
  label: string;
  present: boolean;
  value: number;
  weight: number;
  contribution: number;
  hint: string;
  /** Sin fecha de evento: el valor 0 es un supuesto del modelo, no una medición. */
  noDate: boolean;
}

const NO_EVENT_DATE_HINT = 'El modelo asumió 0 días por defecto.';

const NUMBER_2 = new Intl.NumberFormat('es-PE', { minimumFractionDigits: 2, maximumFractionDigits: 2 });

const THEMES = {
  light: {
    title: 'text-slate-500',
    label: 'text-slate-700',
    value: 'text-slate-800',
    muted: 'text-slate-400',
    track: 'bg-slate-100',
    border: 'border-slate-100',
    pill: 'border-slate-200 bg-slate-100 text-slate-500',
  },
  dark: {
    title: 'text-slate-400',
    label: 'text-slate-300',
    value: 'text-slate-200',
    muted: 'text-slate-500',
    track: 'bg-white/10',
    border: 'border-white/10',
    pill: 'border-white/10 bg-white/10 text-slate-300',
  },
} as const;

function isFinitePositiveOrZero(n: unknown): n is number {
  return typeof n === 'number' && Number.isFinite(n) && n >= 0;
}

/**
 * Explicabilidad del score de riesgo: desglose por factor (lo que el API ya devuelve en `risk_factors`)
 * + nota sobre qué campos entran al cálculo. No inventa datos: un factor que no viene en la respuesta
 * se muestra como "sin dato", y si no viene ninguno se dice explícitamente.
 */
@Component({
  selector: 'app-risk-explanation',
  imports: [LucideAngularModule],
  template: `
    <div class="space-y-3">
      <details class="group" [open]="open()">
        <summary class="flex cursor-pointer list-none items-center justify-between gap-2">
          <span class="text-[11px] font-semibold uppercase tracking-wider {{ t().title }}">
            Desglose por factor <span class="font-normal normal-case tracking-normal">(aproximación)</span>
          </span>
          <lucide-icon name="chevron-down" class="h-3.5 w-3.5 transition-transform group-open:rotate-180 {{ t().muted }}" />
        </summary>

        @if (rows().length === 0) {
          <p class="mt-3 text-xs leading-relaxed {{ t().muted }}">
            El modelo no devolvió desglose por factores para esta clasificación.
          </p>
        } @else {
          <p class="mt-2 text-[11px] leading-relaxed {{ t().muted }}">
            Peso del factor × valor en este caso. El peso es la importancia global del factor en el modelo; el valor
            indica cuánto presenta el factor este caso (0% a 100%). Los productos no suman el porcentaje de
            riesgo: sirven para comparar qué factores pesan más en este caso, no para reconstruir el score.
          </p>
          <div class="mt-3 space-y-3">
            @for (r of rows(); track r.name) {
              <div>
                <div class="flex items-baseline justify-between gap-3 text-xs">
                  <span class="{{ t().label }}">{{ r.label }}</span>
                  @if (!r.present) {
                    <span class="italic {{ t().muted }}">Sin dato en la respuesta del modelo</span>
                  } @else if (r.noDate) {
                    <span class="rounded-full border px-2 text-[11px] font-medium {{ t().pill }}">Sin fecha</span>
                  } @else {
                    <span class="font-semibold {{ t().value }}">{{ pct(r.value) }}</span>
                  }
                </div>
                @if (r.present) {
                  @if (!r.noDate) {
                    <div class="mt-1 h-1.5 overflow-hidden rounded-full {{ t().track }}">
                      <div class="h-full rounded-full bg-gradient-to-r from-blue-500 to-cyan-400"
                        [style.width.%]="r.value * 100"></div>
                    </div>
                  }
                  <div class="mt-1 flex flex-wrap items-baseline justify-between gap-x-3 text-[11px] {{ t().muted }}">
                    <span>{{ r.noDate ? noEventDateHint : r.hint }}</span>
                    <span>peso {{ pct(r.weight) }} × valor {{ pct(r.value) }} ≈ {{ num2(r.contribution) }}</span>
                  </div>
                }
              </div>
            }
          </div>
        }
      </details>

      <p class="flex items-start gap-1.5 border-t pt-3 text-[11px] leading-relaxed {{ t().border }} {{ t().muted }}">
        <lucide-icon name="info" class="mt-px h-3.5 w-3.5 shrink-0" />
        <span>{{ inputsNote }}</span>
      </p>
    </div>
  `,
})
export class RiskExplanationComponent {
  /** `risk_factors` tal como lo persiste el backend (JSON). */
  readonly riskFactorsJson = input<string | null | undefined>(null);
  readonly variant = input<'light' | 'dark'>('light');
  readonly open = input(true);
  /** Fotografía de las entradas evaluadas: permite distinguir "sin fecha de evento" de un 0% medido. */
  readonly inputSnapshot = input<MlInputSnapshot | null | undefined>(null);

  protected readonly inputsNote = RISK_INPUTS_NOTE;
  protected readonly noEventDateHint = NO_EVENT_DATE_HINT;
  /** Solo se afirma "sin fecha" si la fotografía existe y no trae `time_since_incident_days`. */
  private readonly noEventDate = computed(() => {
    const inputs = this.inputSnapshot()?.inputs;
    return !!inputs && inputs.time_since_incident_days == null;
  });
  protected readonly t = computed(() => THEMES[this.variant()]);

  protected readonly rows = computed<FactorRow[]>(() => {
    const parsed = this.parse(this.riskFactorsJson());
    if (parsed.length === 0) {
      return [];
    }
    const byName = new Map(parsed.map((f) => [f.name, f]));
    const known = Object.keys(RISK_FACTOR_LABELS);

    const present = parsed.map((f) => this.toRow(f.name, f));
    present.sort((a, b) => b.contribution - a.contribution);

    // Un factor esperado que el API no envió se muestra como "sin dato", nunca se omite en silencio.
    const missing = known
      .filter((name) => !byName.has(name))
      .map<FactorRow>((name) => ({
        name,
        label: RISK_FACTOR_LABELS[name],
        present: false,
        value: 0,
        weight: 0,
        contribution: 0,
        hint: '',
        noDate: false,
      }));
    return [...present, ...missing];
  });

  protected pct(v: number): string {
    return `${Math.round(v * 100)}%`;
  }

  protected num2(v: number): string {
    return NUMBER_2.format(v);
  }

  private toRow(name: string, f: { description: string; weight: number; value: number; contribution: number }): FactorRow {
    return {
      name,
      label: RISK_FACTOR_LABELS[name] ?? name,
      present: true,
      value: f.value,
      weight: f.weight,
      contribution: f.contribution,
      hint: HINT_OVERRIDES[name] ?? f.description,
      noDate: name === 'time_factor' && this.noEventDate(),
    };
  }

  /** Solo factores completos y con números válidos; lo demás cuenta como "no vino". */
  private parse(json: string | null | undefined): { name: string; description: string; weight: number; value: number; contribution: number }[] {
    if (!json) {
      return [];
    }
    let raw: unknown;
    try {
      raw = JSON.parse(json);
    } catch {
      return [];
    }
    if (!Array.isArray(raw)) {
      return [];
    }
    const out: { name: string; description: string; weight: number; value: number; contribution: number }[] = [];
    for (const item of raw as ApiFactor[]) {
      if (
        item &&
        typeof item.name === 'string' &&
        isFinitePositiveOrZero(item.weight) &&
        isFinitePositiveOrZero(item.value) &&
        item.value <= 1 &&
        isFinitePositiveOrZero(item.contribution)
      ) {
        out.push({
          name: item.name,
          description: typeof item.description === 'string' ? item.description : '',
          weight: item.weight,
          value: item.value,
          contribution: item.contribution,
        });
      }
    }
    return out;
  }
}
