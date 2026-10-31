/** Czysta logika modułu „oferty” (bez Reacta) — testowana w logic.test.ts. */
import { ApiError } from '../../api/client';
import type { JobOfferDraft } from '../../api/types';
import type { Tone } from '../../components/ui';
import type {
  DuplicateCandidate, DuplicateReport, OfferDraftDto, ParseOrigin, ParsedField, ParsedOffer, RedFlag, RiskLevel, Severity,
} from './types';

/** Szkic z parsera → wartości początkowe formularza (tylko pola JobOfferDraft). */
export function toFormInitial(d: OfferDraftDto): Partial<JobOfferDraft> {
  return {
    title: d.title ?? '',
    company: d.company,
    location: d.location,
    url: d.url,
    source: d.source,
    description: d.description,
    workMode: d.workMode,
    contractTypes: [...(d.contractTypes ?? [])],
    seniority: d.seniority,
    salaryMin: d.salaryMin,
    salaryMax: d.salaryMax,
    salaryCurrency: d.salaryCurrency ?? 'PLN',
    salaryPeriod: d.salaryPeriod ?? (d.salaryMin != null || d.salaryMax != null ? 'MONTH' : null),
    salaryGross: d.salaryGross,
    requirements: (d.requirements ?? []).map((r) => ({ ...r })),
    benefits: [...(d.benefits ?? [])],
    publishedAt: d.publishedAt,
    expiresAt: d.expiresAt,
  };
}

export type FieldStatus = 'ok' | 'review' | 'missing';

export function fieldStatus(f: ParsedField): FieldStatus {
  if (f.value == null || f.confidence <= 0) return 'missing';
  return f.review ? 'review' : 'ok';
}

export const FIELD_STATUS: Record<FieldStatus, { label: string; tone: Tone; icon: string }> = {
  ok: { label: 'rozpoznane', tone: 'success', icon: '✓' },
  review: { label: 'sprawdź', tone: 'warning', icon: '!' },
  missing: { label: 'nie znaleziono', tone: 'neutral', icon: '–' },
};

/** 0–1 → „88%”. */
export function formatPercent(v: number | null | undefined): string {
  if (v == null || Number.isNaN(v)) return '—';
  return `${Math.round(Math.min(1, Math.max(0, v)) * 100)}%`;
}

export const ORIGIN_LABEL: Record<ParseOrigin, string> = {
  text: 'wklejona treść',
  'json-ld': 'dane strukturalne strony (JSON-LD JobPosting)',
  html: 'treść strony',
};

/** „Rozpoznano 8 z 11 pól (2 do sprawdzenia) · pewność 82%”. */
export function confidenceSummary(p: ParsedOffer): string {
  const total = p.fields.length;
  const found = p.fields.filter((f) => fieldStatus(f) !== 'missing').length;
  const review = p.fields.filter((f) => fieldStatus(f) === 'review').length;
  const reviewText = review ? ` (${review} do sprawdzenia)` : '';
  return `Rozpoznano ${found} z ${total} pól${reviewText} · ogólna pewność ${formatPercent(p.confidence)}`;
}

/** Etykiety pól do sprawdzenia (niska pewność albo nie znaleziono). */
export function reviewLabels(p: ParsedOffer): string[] {
  return p.fields.filter((f) => fieldStatus(f) !== 'ok').map((f) => f.label);
}

export const SEVERITY: Record<Severity, { label: string; tone: Tone; icon: string; rank: number }> = {
  high: { label: 'Poważne', tone: 'danger', icon: '‼', rank: 0 },
  warning: { label: 'Ostrzeżenie', tone: 'warning', icon: '!', rank: 1 },
  info: { label: 'Do rozważenia', tone: 'info', icon: 'i', rank: 2 },
};

export const RISK: Record<RiskLevel, { label: string; tone: Tone; icon: string }> = {
  high: { label: 'wysokie', tone: 'danger', icon: '⛔' },
  medium: { label: 'średnie', tone: 'warning', icon: '⚠' },
  low: { label: 'niskie', tone: 'success', icon: '✓' },
};

/** Flagi od najpoważniejszych (stabilnie). */
export function sortRedFlags(flags: RedFlag[]): RedFlag[] {
  return flags
    .map((f, i) => ({ f, i }))
    .sort((a, b) => SEVERITY[a.f.severity].rank - SEVERITY[b.f.severity].rank || a.i - b.i)
    .map((x) => x.f);
}

