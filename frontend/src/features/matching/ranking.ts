/**
 * Czysta logika strony ofert: filtrowanie, sortowanie, komunikaty i konwersje zapisanych wyszukiwań.
 * Semantyka filtrów jest taka sama jak w backendzie (`RankingQuery.matches`, `RankingSort`).
 */
import type { OfferStatus, WorkMode } from '../../api/types';
import { OFFER_STATUS, WORK_MODE, formatMoney } from '../../api/labels';
import type { RankedOffer, RankingFilters, RankingSort, SavedSearch, SavedSearchRequest } from './types';

export const ALL_STATUSES: OfferStatus[] = ['NEW', 'SAVED', 'REJECTED', 'ARCHIVED'];
export const ALL_WORK_MODES: WorkMode[] = ['REMOTE', 'HYBRID', 'ONSITE'];
export const DEFAULT_STATUSES: OfferStatus[] = ['NEW', 'SAVED'];

export const SORT_LABEL: Record<RankingSort, string> = {
  RANK: 'Ranking (dopasowanie × preferencje)',
  MATCH: 'Dopasowanie do CV',
  DATE: 'Najnowsze',
  SALARY: 'Wynagrodzenie',
};

export const MIN_SCORE_OPTIONS = [40, 50, 60, 70, 80];

export const DEFAULT_FILTERS: RankingFilters = {
  statuses: DEFAULT_STATUSES,
  q: '',
  minScore: null,
  workModes: [],
  minSalary: null,
  showExcluded: false,
  sort: 'RANK',
  cvId: null,
};

/** Odmiana po liczebniku: 1 oferta, 2 oferty, 5 ofert. */
export function plural(n: number, one: string, few: string, many: string): string {
  if (n === 1) return one;
  const mod10 = n % 10;
  const mod100 = n % 100;
  return mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14) ? few : many;
}

/** Najlepsza znana kwota z widełek (górna granica, a gdy jej brak — dolna). */
export function salaryTop(r: RankedOffer): number | null {
  return r.comparableSalaryMax ?? r.comparableSalaryMin;
}

/** Czy oferta spełnia filtry (poza frazą — tę filtruje serwer i poza przełącznikiem wykluczonych). */
export function matchesFilters(r: RankedOffer, f: RankingFilters): boolean {
  if (f.statuses.length > 0 && !f.statuses.includes(r.offer.status)) return false;
  if (f.minScore != null && r.matchScore < f.minScore) return false;
  if (f.workModes.length > 0 && (r.offer.workMode == null || !f.workModes.includes(r.offer.workMode))) return false;
  if (f.minSalary != null && f.minSalary > 0) {
    const top = salaryTop(r);
    if (top == null || top < f.minSalary) return false;
  }
  return true;
}

export interface FilterResult {
  /** oferty do pokazania (bez sortowania) */
  shown: RankedOffer[];
  /** ile ofert spełniających filtry jest wykluczonych przez deal-breakery */
  excludedCount: number;
  /** ile z nich jest ukrytych (gdy przełącznik „pokaż wykluczone” jest wyłączony) */
  excludedHidden: number;
}

export function applyFilters(items: RankedOffer[], f: RankingFilters): FilterResult {
  const passing = items.filter((r) => matchesFilters(r, f));
  const excludedCount = passing.filter((r) => r.excluded).length;
  const shown = f.showExcluded ? passing : passing.filter((r) => !r.excluded);
  return { shown, excludedCount, excludedHidden: f.showExcluded ? 0 : excludedCount };
}

function offerDate(r: RankedOffer): string | null {
  return r.offer.publishedAt ?? (r.offer.createdAt ? r.offer.createdAt.slice(0, 10) : null);
}

function desc<T extends string | number>(a: T | null, b: T | null): number {
  if (a == null && b == null) return 0;
  if (a == null) return 1;
  if (b == null) return -1;
  return a < b ? 1 : a > b ? -1 : 0;
}

/** Sortowanie: wykluczone na końcu, potem wg wybranego klucza; remisy: ranking, dopasowanie, id. */
export function sortRanked(items: RankedOffer[], sort: RankingSort): RankedOffer[] {
  const primary = (a: RankedOffer, b: RankedOffer): number => {
    switch (sort) {
      case 'MATCH':
        return b.matchScore - a.matchScore;
      case 'DATE':
        return desc(offerDate(a), offerDate(b));
      case 'SALARY':
        return desc(a.comparableSalaryMid, b.comparableSalaryMid);
      default:
        return b.rankScore - a.rankScore;
    }
  };
  return [...items].sort(
    (a, b) =>
      Number(a.excluded) - Number(b.excluded) ||
      primary(a, b) ||
      b.rankScore - a.rankScore ||
      b.matchScore - a.matchScore ||
      a.offer.id - b.offer.id,
  );
}

