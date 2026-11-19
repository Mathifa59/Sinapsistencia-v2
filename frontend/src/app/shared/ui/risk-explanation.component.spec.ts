import { TestBed } from '@angular/core/testing';
import { RISK_FACTOR_LABELS, RISK_INPUTS_NOTE, RiskExplanationComponent } from './risk-explanation.component';
import { provideTestIcons } from '../../testing/test-icons';

const factor = (name: string, weight: number, value: number, description = 'desc') => ({
  name,
  weight,
  value,
  contribution: +(weight * value).toFixed(4),
  description,
});

const TODOS = [
  factor('specialty_risk', 0.22, 0.45),
  factor('procedure_complexity', 0.19, 1.0),
  factor('priority', 0.17, 0.67),
  factor('documentation', 0.18, 1.0),
  factor('informed_consent', 0.17, 0.0),
  factor('prior_complaints', 0.12, 0.0),
  factor('time_factor', 0.002, 0.08),
];

function montar(json: string | null, inputs: Record<string, unknown> | null = null) {
  TestBed.configureTestingModule({ providers: [provideTestIcons()] });
  const fixture = TestBed.createComponent(RiskExplanationComponent);
  fixture.componentRef.setInput('riskFactorsJson', json);
  if (inputs) fixture.componentRef.setInput('inputSnapshot', { inputs } as never);
  fixture.detectChanges();
  return fixture.nativeElement as HTMLElement;
}

describe('RiskExplanationComponent', () => {
  it('sin desglose dice que el modelo no lo devolvió, no inventa factores', () => {
    const el = montar(null);
    expect(el.textContent).toContain('El modelo no devolvió desglose');
    expect(el.textContent).not.toContain('Riesgo por especialidad');
  });

  it.each([['{no es json'], ['{"a":1}'], ['[]']])('un JSON inválido o vacío (%s) se trata como sin desglose', (json) => {
    expect(montar(json).textContent).toContain('El modelo no devolvió desglose');
  });

  it('muestra los siete factores con su etiqueta en español', () => {
    const el = montar(JSON.stringify(TODOS));
    for (const etiqueta of Object.values(RISK_FACTOR_LABELS)) {
      expect(el.textContent).toContain(etiqueta);
    }
    expect(el.textContent).not.toContain('Sin dato en la respuesta del modelo');
  });

  it('ordena los factores presentes por contribución, de mayor a menor', () => {
    const texto = montar(JSON.stringify(TODOS)).textContent ?? '';
    expect(texto.indexOf('Complejidad del procedimiento')).toBeLessThan(texto.indexOf('Historial de quejas'));
    expect(texto.indexOf('Estado de la documentación')).toBeLessThan(texto.indexOf('Tiempo desde el incidente'));
  });

  it('un factor que el API no envió se marca como «sin dato» en lugar de omitirse', () => {
    const sinTiempo = TODOS.filter((f) => f.name !== 'time_factor');
    const el = montar(JSON.stringify(sinTiempo));
    expect(el.textContent).toContain('Tiempo desde el incidente');
    expect(el.textContent).toContain('Sin dato en la respuesta del modelo');
  });

  it('descarta los factores con números inválidos', () => {
    const roto = [...TODOS.slice(0, 6), { name: 'time_factor', weight: 'x', value: null, contribution: 1, description: '' }];
    expect(montar(JSON.stringify(roto)).textContent).toContain('Sin dato en la respuesta del modelo');
  });

  it('sin fecha de evento muestra «Sin fecha» y no un 0 % medido', () => {
    const el = montar(JSON.stringify(TODOS), { time_since_incident_days: null });
    expect(el.textContent).toContain('Sin fecha');
    expect(el.textContent).toContain('El modelo asumió 0 días por defecto.');
  });

  it('con fecha de evento no aparece «Sin fecha»', () => {
    const el = montar(JSON.stringify(TODOS), { time_since_incident_days: 30 });
    expect(el.textContent).not.toContain('Sin fecha');
  });

  it('siempre aclara que el texto libre no entra al cálculo de riesgo', () => {
    const el = montar(JSON.stringify(TODOS));
    expect(el.textContent).toContain(RISK_INPUTS_NOTE);
    expect(RISK_INPUTS_NOTE).toContain('no forman parte de este cálculo');
  });

  it('avisa que los productos no reconstruyen el puntaje (es una aproximación)', () => {
    const el = montar(JSON.stringify(TODOS));
    expect(el.textContent).toContain('(aproximación)');
    expect(el.textContent).toContain('no para reconstruir el score');
  });
});
