/** Czysta logika modułu „tailoring” (etykiety, podsumowania, formatowanie) — testowana w Vitest. */
import type {
  BulletChange, DiffEntry, DiffKind, GuardReport, LibraryItem, RequirementChangeKind, ViolationKind,
} from './types';
import type { Tone } from '../../components/ui';

/** Etykiety zmian w widoku różnic — informacja tekstem, nie tylko kolorem. */
export const DIFF_LABEL: Record<DiffKind, string> = {
  SAME: 'bez zmian',
  ADDED: 'dodane',
  MOVED: 'przeniesione',
  CHANGED: 'zmienione',
  REMOVED: 'ukryte',
};

/** Znacznik na początku linii różnic (jak w narzędziach diff). */
export const DIFF_MARK: Record<DiffKind, string> = {
  SAME: '·',
  ADDED: '+',
  MOVED: '↕',
  CHANGED: '~',
  REMOVED: '−',
};

export const VIOLATION_LABEL: Record<ViolationKind, string> = {
  NEW_SKILL: 'Nowa umiejętność',
  NEW_NUMBER: 'Nowa liczba',
  NEW_EMPLOYER: 'Zmienione stanowisko lub pracodawca',
  NEW_DATE: 'Nowa lub zmieniona data',
  MISSING_ROLE: 'Brakujące stanowisko',
  UNVERIFIED_LINE: 'Niepotwierdzona treść',
};

export const CHANGE_LABEL: Record<RequirementChangeKind, string> = {
  GAINED_EVIDENCE: 'Zyskało dowód',
  IMPROVED: 'Lepiej',
  UNCHANGED: 'Bez zmian',
  STILL_MISSING: 'Nadal brakuje',
  WORSE: 'Gorzej',
};

export const CHANGE_TONE: Record<RequirementChangeKind, Tone> = {
  GAINED_EVIDENCE: 'success',
  IMPROVED: 'success',
  UNCHANGED: 'neutral',
  STILL_MISSING: 'warning',
  WORSE: 'danger',
};

/** Polska odmiana liczebników: 1 naruszenie, 2 naruszenia, 5 naruszeń. */
export function pluralPl(n: number, one: string, few: string, many: string): string {
  if (n === 1) return one;
  const mod10 = n % 10;
  const mod100 = n % 100;
  return mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14) ? few : many;
}

export function diffCounts(entries: DiffEntry[]): Record<DiffKind, number> {
  const out: Record<DiffKind, number> = { SAME: 0, ADDED: 0, MOVED: 0, CHANGED: 0, REMOVED: 0 };
  for (const e of entries) out[e.kind] += 1;
  return out;
}

/** „Dodane: 3 · przeniesione: 2 · ukryte: 1” albo „Brak zmian”. */
export function diffSummary(entries: DiffEntry[]): string {
  const counts = diffCounts(entries);
  const parts = (['ADDED', 'MOVED', 'CHANGED', 'REMOVED'] as const)
    .filter((k) => counts[k] > 0)
    .map((k) => `${DIFF_LABEL[k]}: ${counts[k]}`);
  if (parts.length === 0) return 'Brak zmian względem CV bazowego';
  const text = parts.join(' · ');
  return text.charAt(0).toUpperCase() + text.slice(1);
}

/** „+6 pkt”, „−3 pkt”, „bez zmian”. */
export function formatDelta(delta: number): string {
  if (delta === 0) return 'bez zmian';
  return `${delta > 0 ? '+' : '−'}${Math.abs(delta)} pkt`;
}

export function guardSummary(report: GuardReport): string {
  const n = report.violations.length;
  if (n === 0) return 'Strażnik prawdomówności: brak naruszeń';
  return `Strażnik prawdomówności: ${n} ${pluralPl(n, 'naruszenie', 'naruszenia', 'naruszeń')}`;
}

/** Komunikat dla czytników ekranu po wygenerowaniu podglądu lub kontroli. */
export function resultAnnouncement(before: number, after: number, report: GuardReport, prefix = 'Podgląd gotowy'): string {
  return `${prefix}: wynik ${before} → ${after} (${formatDelta(after - before)}). ${guardSummary(report)}.`;
}

/** Opis zmiany pozycji punktu doświadczenia (#70). */
export function bulletMoveLabel(b: BulletChange): string {
  if (b.hidden) return 'zwinięty (nie dotyczy wymagań)';
  if (b.newPosition == null || b.newPosition === b.originalPosition) return `bez zmian (pozycja ${b.originalPosition})`;
  const dir = b.newPosition < b.originalPosition ? 'wyżej' : 'niżej';
  return `${dir}: z pozycji ${b.originalPosition} na ${b.newPosition}`;
}

/** Najnowsza istniejąca dopasowana wersja dla oferty. */
export function latestForOffer(items: LibraryItem[], offerId: number): LibraryItem | undefined {
  return items
    .filter((i) => i.offer.id === offerId && i.cvExists)
    .sort((a, b) => (a.createdAt < b.createdAt ? 1 : a.createdAt > b.createdAt ? -1 : b.id - a.id))[0];
}

/** Pole „maks. punktów na stanowisko”: puste lub 0 = bez limitu, wartość ograniczona do 1–20. */
export function parseMaxBullets(value: string): number | null {
  const n = Number.parseInt(value, 10);
  if (!Number.isFinite(n) || n <= 0) return null;
  return Math.min(n, 20);
}

/** Czy tekst w edytorze różni się od wygenerowanego (pomija różnice końców linii i spacji na końcu). */
export function isEdited(generated: string, current: string): boolean {
  const norm = (s: string) => s.replace(/\r\n/g, '\n').replace(/[ \t]+$/gm, '').trimEnd();
  return norm(generated) !== norm(current);
}
