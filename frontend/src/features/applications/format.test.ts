import { describe, expect, it } from 'vitest';
import {
  agoLabel, daysText, daysUntil, diffSummary, emailFrom, followUpLabel, fromLocalInput, isUrl, mailtoHref, pluralPl,
  toLocalInput,
} from './format';

describe('polish plurals', () => {
  it('declines nouns after numbers', () => {
    expect(pluralPl(1, 'linia', 'linie', 'linii')).toBe('linia');
    expect(pluralPl(3, 'linia', 'linie', 'linii')).toBe('linie');
    expect(pluralPl(5, 'linia', 'linie', 'linii')).toBe('linii');
    expect(pluralPl(12, 'linia', 'linie', 'linii')).toBe('linii');
    expect(pluralPl(22, 'linia', 'linie', 'linii')).toBe('linie');
    expect(daysText(1)).toBe('1 dzień');
    expect(daysText(-4)).toBe('4 dni');
  });
});

describe('follow-up labels', () => {
  it('describes due dates relative to today', () => {
    expect(followUpLabel(-1)).toBe('1 dzień po terminie');
    expect(followUpLabel(-5)).toBe('5 dni po terminie');
    expect(followUpLabel(0)).toBe('dziś');
    expect(followUpLabel(1)).toBe('jutro');
    expect(followUpLabel(7)).toBe('za 7 dni');
  });

  it('counts days between ISO dates across months and DST', () => {
    expect(daysUntil('2026-11-03', '2026-10-31')).toBe(3);
    expect(daysUntil('2026-10-24', '2026-10-31')).toBe(-7);
    expect(daysUntil('2026-03-30', '2026-03-28')).toBe(2);
    expect(agoLabel(0)).toBe('dziś');
    expect(agoLabel(1)).toBe('wczoraj');
    expect(agoLabel(12)).toBe('12 dni temu');
    expect(agoLabel(null)).toBeNull();
  });
});

describe('misc', () => {
  it('summarizes CV changes', () => {
    expect(diffSummary(0, 0)).toBe('bez zmian');
    expect(diffSummary(1, 0)).toBe('dodano 1 linię');
    expect(diffSummary(2, 5)).toBe('dodano 2 linie, usunięto 5 linii');
  });

  it('converts datetime-local values', () => {
    expect(toLocalInput('2026-11-03T14:00:00')).toBe('2026-11-03T14:00');
    expect(toLocalInput(null)).toBe('');
    expect(fromLocalInput('2026-11-03T14:00')).toBe('2026-11-03T14:00:00');
    expect(fromLocalInput('2026-11-03T14:00:30')).toBe('2026-11-03T14:00:30');
  });

  it('builds mailto links and detects URLs', () => {
    expect(mailtoHref('hr@x.pl', 'Temat ą', 'A&B')).toBe('mailto:hr@x.pl?subject=Temat%20%C4%85&body=A%26B');
    expect(mailtoHref(null, 'T', 'B')).toBe('mailto:?subject=T&body=B');
    expect(emailFrom('Jan Rekruter <hr@x.pl>')).toBe('hr@x.pl');
    expect(emailFrom('hr@x.pl')).toBe('hr@x.pl');
    expect(emailFrom('Jan Rekruter')).toBeNull();
    expect(isUrl('https://meet.example.com/abc')).toBe(true);
    expect(isUrl('Biuro, ul. Prosta 1')).toBe(false);
  });
});
