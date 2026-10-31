import { describe, expect, it } from 'vitest';
import { ApiError } from '../../api/client';
import {
  PASTE_HINT, confidenceSummary, countBySeverity, describeImportError, fieldStatus, formatPercent, isHttpUrl,
  likelyDuplicates, nextTabIndex, parseAnnouncement, plural, redFlagSummary, reviewLabels, sortRedFlags, toFormInitial,
} from './logic';
import type { DuplicateReport, OfferDraftDto, ParsedField, ParsedOffer, RedFlag } from './types';

const draft: OfferDraftDto = {
  title: 'Senior Java Developer',
  company: 'FinPay Systems',
  location: 'Warszawa',
  url: 'https://justjoin.it/offers/1',
  source: 'justjoin.it',
  description: 'Treść',
  workMode: 'HYBRID',
  contractTypes: ['B2B'],
  seniority: 'SENIOR',
  salaryMin: 22000,
  salaryMax: 28000,
  salaryCurrency: 'PLN',
  salaryPeriod: null,
  salaryGross: true,
  requirements: [{ text: 'Java', mustHave: true, minYears: 5 }],
  benefits: ['Opieka medyczna'],
  publishedAt: null,
  expiresAt: '2026-11-30',
};

const field = (f: Partial<ParsedField>): ParsedField => ({
  field: 'title', label: 'Stanowisko', value: 'x', confidence: 0.9, evidence: null, method: 'm', review: false, ...f,
});

const parsed: ParsedOffer = {
  draft,
  fields: [
    field({ field: 'title', label: 'Stanowisko' }),
    field({ field: 'company', label: 'Firma', confidence: 0.65, review: true }),
    field({ field: 'salary', label: 'Wynagrodzenie', value: null, confidence: 0, review: true }),
  ],
  recognized: ['title', 'company'],
  toReview: ['company', 'salary'],
  confidence: 0.724,
  reviewThreshold: 0.75,
  origin: 'text',
  warnings: [],
};

describe('toFormInitial', () => {
  it('kopiuje pola szkicu i uzupełnia okres przy widełkach', () => {
    const init = toFormInitial(draft);
    expect(init.title).toBe('Senior Java Developer');
    expect(init.salaryPeriod).toBe('MONTH');
    expect(init.contractTypes).toEqual(['B2B']);
    expect(init.requirements).toEqual([{ text: 'Java', mustHave: true, minYears: 5 }]);
    expect(init.requirements).not.toBe(draft.requirements);
    expect(Object.keys(init)).not.toContain('fields');
  });

  it('pusty tytuł i brak widełek', () => {
    const init = toFormInitial({ ...draft, title: null, salaryMin: null, salaryMax: null, salaryCurrency: null });
    expect(init.title).toBe('');
    expect(init.salaryPeriod).toBeNull();
    expect(init.salaryCurrency).toBe('PLN');
  });
});

describe('pola rozpoznane', () => {
  it('status pola', () => {
    expect(fieldStatus(parsed.fields[0])).toBe('ok');
    expect(fieldStatus(parsed.fields[1])).toBe('review');
    expect(fieldStatus(parsed.fields[2])).toBe('missing');
  });

  it('podsumowanie i lista do sprawdzenia', () => {
    expect(confidenceSummary(parsed)).toBe('Rozpoznano 2 z 3 pól (1 do sprawdzenia) · ogólna pewność 72%');
    expect(reviewLabels(parsed)).toEqual(['Firma', 'Wynagrodzenie']);
    expect(parseAnnouncement(parsed)).toContain('2 pola wymagają sprawdzenia');
  });

  it('procenty', () => {
    expect(formatPercent(0.876)).toBe('88%');
    expect(formatPercent(1.2)).toBe('100%');
    expect(formatPercent(null)).toBe('—');
  });
});

