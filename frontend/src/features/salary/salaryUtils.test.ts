import { describe, expect, it } from 'vitest';
import {
  DEFAULT_NET_FORM, barWidth, buildNetRequest, calculatorLink, formatPln, formatRatio, negotiationLink,
  parseAmount, parseContractKind, parseTab, statsRows, toggleSelection, verdictView,
} from './salaryUtils';

describe('parseAmount', () => {
  it('accepts Polish formats', () => {
    expect(parseAmount('12 000')).toBe(12000);
    expect(parseAmount('12000,50')).toBe(12000.5);
    expect(parseAmount('18k')).toBe(18000);
    expect(parseAmount('15 tys.')).toBe(15000);
    expect(parseAmount('9 500 zł')).toBe(9500);
  });
  it('rejects garbage', () => {
    expect(parseAmount('')).toBeNull();
    expect(parseAmount('dużo')).toBeNull();
    expect(parseAmount(null)).toBeNull();
  });
});

describe('buildNetRequest', () => {
  it('sends only options relevant for employment contract', () => {
    const req = buildNetRequest({ ...DEFAULT_NET_FORM, amount: '10 000', ppk: true, zus: 'PREFERENTIAL' });
    expect(req).toEqual({ amount: 10000, contract: 'UOP', kup: 'BASIC', ppk: true, under26: false });
  });
  it('sends ZUS and ryczałt rate for B2B', () => {
    const req = buildNetRequest({ ...DEFAULT_NET_FORM, contract: 'B2B_RYCZALT', ryczaltRate: 8.5, zus: 'START_RELIEF' });
    expect(req).toMatchObject({ contract: 'B2B_RYCZALT', ryczaltRate: 8.5, zus: 'START_RELIEF', sickness: false, costsMonthly: 0 });
  });
  it('student on civil contract implies youth relief and no PPK', () => {
    const req = buildNetRequest({ ...DEFAULT_NET_FORM, contract: 'UZ', student: true, ppk: true });
    expect(req).toMatchObject({ contract: 'UZ', student: true, under26: true, ppk: false });
  });
  it('returns a readable error for invalid amount', () => {
    expect(buildNetRequest({ ...DEFAULT_NET_FORM, amount: 'abc' })).toHaveProperty('error');
    expect(buildNetRequest({ ...DEFAULT_NET_FORM, contract: 'B2B_LINIOWY', costsMonthly: 'x' })).toHaveProperty('error');
  });
});

describe('selection and links', () => {
  it('toggles up to four offers', () => {
    expect(toggleSelection([1, 2], 3)).toEqual([1, 2, 3]);
    expect(toggleSelection([1, 2, 3, 4], 5)).toEqual([1, 2, 3, 4]);
    expect(toggleSelection([1, 2], 1)).toEqual([2]);
  });
  it('builds links with parameters', () => {
    expect(calculatorLink(15999.6, 'B2B_LINIOWY')).toBe('/wynagrodzenia?tab=kalkulator&amount=16000&contract=B2B_LINIOWY');
    expect(negotiationLink(7)).toBe('/wynagrodzenia?tab=negocjacje&offerId=7');
    expect(parseTab('statystyki')).toBe('statystyki');
    expect(parseTab('cokolwiek')).toBe('kalkulator');
    expect(parseContractKind('UZ')).toBe('UZ');
    expect(parseContractKind('B2B')).toBeNull();
  });
});

describe('formatting and charts', () => {
  it('formats money and ratios', () => {
    expect(formatPln(12345).replace(/\s/g, ' ')).toBe('12 345 zł');
    expect(formatPln(13605.85, 2).replace(/\s/g, ' ')).toBe('13 605,85 zł');
    expect(formatPln(null)).toBe('—');
    expect(formatRatio(0.814)).toBe('81%');
  });
  it('computes bar widths relative to the largest median', () => {
    expect(barWidth(5, 10)).toBe(50);
    expect(barWidth(20, 10)).toBe(100);
    expect(barWidth(null, 10)).toBe(0);
    const rows = statsRows([
      { key: 'MID', label: 'mid', stats: { count: 4, median: 15000, p25: 13000, p75: 17000, min: 1, max: 2, smallSample: true } },
      { key: 'SENIOR', label: 'senior', stats: { count: 6, median: 20000, p25: null, p75: null, min: 1, max: 2, smallSample: false } },
    ]);
    expect(rows[0].width).toBe(75);
    expect(rows[1].width).toBe(100);
    expect(rows[1].range).toBe('—');
    expect(rows[0].small).toBe(true);
  });
  it('describes verdicts with text and icon, not only colour', () => {
    expect(verdictView('BELOW')).toMatchObject({ tone: 'warning', label: 'Poniżej rynku' });
    expect(verdictView('ABOVE').icon).toBeTruthy();
    expect(verdictView('NO_DATA').label).toBe('Za mało danych');
  });
});
