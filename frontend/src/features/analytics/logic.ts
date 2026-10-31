/** Czysta logika modułu analityki (formatowanie, obliczenia) — testowana w logic.test.ts. */
import type { ActionLevel, GapType, Trend, Week } from './types';

/** Odmiana liczebników: 1 aplikacja, 2 aplikacje, 5 aplikacji. */
export function plural(n: number, one: string, few: string, many: string): string {
  if (n === 1) return one;
  const mod10 = n % 10;
  const mod100 = n % 100;
  return mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14) ? few : many;
}

export function count(n: number, one: string, few: string, many: string): string {
  return `${n} ${plural(n, one, few, many)}`;
}

const pctFormat = new Intl.NumberFormat('pl-PL', { maximumFractionDigits: 1 });

/** Procent po polsku, np. „66,7%”; brak wartości → „—”. */
export function formatPercent(value: number | null | undefined): string {
  if (value == null || Number.isNaN(value)) return '—';
  return `${pctFormat.format(value)}%`;
}

/** Szerokość słupka w % (0–100) jako wartość CSS. */
export function barWidth(value: number | null | undefined, max: number): string {
  if (value == null || max <= 0 || value <= 0) return '0%';
  return `${Math.min(100, Math.round((1000 * value) / max) / 10)}%`;
}

/** Parametry zapytania bez pustych wartości, np. „?from=2026-10-01”. */
export function buildQuery(params: Record<string, string | number | null | undefined>): string {
  const q = new URLSearchParams();
  for (const [k, v] of Object.entries(params)) {
    if (v !== null && v !== undefined && v !== '') q.set(k, String(v));
  }
  const s = q.toString();
  return s ? `?${s}` : '';
}

/** Data ISO przesunięta o `days` dni (bez wpływu strefy czasowej). */
export function addDaysIso(iso: string, days: number): string {
  const [y, m, d] = iso.split('-').map(Number);
  const date = new Date(Date.UTC(y, m - 1, d + days));
  return date.toISOString().slice(0, 10);
}

/** Domyślny zakres raportu: ostatnie 30 dni łącznie z dziś. */
export function defaultReportRange(today: string): { from: string; to: string } {
  return { from: addDaysIso(today, -29), to: today };
}

/** Czy zakres dat jest poprawny (puste granice są dozwolone). */
export function isValidRange(from: string, to: string): boolean {
  return !from || !to || from <= to;
}

// --- cel tygodniowy (#73) ---

export function goalText(week: Pick<Week, 'applications' | 'goal' | 'percent' | 'remaining' | 'achieved'>): string {
  // po „z N” i „brakuje N” zawsze dopełniacz: „aplikacji”
  const base = `${week.applications} z ${week.goal} aplikacji (${week.percent}%)`;
  if (week.achieved) return `Cel osiągnięty: ${base}.`;
  return `${base} — brakuje ${week.remaining} aplikacji.`;
}

export function streakText(streak: number): string {
  if (streak <= 0) return 'Seria: jeszcze bez tygodnia z osiągniętym celem — każdy tydzień to nowa szansa.';
  return `Seria: ${count(streak, 'tydzień', 'tygodnie', 'tygodni')} z rzędu z osiągniętym celem.`;
}

/** Szerokość paska postępu (maks. 100%). */
export function progressWidth(percent: number): string {
  return `${Math.max(0, Math.min(100, percent))}%`;
}

// --- „Co dalej?” (#97) ---

export const LEVEL_LABEL: Record<ActionLevel, string> = { HIGH: 'Pilne', MEDIUM: 'Ważne', LOW: 'Warto zrobić' };

export function levelTone(level: ActionLevel): 'danger' | 'warning' | 'info' {
  return level === 'HIGH' ? 'danger' : level === 'MEDIUM' ? 'warning' : 'info';
}

// --- nastrój (#76) ---

export const MOOD_LABELS = ['Bardzo źle', 'Słabo', 'Tak sobie', 'Dobrze', 'Bardzo dobrze'] as const;
export const ENERGY_LABELS = ['Bardzo niska', 'Niska', 'Średnia', 'Wysoka', 'Bardzo wysoka'] as const;
/** Ikony tylko jako dodatek — znaczenie zawsze niesie tekst. */
export const MOOD_ICONS = ['😞', '🙁', '😐', '🙂', '😄'] as const;

export function moodLabel(value: number): string {
  return MOOD_LABELS[value - 1] ?? '—';
}

export function energyLabel(value: number): string {
  return ENERGY_LABELS[value - 1] ?? '—';
}

/** Tonacja słupka nastroju/energii 1–5. */
export function scaleTone(value: number): 'danger' | 'warning' | 'success' {
  return value <= 2 ? 'danger' : value === 3 ? 'warning' : 'success';
}

// --- trendy (#75) ---

export function trendText(trend: Trend, deltaPp: number | null): string {
  const delta = deltaPp == null ? '' : ` o ${pctFormat.format(Math.abs(deltaPp))} pp`;
  switch (trend) {
    case 'UP':
      return `↑ wzrost${delta}`;
    case 'DOWN':
      return `↓ spadek${delta}`;
    case 'STABLE':
      return '→ bez większych zmian';
    default:
      return 'za mało danych do porównania';
  }
}

// --- skuteczność (#72) ---

/** Mediana dni, np. „1 dzień”, „4 dni”, „3,5 dnia”. */
export function formatDays(value: number | null | undefined): string {
  if (value == null) return '—';
  if (!Number.isInteger(value)) return `${pctFormat.format(value)} dnia`;
  return count(value, 'dzień', 'dni', 'dni');
}

// --- luki (#30) ---

export const GAP_TYPE_LABEL: Record<GapType, string> = {
  MISSING: 'brak w CV',
  SOFT_SKILL: 'kompetencja miękka',
  LEVEL: 'za mało lat / poziom',
};

/** Data i godzina z backendu (LocalDateTime, bez strefy) w zapisie „21.10.2026, 10:00” — do raportu. */
export function formatReportDateTime(value: string | null | undefined): string {
  const m = value?.match(/^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})/);
  if (!m) return value ?? '—';
  return `${m[3]}.${m[2]}.${m[1]}, ${m[4]}:${m[5]}`;
}

/** Pierwsze imię do powitania (bez imienia — pusty tekst). */
export function firstName(fullName: string | null | undefined): string {
  return fullName?.trim().split(/\s+/)[0] ?? '';
}
