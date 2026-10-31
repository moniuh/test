import { describe, expect, it } from 'vitest';
import {
  comparableNote, describeQuery, formatRunResult, formatRunsSummary, highlightSegments, maxSeverity, parseList, plural, relativeDay,
  runTone, validateSource,
} from './logic';
import { queryString } from './api';
import type { AggregatorRun } from './types';

const run = (over: Partial<AggregatorRun> = {}): AggregatorRun => ({
  id: 1, sourceId: 1, sourceName: 'Demo', trigger: 'MANUAL', startedAt: '2026-10-31T10:00:00Z',
  finishedAt: '2026-10-31T10:00:02Z', fetched: 8, created: 5, duplicates: 2, filteredOut: 1, errors: 0,
  status: 'OK', message: null, ...over,
});

describe('plural', () => {
  it('odmienia polskie liczebniki', () => {
    expect(plural(1, 'oferta', 'oferty', 'ofert')).toBe('oferta');
    expect(plural(2, 'oferta', 'oferty', 'ofert')).toBe('oferty');
    expect(plural(5, 'oferta', 'oferty', 'ofert')).toBe('ofert');
    expect(plural(12, 'oferta', 'oferty', 'ofert')).toBe('ofert');
    expect(plural(22, 'oferta', 'oferty', 'ofert')).toBe('oferty');
    expect(plural(0, 'oferta', 'oferty', 'ofert')).toBe('ofert');
  });
});

describe('formatRunResult', () => {
  it('opisuje udany przebieg', () => {
    expect(formatRunResult(run())).toBe(
      '„Demo”: pobrano 8 pozycji: 5 nowych ofert, 2 duplikaty, 1 odfiltrowana.',
    );
    expect(formatRunResult(run({ created: 1, duplicates: 0, filteredOut: 0, errors: 3, fetched: 4 }))).toBe(
      '„Demo”: pobrano 4 pozycje: 1 nowa oferta, 0 duplikatów, 0 odfiltrowanych, 3 błędy.',
    );
  });

  it('opisuje błąd i blokadę robots.txt', () => {
    expect(formatRunResult(run({ status: 'ERROR', message: 'HTTP 500' }))).toBe('„Demo”: błąd — HTTP 500');
    expect(formatRunResult(run({ status: 'BLOCKED', message: 'robots.txt' }))).toContain('zablokowane przez robots.txt');
  });

  it('podsumowuje wiele źródeł', () => {
    expect(formatRunsSummary([])).toBe('Brak włączonych źródeł do pobrania.');
    expect(formatRunsSummary([run(), run({ status: 'ERROR', created: 0 })])).toBe(
      'Pobrano 2 źródła: 5 nowych ofert, 1 źródło z błędem.',
    );
  });
});

describe('parseList', () => {
  it('dzieli po przecinkach i liniach, usuwa puste i powtórzenia', () => {
    expect(parseList('java, Kotlin;\n spring boot ,, JAVA')).toEqual(['java', 'Kotlin', 'spring boot']);
    expect(parseList('  ')).toEqual([]);
  });
});

describe('validateSource', () => {
  it('wymaga nazwy i poprawnego adresu', () => {
    expect(validateSource({ name: '', type: 'RSS', url: 'ftp://x', minSalary: null })).toEqual({
      name: 'Podaj nazwę źródła.',
      url: 'Adres musi zaczynać się od http:// lub https://.',
    });
    expect(validateSource({ name: 'A', type: 'RSS', url: 'https://jobs.example.com/rss', minSalary: 15000 })).toEqual({});
    expect(validateSource({ name: 'A', type: 'DEMO', url: '../x.xml', minSalary: null }).url).toBeDefined();
    expect(validateSource({ name: 'A', type: 'DEMO', url: 'demo-feed.xml', minSalary: -5 }).minSalary).toBeDefined();
  });
});

describe('relativeDay', () => {
  it('liczy dni względem dzisiaj', () => {
    expect(relativeDay('2026-10-31T08:00:00', '2026-10-31')).toBe('dziś');
    expect(relativeDay('2026-10-30T08:00:00', '2026-10-31')).toBe('wczoraj');
    expect(relativeDay('2026-10-28T08:00:00', '2026-10-31')).toBe('3 dni temu');
    expect(relativeDay(null, '2026-10-31')).toBe('—');
  });
});

describe('highlightSegments', () => {
  it('scala nakładające się zakresy i przycina do tekstu', () => {
    expect(highlightSegments('Java i Spring Boot', [[0, 4], [7, 13], [9, 18], [30, 40]])).toEqual([
      { text: 'Java', mark: true },
      { text: ' i ', mark: false },
      { text: 'Spring Boot', mark: true },
    ]);
    expect(highlightSegments('tekst', [])).toEqual([{ text: 'tekst', mark: false }]);
  });
});

describe('pozostałe', () => {
  it('mapuje statusy i sygnały', () => {
    expect(runTone('OK')).toBe('success');
    expect(runTone('ERROR')).toBe('danger');
    expect(runTone(null)).toBe('neutral');
    expect(maxSeverity([])).toBeNull();
    expect(maxSeverity([
      { kind: 'INVISIBLE_CHARACTERS', severity: 'LOW', quote: 'a', detail: '' },
      { kind: 'HIDDEN_TEXT', severity: 'MEDIUM', quote: 'b', detail: '' },
    ])).toBe('MEDIUM');
  });

  it('pokazuje równoważnik UoP tylko gdy różni się od kwot z ogłoszenia', () => {
    expect(comparableNote({ salaryMin: 15000, salaryMax: 20000, comparableMin: 15000, comparableMax: 20000 })).toBeNull();
    expect(comparableNote({ salaryMin: null, salaryMax: null, comparableMin: null, comparableMax: null })).toBeNull();
    expect(comparableNote({ salaryMin: 150, salaryMax: 185, comparableMin: 20160, comparableMax: 24860 }))
      .toMatch(/^≈ 20.160–24.860 zł\/mies\. brutto UoP$/);
    expect(comparableNote({ salaryMin: null, salaryMax: 9000, comparableMin: null, comparableMax: 12600 }))
      .toMatch(/^≈ 12.600 zł/);
  });

  it('buduje zapytania i opisy', () => {
    expect(queryString({ q: 'java', status: null, onlyUnseen: false, minScore: 50 })).toBe('?q=java&minScore=50');
    expect(queryString({})).toBe('');
    expect(describeQuery(['java'], ['spring boot'], ['react'])).toBe('frazy: „spring boot”; słowa (rdzenie): java; bez: react');
  });
});
