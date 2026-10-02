/**
 * H-04: formateador único para puntuaciones del modelo de riesgo (0–1). Antes
 * cada pantalla redondeaba a entero por su cuenta (`Math.round(score * 100)`),
 * perdiendo precisión frente al valor real que persiste el backend
 * (ej. 0.9952 se mostraba como "100%" en vez de "99,52%").
 */
const ML_SCORE_FORMATTER = new Intl.NumberFormat('es-PE', {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});

/**
 * Formatea una puntuación de riesgo [0,1] como porcentaje con dos decimales.
 * `null`/`undefined`/no finito/fuera de [0,1] devuelven `null` — el llamador
 * decide cómo representar la ausencia (nunca NaN ni un cero inventado).
 */
export function formatMlScore(value: number | null | undefined): string | null {
  if (value == null || !Number.isFinite(value) || value < 0 || value > 1) {
    return null;
  }
  return `${ML_SCORE_FORMATTER.format(value * 100)}%`;
}