export function countBySeverity(flags: RedFlag[]): Record<Severity, number> {
  const out: Record<Severity, number> = { high: 0, warning: 0, info: 0 };
  for (const f of flags) out[f.severity] += 1;
  return out;
}

/** „2 poważne, 1 ostrzeżenie” — krótkie podsumowanie flag. */
export function redFlagSummary(flags: RedFlag[]): string {
  if (flags.length === 0) return 'Nie wykryto czerwonych flag';
  const c = countBySeverity(flags);
  const parts: string[] = [];
  if (c.high) parts.push(`${c.high} ${plural(c.high, 'poważna', 'poważne', 'poważnych')}`);
  if (c.warning) parts.push(`${c.warning} ${plural(c.warning, 'ostrzeżenie', 'ostrzeżenia', 'ostrzeżeń')}`);
  if (c.info) parts.push(`${c.info} do rozważenia`);
  return parts.join(', ');
}

/** Polska odmiana liczebnika: 1 flaga, 2 flagi, 5 flag, 22 flagi, 12 flag. */
export function plural(n: number, one: string, few: string, many: string): string {
  if (n === 1) return one;
  const mod10 = n % 10;
  const mod100 = n % 100;
  return mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14) ? few : many;
}

export function isHttpUrl(s: string): boolean {
  const t = s.trim();
  if (!/^https?:\/\//i.test(t)) return false;
  try {
    const u = new URL(t);
    return (u.protocol === 'http:' || u.protocol === 'https:') && u.hostname.length > 0;
  } catch {
    return false;
  }
}

export const PASTE_HINT =
  'Otwórz ogłoszenie w przeglądarce, zaznacz i skopiuj jego treść, a potem wklej ją w zakładce „Wklej treść”.';

export interface ImportErrorView {
  message: string;
  hint: string | null;
  /** czy zaproponować przejście do wklejania treści */
  suggestPaste: boolean;
}

/** Błąd importu z URL → komunikat i podpowiedź (np. portal blokuje pobieranie → wklej treść). */
export function describeImportError(e: unknown): ImportErrorView {
  if (e instanceof ApiError) {
    if (e.status === 422) {
      return { message: e.message, hint: e.details.find((d) => d.length > 20) ?? PASTE_HINT, suggestPaste: true };
    }
    if (e.status === 400) {
      return { message: e.message, hint: 'Sprawdź adres albo wklej treść ogłoszenia.', suggestPaste: true };
    }
    if (e.status === 0) {
      return { message: e.message, hint: null, suggestPaste: false };
    }
    return { message: e.message || 'Nie udało się pobrać ogłoszenia', hint: PASTE_HINT, suggestPaste: true };
  }
  return { message: e instanceof Error ? e.message : 'Nie udało się pobrać ogłoszenia', hint: PASTE_HINT, suggestPaste: true };
}

/** Kandydaci powyżej progu duplikatu (do ostrzeżenia przed zapisem). */
export function likelyDuplicates(report: DuplicateReport | null | undefined): DuplicateCandidate[] {
  return (report?.candidates ?? []).filter((c) => c.duplicate);
}

/** Nawigacja klawiaturą po zakładkach (strzałki, Home, End). Zwraca nowy indeks albo null. */
export function nextTabIndex(current: number, key: string, count: number): number | null {
  switch (key) {
    case 'ArrowRight':
    case 'ArrowDown':
      return (current + 1) % count;
    case 'ArrowLeft':
    case 'ArrowUp':
      return (current - 1 + count) % count;
    case 'Home':
      return 0;
    case 'End':
      return count - 1;
    default:
      return null;
  }
}

/** Komunikat dla czytnika ekranu po rozpoznaniu pól. */
export function parseAnnouncement(p: ParsedOffer): string {
  const review = p.fields.filter((f) => fieldStatus(f) !== 'ok').length;
  return review
    ? `Rozpoznano pola ogłoszenia. ${review} ${plural(review, 'pole wymaga', 'pola wymagają', 'pól wymaga')} sprawdzenia.`
    : 'Rozpoznano pola ogłoszenia. Sprawdź je i zapisz ofertę.';
}
