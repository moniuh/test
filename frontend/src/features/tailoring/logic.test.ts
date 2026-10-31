import { describe, expect, it } from 'vitest';
import {
  bulletMoveLabel, diffCounts, diffSummary, formatDelta, guardSummary, isEdited, latestForOffer, parseMaxBullets,
  pluralPl, resultAnnouncement,
} from './logic';
import type { DiffEntry, GuardReport, LibraryItem } from './types';

const entry = (kind: DiffEntry['kind']): DiffEntry => ({ kind, text: 'x', previous: null, baseLine: 1, newLine: 1 });

const report = (violations: number): GuardReport => ({
  passed: violations === 0,
  violations: Array.from({ length: violations }, (_, i) => ({ line: i + 1, kind: 'NEW_SKILL', detail: 'd', text: 't' })),
  checkedLines: 10,
  verbatimLines: 8,
  rewrittenLines: 1,
  generatedLines: 1,
  profileOnlySkills: [],
});

const item = (id: number, offerId: number, createdAt: string, cvExists = true): LibraryItem => ({
  id,
  cvVariantId: 100 + id,
  name: `v${id}`,
  cvExists,
  active: false,
  baseCvId: 1,
  baseCvName: 'Backend',
  offer: { id: offerId, title: 'T', company: 'C', status: 'NEW', exists: true },
  scoreBefore: 70,
  scoreAfter: 80,
  source: 'offline',
  edited: false,
  acceptedViolations: false,
  violations: 0,
  createdAt,
  applications: [],
});

describe('tailoring logic', () => {
  it('odmienia liczebniki po polsku', () => {
    expect(pluralPl(1, 'naruszenie', 'naruszenia', 'naruszeń')).toBe('naruszenie');
    expect(pluralPl(3, 'naruszenie', 'naruszenia', 'naruszeń')).toBe('naruszenia');
    expect(pluralPl(5, 'naruszenie', 'naruszenia', 'naruszeń')).toBe('naruszeń');
    expect(pluralPl(12, 'naruszenie', 'naruszenia', 'naruszeń')).toBe('naruszeń');
    expect(pluralPl(22, 'naruszenie', 'naruszenia', 'naruszeń')).toBe('naruszenia');
  });

  it('podsumowuje różnice tekstem', () => {
    const entries = [entry('SAME'), entry('ADDED'), entry('ADDED'), entry('MOVED'), entry('REMOVED')];
    expect(diffCounts(entries)).toEqual({ SAME: 1, ADDED: 2, MOVED: 1, CHANGED: 0, REMOVED: 1 });
    expect(diffSummary(entries)).toBe('Dodane: 2 · przeniesione: 1 · ukryte: 1');
    expect(diffSummary([entry('SAME')])).toBe('Brak zmian względem CV bazowego');
  });

  it('formatuje różnicę wyniku i ogłoszenie', () => {
    expect(formatDelta(6)).toBe('+6 pkt');
    expect(formatDelta(-3)).toBe('−3 pkt');
    expect(formatDelta(0)).toBe('bez zmian');
    expect(guardSummary(report(0))).toBe('Strażnik prawdomówności: brak naruszeń');
    expect(guardSummary(report(2))).toBe('Strażnik prawdomówności: 2 naruszenia');
    expect(resultAnnouncement(82, 88, report(0))).toBe(
      'Podgląd gotowy: wynik 82 → 88 (+6 pkt). Strażnik prawdomówności: brak naruszeń.',
    );
  });

  it('opisuje przesunięcie punktu doświadczenia', () => {
    const base = { text: 't', relevance: 3, matchedSkills: [] };
    expect(bulletMoveLabel({ ...base, originalPosition: 4, newPosition: 1, hidden: false })).toBe('wyżej: z pozycji 4 na 1');
    expect(bulletMoveLabel({ ...base, originalPosition: 1, newPosition: 3, hidden: false })).toBe('niżej: z pozycji 1 na 3');
    expect(bulletMoveLabel({ ...base, originalPosition: 2, newPosition: 2, hidden: false })).toBe('bez zmian (pozycja 2)');
    expect(bulletMoveLabel({ ...base, originalPosition: 5, newPosition: null, hidden: true })).toContain('zwinięty');
  });

  it('wybiera najnowszą istniejącą wersję dla oferty', () => {
    const items = [item(1, 7, '2026-10-01T10:00:00Z'), item(2, 7, '2026-10-05T10:00:00Z'),
      item(3, 7, '2026-10-09T10:00:00Z', false), item(4, 8, '2026-10-10T10:00:00Z')];
    expect(latestForOffer(items, 7)?.id).toBe(2);
    expect(latestForOffer(items, 9)).toBeUndefined();
  });

  it('parsuje limit punktów i wykrywa edycję', () => {
    expect(parseMaxBullets('')).toBeNull();
    expect(parseMaxBullets('0')).toBeNull();
    expect(parseMaxBullets('3')).toBe(3);
    expect(parseMaxBullets('99')).toBe(20);
    expect(isEdited('a\nb\n', 'a\r\nb  ')).toBe(false);
    expect(isEdited('a\nb', 'a\nc')).toBe(true);
  });
});
