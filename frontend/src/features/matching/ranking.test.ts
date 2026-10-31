import { describe, expect, it } from 'vitest';
import {
  DEFAULT_FILTERS, applyFilters, describeFilters, filtersFromSearch, formatPoints, hasActiveFilters, newBadge, plural,
  resultMessage, searchRequestFromFilters, similarityPercent, sortRanked, toggle,
} from './ranking';
import type { RankedOffer, RankingFilters, SavedSearch } from './types';

function ranked(id: number, over: Partial<RankedOffer> = {}, offer: Partial<RankedOffer['offer']> = {}): RankedOffer {
  return {
    offer: {
      id, title: `Oferta ${id}`, company: null, location: null, source: null, workMode: 'REMOTE', contractTypes: [],
      seniority: null, salaryMin: null, salaryMax: null, salaryCurrency: 'PLN', salaryPeriod: null, salaryGross: null,
      status: 'NEW', rejectReason: null, duplicateOfId: null, tags: [], publishedAt: '2026-10-20', expiresAt: null,
      createdAt: '2026-10-20T08:00:00Z', ...offer,
    },
    matchScore: 50, matchSummary: null, missingSkills: [], rankScore: 50, comparableSalaryMin: null,
    comparableSalaryMax: null, comparableSalaryMid: null, components: [], excluded: false, exclusions: [], ...over,
  };
}

const items: RankedOffer[] = [
  ranked(1, { rankScore: 80, matchScore: 70, comparableSalaryMin: 16000, comparableSalaryMax: 20000, comparableSalaryMid: 18000 }),
  ranked(2, { rankScore: 90, matchScore: 60 }, { workMode: 'HYBRID', status: 'SAVED', publishedAt: '2026-10-29' }),
  ranked(3, { rankScore: 95, matchScore: 95, excluded: true }, { workMode: 'ONSITE' }),
  ranked(4, { rankScore: 40, matchScore: 30, comparableSalaryMin: 25000, comparableSalaryMax: null, comparableSalaryMid: 25000 }, { status: 'ARCHIVED' }),
];

const ids = (list: RankedOffer[]) => list.map((r) => r.offer.id);

describe('applyFilters', () => {
  it('hides excluded by default and counts them', () => {
    const r = applyFilters(items, { ...DEFAULT_FILTERS });
    expect(ids(r.shown)).toEqual([1, 2]);
    expect(r.excludedCount).toBe(1);
    expect(r.excludedHidden).toBe(1);
  });

  it('shows excluded when toggled', () => {
    const r = applyFilters(items, { ...DEFAULT_FILTERS, showExcluded: true });
    expect(ids(r.shown)).toEqual([1, 2, 3]);
    expect(r.excludedHidden).toBe(0);
  });

  it('empty status list means all statuses', () => {
    expect(ids(applyFilters(items, { ...DEFAULT_FILTERS, statuses: [] }).shown)).toEqual([1, 2, 4]);
  });

  it('filters by min score, work mode and salary top', () => {
    const base: RankingFilters = { ...DEFAULT_FILTERS, statuses: [] };
    expect(ids(applyFilters(items, { ...base, minScore: 60 }).shown)).toEqual([1, 2]);
    expect(ids(applyFilters(items, { ...base, workModes: ['HYBRID'] }).shown)).toEqual([2]);
    // oferta 1: górna granica 20 000; oferta 4: tylko „od 25 000”; oferta 2 bez widełek odpada
    expect(ids(applyFilters(items, { ...base, minSalary: 20000 }).shown)).toEqual([1, 4]);
    expect(ids(applyFilters(items, { ...base, minSalary: 21000 }).shown)).toEqual([4]);
  });
});

describe('sortRanked', () => {
  const all = applyFilters(items, { ...DEFAULT_FILTERS, statuses: [], showExcluded: true }).shown;

  it('ranks by rankScore with excluded last', () => {
    expect(ids(sortRanked(all, 'RANK'))).toEqual([2, 1, 4, 3]);
  });
  it('sorts by match, date and salary', () => {
    expect(ids(sortRanked(all, 'MATCH'))).toEqual([1, 2, 4, 3]);
    expect(ids(sortRanked(all, 'DATE'))).toEqual([2, 1, 4, 3]);
    expect(ids(sortRanked(all, 'SALARY'))).toEqual([4, 1, 2, 3]);
  });
  it('breaks ties by id and does not mutate input', () => {
    const tie = [ranked(9), ranked(7), ranked(8)];
    expect(ids(sortRanked(tie, 'RANK'))).toEqual([7, 8, 9]);
    expect(ids(tie)).toEqual([9, 7, 8]);
  });
});

