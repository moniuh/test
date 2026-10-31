import { describe, expect, it } from 'vitest';
import {
  addDaysIso, barWidth, buildQuery, count, defaultReportRange, energyLabel, firstName, formatDays, formatPercent,
  formatReportDateTime, goalText, isValidRange, levelTone, moodLabel, plural, progressWidth, scaleTone, streakText, trendText,
} from './logic';

describe('odmiana i formatowanie', () => {
  it('odmienia liczebniki po polsku', () => {
    expect(plural(1, 'tydzień', 'tygodnie', 'tygodni')).toBe('tydzień');
    expect(plural(3, 'tydzień', 'tygodnie', 'tygodni')).toBe('tygodnie');
    expect(plural(12, 'tydzień', 'tygodnie', 'tygodni')).toBe('tygodni');
    expect(plural(22, 'tydzień', 'tygodnie', 'tygodni')).toBe('tygodnie');
    expect(count(5, 'oferta', 'oferty', 'ofert')).toBe('5 ofert');
  });

  it('formatuje procenty i dni', () => {
    expect(formatPercent(66.7)).toBe('66,7%');
    expect(formatPercent(100)).toBe('100%');
    expect(formatPercent(null)).toBe('—');
    expect(formatDays(1)).toBe('1 dzień');
    expect(formatDays(4)).toBe('4 dni');
    expect(formatDays(3.5)).toBe('3,5 dnia');
    expect(formatDays(null)).toBe('—');
  });

  it('liczy szerokość słupków z ograniczeniem do 100%', () => {
    expect(barWidth(5, 10)).toBe('50%');
    expect(barWidth(1, 3)).toBe('33.3%');
    expect(barWidth(20, 10)).toBe('100%');
    expect(barWidth(3, 0)).toBe('0%');
    expect(barWidth(null, 10)).toBe('0%');
    expect(progressWidth(130)).toBe('100%');
    expect(progressWidth(-5)).toBe('0%');
  });
});

describe('daty i zapytania', () => {
  it('przesuwa daty ISO bez wpływu strefy czasowej', () => {
    expect(addDaysIso('2026-10-31', -29)).toBe('2026-10-02');
    expect(addDaysIso('2026-12-31', 1)).toBe('2027-01-01');
    expect(addDaysIso('2024-03-01', -1)).toBe('2024-02-29');
    expect(defaultReportRange('2026-10-31')).toEqual({ from: '2026-10-02', to: '2026-10-31' });
  });

  it('sprawdza zakres i buduje zapytanie bez pustych parametrów', () => {
    expect(isValidRange('2026-10-01', '2026-10-31')).toBe(true);
    expect(isValidRange('2026-11-01', '2026-10-31')).toBe(false);
    expect(isValidRange('', '2026-10-31')).toBe(true);
    expect(buildQuery({ from: '2026-10-01', to: '', cvId: null, days: 30 })).toBe('?from=2026-10-01&days=30');
    expect(buildQuery({})).toBe('');
  });
});

describe('motywacja', () => {
  it('opisuje postęp celu tygodniowego i serię', () => {
    expect(goalText({ applications: 1, goal: 8, percent: 13, remaining: 7, achieved: false }))
      .toBe('1 z 8 aplikacji (13%) — brakuje 7 aplikacji.');
    expect(goalText({ applications: 9, goal: 8, percent: 113, remaining: 0, achieved: true }))
      .toBe('Cel osiągnięty: 9 z 8 aplikacji (113%).');
    expect(streakText(0)).toContain('nowa szansa');
    expect(streakText(2)).toBe('Seria: 2 tygodnie z rzędu z osiągniętym celem.');
  });

  it('ma słowne etykiety nastroju i energii', () => {
    expect(moodLabel(1)).toBe('Bardzo źle');
    expect(moodLabel(5)).toBe('Bardzo dobrze');
    expect(energyLabel(3)).toBe('Średnia');
    expect(moodLabel(9)).toBe('—');
    expect(scaleTone(2)).toBe('danger');
    expect(scaleTone(4)).toBe('success');
  });

  it('opisuje trendy i priorytety słowami', () => {
    expect(trendText('UP', 12.2)).toBe('↑ wzrost o 12,2 pp');
    expect(trendText('DOWN', -21.2)).toBe('↓ spadek o 21,2 pp');
    expect(trendText('STABLE', 1)).toBe('→ bez większych zmian');
    expect(trendText('UNKNOWN', null)).toBe('za mało danych do porównania');
    expect(levelTone('HIGH')).toBe('danger');
    expect(levelTone('LOW')).toBe('info');
    expect(firstName('Anna Nowak')).toBe('Anna');
    expect(firstName(null)).toBe('');
  });

  it('formatuje datę i godzinę do raportu z rokiem', () => {
    expect(formatReportDateTime('2026-10-21T10:00:00')).toBe('21.10.2026, 10:00');
    expect(formatReportDateTime('2026-10-31T12:39:01.903')).toBe('31.10.2026, 12:39');
    expect(formatReportDateTime(null)).toBe('—');
  });
});
