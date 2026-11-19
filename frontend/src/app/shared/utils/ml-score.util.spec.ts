import { formatMlScore } from './ml-score.util';

describe('formatMlScore', () => {
  it('muestra el puntaje como porcentaje con dos decimales (el separador lo fija el formato es-PE)', () => {
    expect(formatMlScore(0.9952)).toMatch(/^99[.,]52%$/);
    expect(formatMlScore(0.5)).toMatch(/^50[.,]00%$/);
  });

  it('no redondea a entero: 0,9952 no se muestra como 100 %', () => {
    expect(formatMlScore(0.9952)).not.toMatch(/^100/);
  });

  it('los extremos 0 y 1 son válidos', () => {
    expect(formatMlScore(0)).toMatch(/^0[.,]00%$/);
    expect(formatMlScore(1)).toMatch(/^100[.,]00%$/);
  });

  it.each([[null], [undefined], [Number.NaN], [Infinity], [-0.01], [1.01]])(
    'un valor ausente o fuera de [0, 1] (%s) devuelve null y nunca un número inventado',
    (v) => {
      expect(formatMlScore(v as number | null | undefined)).toBeNull();
    },
  );
});