describe('czerwone flagi', () => {
  const flags: RedFlag[] = [
    { code: 'FAMILY_ATMOSPHERE', severity: 'info', title: 'a', why: 'w', quote: null },
    { code: 'NO_SALARY', severity: 'warning', title: 'b', why: 'w', quote: null },
    { code: 'UNPAID_TRIAL', severity: 'high', title: 'c', why: 'w', quote: 'Bezpłatny okres próbny' },
    { code: 'UOD_FOR_JOB', severity: 'high', title: 'd', why: 'w', quote: null },
  ];

  it('sortuje od najpoważniejszych, stabilnie', () => {
    expect(sortRedFlags(flags).map((f) => f.code)).toEqual(['UNPAID_TRIAL', 'UOD_FOR_JOB', 'NO_SALARY', 'FAMILY_ATMOSPHERE']);
  });

  it('liczy i podsumowuje z polską odmianą', () => {
    expect(countBySeverity(flags)).toEqual({ high: 2, warning: 1, info: 1 });
    expect(redFlagSummary(flags)).toBe('2 poważne, 1 ostrzeżenie, 1 do rozważenia');
    expect(redFlagSummary([])).toBe('Nie wykryto czerwonych flag');
    expect(plural(5, 'flaga', 'flagi', 'flag')).toBe('flag');
    expect(plural(22, 'flaga', 'flagi', 'flag')).toBe('flagi');
    expect(plural(12, 'flaga', 'flagi', 'flag')).toBe('flag');
  });
});

describe('import z URL', () => {
  it('waliduje adres http/https', () => {
    expect(isHttpUrl('https://www.pracuj.pl/praca/1')).toBe(true);
    expect(isHttpUrl(' http://example.com ')).toBe(true);
    expect(isHttpUrl('pracuj.pl/praca/1')).toBe(false);
    expect(isHttpUrl('ftp://example.com')).toBe(false);
    expect(isHttpUrl('https://')).toBe(false);
  });

  it('błąd 422 (portal blokuje) → podpowiedź wklejenia treści', () => {
    const e = new ApiError('Portal zablokował automatyczne pobieranie ogłoszenia (HTTP 403)', 422, ['blocked', PASTE_HINT]);
    const v = describeImportError(e);
    expect(v.message).toContain('HTTP 403');
    expect(v.hint).toBe(PASTE_HINT);
    expect(v.suggestPaste).toBe(true);
  });

  it('błąd walidacji i brak połączenia', () => {
    expect(describeImportError(new ApiError('Obsługujemy tylko adresy http:// i https://', 400)).suggestPaste).toBe(true);
    const offline = describeImportError(new ApiError('Brak połączenia z serwerem — czy backend działa?', 0));
    expect(offline.suggestPaste).toBe(false);
    expect(offline.hint).toBeNull();
    expect(describeImportError(new Error('x')).hint).toBe(PASTE_HINT);
  });
});

describe('duplikaty i zakładki', () => {
  it('wybiera kandydatów powyżej progu', () => {
    const report: DuplicateReport = {
      offerId: null, threshold: 0.75, minScore: 0.5, weights: { company: 0.35, title: 0.3, content: 0.35 },
      candidates: [
        { offerId: 2, title: 'Java Developer', company: 'LogiTech', source: null, status: 'NEW', score: 0.92, companyScore: 1,
          titleScore: 1, contentScore: 0.78, sameUrl: false, duplicate: true, alreadyMarked: false, older: true, reasons: [] },
        { offerId: 9, title: 'Java Dev', company: 'Inna', source: null, status: 'NEW', score: 0.55, companyScore: 0.1,
          titleScore: 1, contentScore: 0.4, sameUrl: false, duplicate: false, alreadyMarked: false, older: true, reasons: [] },
      ],
    };
    expect(likelyDuplicates(report).map((c) => c.offerId)).toEqual([2]);
    expect(likelyDuplicates(undefined)).toEqual([]);
  });

  it('nawigacja klawiaturą po zakładkach', () => {
    expect(nextTabIndex(0, 'ArrowRight', 3)).toBe(1);
    expect(nextTabIndex(2, 'ArrowRight', 3)).toBe(0);
    expect(nextTabIndex(0, 'ArrowLeft', 3)).toBe(2);
    expect(nextTabIndex(1, 'Home', 3)).toBe(0);
    expect(nextTabIndex(0, 'End', 3)).toBe(2);
    expect(nextTabIndex(0, 'Enter', 3)).toBeNull();
  });
});