describe('messages', () => {
  it('announces filtering result in Polish', () => {
    expect(resultMessage(8, 14, 0, '')).toBe('Pokazano 8 z 14 ofert.');
    expect(resultMessage(1, 1, 0, '')).toBe('Pokazano 1 z 1 oferty.');
    expect(resultMessage(3, 5, 2, ' kafka ')).toBe('Pokazano 3 z 5 ofert pasujących do „kafka”. Ukryto 2 wykluczone.');
    expect(resultMessage(0, 7, 5, '')).toContain('Ukryto 5 wykluczonych.');
    expect(resultMessage(0, 7, 1, '')).toContain('Ukryto 1 wykluczoną.');
  });

  it('pluralizes', () => {
    expect([1, 2, 5, 12, 22, 25].map((n) => plural(n, 'oferta', 'oferty', 'ofert'))).toEqual([
      'oferta', 'oferty', 'ofert', 'ofert', 'oferty', 'ofert',
    ]);
    expect(newBadge(1)).toBe('1 nowa');
    expect(newBadge(3)).toBe('3 nowe');
    expect(newBadge(5)).toBe('5 nowych');
  });

  it('formats signed points', () => {
    expect(formatPoints(40)).toBe('+40');
    expect(formatPoints(7.5)).toBe('+7,5');
    expect(formatPoints(-3.04)).toBe('−3');
    expect(formatPoints(0.01)).toBe('0');
  });

  it('similarity as clamped percent', () => {
    expect(similarityPercent(0.724)).toBe(72);
    expect(similarityPercent(1.2)).toBe(100);
  });
});

describe('saved searches', () => {
  const filters: RankingFilters = {
    ...DEFAULT_FILTERS, q: ' kafka ', minScore: 60, workModes: ['REMOTE'], minSalary: 15000, cvId: 2, sort: 'DATE',
  };

  it('describes filters', () => {
    const d = describeFilters(filters);
    expect(d).toContain('„kafka”');
    expect(d).toContain('zdalnie');
    expect(d).toContain('≥ 60% dopasowania');
    expect(d).toContain('15');
    expect(describeFilters(DEFAULT_FILTERS)).toBe('Nowe i zapisane oferty');
    expect(describeFilters({ ...DEFAULT_FILTERS, statuses: [] })).toBe('wszystkie statusy');
  });

  it('round-trips filters through a saved search', () => {
    const req = searchRequestFromFilters('  Kafka zdalnie ', filters);
    expect(req).toEqual({
      name: 'Kafka zdalnie', query: 'kafka', minScore: 60, minSalary: 15000, workModes: ['REMOTE'],
      statuses: ['NEW', 'SAVED'], includeExcluded: false, cvId: 2,
    });
    const saved: SavedSearch = {
      ...req, id: 1, lastCheckedAt: null, createdAt: null, totalCount: 3, newCount: 1,
    };
    expect(filtersFromSearch(saved, 'DATE')).toEqual({ ...filters, q: 'kafka' });
  });

  it('detects active filters and toggles in canonical order', () => {
    expect(hasActiveFilters(DEFAULT_FILTERS)).toBe(false);
    expect(hasActiveFilters({ ...DEFAULT_FILTERS, statuses: ['SAVED', 'NEW'] })).toBe(false);
    expect(hasActiveFilters({ ...DEFAULT_FILTERS, showExcluded: true })).toBe(true);
    expect(toggle(['HYBRID'], 'REMOTE', ['REMOTE', 'HYBRID', 'ONSITE'])).toEqual(['REMOTE', 'HYBRID']);
    expect(toggle(['REMOTE', 'HYBRID'], 'REMOTE', ['REMOTE', 'HYBRID', 'ONSITE'])).toEqual(['HYBRID']);
  });
});