/** Komunikat ogłaszany po filtrowaniu, np. „Pokazano 8 z 14 ofert. Ukryto 2 wykluczone.” */
export function resultMessage(shown: number, total: number, excludedHidden: number, q: string): string {
  const query = q.trim();
  const of = query
    ? `${total === 1 ? 'oferty pasującej' : 'ofert pasujących'} do „${query}”`
    : total === 1 ? 'oferty' : 'ofert';
  let msg = `Pokazano ${shown} z ${total} ${of}.`;
  if (excludedHidden > 0) {
    msg += ` Ukryto ${excludedHidden} ${plural(excludedHidden, 'wykluczoną', 'wykluczone', 'wykluczonych')}.`;
  }
  return msg;
}

/** Punkty ze znakiem i polskim przecinkiem: „+7,5”, „−3”, „0”. */
export function formatPoints(points: number): string {
  const r = Math.round(points * 10) / 10;
  const abs = Math.abs(r).toLocaleString('pl-PL', { maximumFractionDigits: 1 });
  if (r > 0) return `+${abs}`;
  if (r < 0) return `−${abs}`;
  return '0';
}

/** Odznaka zapisanego wyszukiwania: „1 nowa”, „3 nowe”, „5 nowych”. */
export function newBadge(n: number): string {
  return `${n} ${plural(n, 'nowa', 'nowe', 'nowych')}`;
}

function sameSet<T>(a: T[], b: T[]): boolean {
  return a.length === b.length && a.every((x) => b.includes(x));
}

/** Czy filtry różnią się od domyślnych (poza sortowaniem i wersją CV). */
export function hasActiveFilters(f: RankingFilters): boolean {
  return (
    !sameSet(f.statuses, DEFAULT_STATUSES) || f.q.trim() !== '' || f.minScore != null || f.workModes.length > 0 ||
    (f.minSalary != null && f.minSalary > 0) || f.showExcluded
  );
}

/** Krótki opis kryteriów, np. „„kafka” · zdalnie · ≥ 60% dopasowania”. Służy też jako domyślna nazwa. */
export function describeFilters(f: RankingFilters): string {
  const parts: string[] = [];
  if (f.q.trim()) parts.push(`„${f.q.trim()}”`);
  if (f.workModes.length > 0) parts.push(f.workModes.map((m) => WORK_MODE[m].toLowerCase()).join('/'));
  if (f.minScore != null) parts.push(`≥ ${f.minScore}% dopasowania`);
  if (f.minSalary != null && f.minSalary > 0) parts.push(`od ${formatMoney(f.minSalary)}`);
  if (f.statuses.length === 0) parts.push('wszystkie statusy');
  else if (!sameSet(f.statuses, DEFAULT_STATUSES)) {
    parts.push(ALL_STATUSES.filter((s) => f.statuses.includes(s)).map((s) => OFFER_STATUS[s].toLowerCase()).join(', '));
  }
  if (f.showExcluded) parts.push('z wykluczonymi');
  return parts.length ? parts.join(' · ') : 'Nowe i zapisane oferty';
}

/** Filtry strony → żądanie zapisu wyszukiwania. */
export function searchRequestFromFilters(name: string, f: RankingFilters): SavedSearchRequest {
  return {
    name: name.trim(),
    query: f.q.trim() || null,
    minScore: f.minScore,
    minSalary: f.minSalary != null && f.minSalary > 0 ? f.minSalary : null,
    workModes: [...f.workModes],
    statuses: [...f.statuses],
    includeExcluded: f.showExcluded,
    cvId: f.cvId,
  };
}

/** Zapisane wyszukiwanie → filtry strony (sortowanie zostaje bieżące). */
export function filtersFromSearch(s: SavedSearch, sort: RankingSort = 'RANK'): RankingFilters {
  return {
    statuses: [...s.statuses],
    q: s.query ?? '',
    minScore: s.minScore,
    workModes: [...s.workModes],
    minSalary: s.minSalary,
    showExcluded: s.includeExcluded,
    sort,
    cvId: s.cvId,
  };
}

/** Przełącza element w liście (zachowując kolejność wzorcową). */
export function toggle<T>(list: T[], value: T, order: T[]): T[] {
  const next = list.includes(value) ? list.filter((x) => x !== value) : [...list, value];
  return order.filter((x) => next.includes(x));
}

/** Podobieństwo 0–1 jako procent. */
export function similarityPercent(similarity: number): number {
  return Math.round(Math.max(0, Math.min(1, similarity)) * 100);
}
