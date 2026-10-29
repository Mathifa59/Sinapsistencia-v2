import { type ClassValue, clsx } from 'clsx';
import { twMerge } from 'tailwind-merge';

/** Igual que el cn() del legacy: clsx + tailwind-merge. */
export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}

const DATE_ONLY = /^(\d{4})-(\d{2})-(\d{2})$/;

/**
 * Convierte a Date lo que llega de la API. Una fecha pura (`2026-01-08`, el LocalDate del
 * backend) es un día de calendario, no un instante: `new Date('2026-01-08')` la lee como
 * medianoche UTC y, al formatearla en una zona al oeste de UTC (Lima, UTC-5), mostraba el día
 * anterior. Se construye como medianoche local para que se muestre el mismo día guardado.
 * Un instante (con hora, p. ej. createdAt) sí se convierte a la zona local.
 */
function toDate(date: string | Date): Date {
  const parts = typeof date === 'string' ? DATE_ONLY.exec(date) : null;
  if (!parts) return new Date(date);
  const [year, month, day] = [Number(parts[1]), Number(parts[2]), Number(parts[3])];
  const d = new Date(year, month - 1, day);
  // Un día inexistente (2026-02-30) no debe desbordar al mes siguiente.
  const valid = d.getFullYear() === year && d.getMonth() === month - 1 && d.getDate() === day;
  return valid ? d : new Date(NaN);
}

export function formatDate(date: string | Date | null | undefined): string {
  if (!date) return '—';
  const d = toDate(date);
  if (isNaN(d.getTime())) return '—';
  return new Intl.DateTimeFormat('es-PE', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
  }).format(d);
}

export function formatDateTime(date: string | Date | null | undefined): string {
  if (!date) return '—';
  const d = toDate(date);
  if (isNaN(d.getTime())) return '—';
  return new Intl.DateTimeFormat('es-PE', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  }).format(d);
}

export function getInitials(name: string): string {
  return name
    .split(' ')
    .map((n) => n[0])
    .slice(0, 2)
    .join('')
    .toUpperCase();
}
