import { formatDate, formatDateTime } from './cn';

/** Referencia fija en UTC: el día de calendario esperado, sin depender de la zona de la máquina. */
function diaDeCalendario(y: number, m: number, d: number): string {
  return new Intl.DateTimeFormat('es-PE', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
    timeZone: 'UTC',
  }).format(new Date(Date.UTC(y, m - 1, d)));
}

describe('formatDate', () => {
  // Las cuatro fechas del informe de validación con médicos: se mostraban un día antes en Lima (UTC-5).
  it.each([
    ['2026-01-08', 2026, 1, 8],
    ['2026-05-12', 2026, 5, 12],
    ['2026-10-20', 2026, 10, 20],
    ['2022-10-10', 2022, 10, 10],
  ])('una fecha pura %s se muestra como el mismo día de calendario', (iso, y, m, d) => {
    expect(formatDate(iso)).toBe(diaDeCalendario(y, m, d));
  });

  it('un instante con hora sigue convirtiéndose a la zona local (createdAt/updatedAt)', () => {
    const instante = '2026-01-08T03:00:00Z';
    const esperado = new Intl.DateTimeFormat('es-PE', { day: '2-digit', month: 'short', year: 'numeric' }).format(
      new Date(instante),
    );
    expect(formatDate(instante)).toBe(esperado);
  });

  it('un Date recibido se formatea en la zona local, igual que antes', () => {
    const d = new Date(2026, 0, 8, 12, 0, 0);
    expect(formatDate(d)).toBe(diaDeCalendario(2026, 1, 8));
  });

  it.each([[null], [undefined], [''], ['no-es-fecha'], ['2026-13-45'], ['2026-02-30']])(
    'un valor vacío o inválido (%s) se muestra como guion',
    (valor) => {
      expect(formatDate(valor as string | null | undefined)).toBe('—');
    },
  );
});

describe('formatDateTime', () => {
  it('una fecha pura no retrocede un día', () => {
    expect(formatDateTime('2026-01-08')).toContain('08');
  });

  it('un valor vacío se muestra como guion', () => {
    expect(formatDateTime(null)).toBe('—');
  });
});
