import { describe, expect, it } from 'vitest';
import { formatSalary } from './labels';

describe('formatSalary', () => {
  it('formats B2B hourly range', () => {
    const s = formatSalary({ salaryMin: 140, salaryMax: 170, salaryCurrency: 'PLN', salaryPeriod: 'HOUR', salaryGross: true, contractTypes: ['B2B'] });
    expect(s).toContain('140');
    expect(s).toContain('/ h');
    expect(s).toContain('netto + VAT');
  });
  it('handles missing salary', () => {
    expect(formatSalary({ salaryMin: null, salaryMax: null, salaryCurrency: 'PLN', salaryPeriod: null, salaryGross: null, contractTypes: [] })).toBe('Brak widełek');
  });
});
