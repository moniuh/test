import { describe, expect, it } from 'vitest';
import {
  MAX_CV_BYTES, applySummary, auditCountsText, categoryLabel, countByStatus, formatBytes, importSummary,
  initialSelection, pluralize, potentialGain, scoreDelta, sortChecks, toggleSelection, validateCvFile, yearsLabel,
} from './cvTools';
import type { AuditCheck, CvImportResult, SkillSuggestion } from './types';

const check = (code: string, status: AuditCheck['status'], weight: number): AuditCheck => ({
  code, status, title: code, details: '', fix: null, weight, examples: [],
  points: status === 'ok' ? weight : status === 'warning' ? weight / 2 : 0,
});

describe('pluralize', () => {
  it('follows Polish rules', () => {
    expect(pluralize(1, 'błąd', 'błędy', 'błędów')).toBe('błąd');
    expect(pluralize(3, 'błąd', 'błędy', 'błędów')).toBe('błędy');
    expect(pluralize(5, 'błąd', 'błędy', 'błędów')).toBe('błędów');
    expect(pluralize(12, 'błąd', 'błędy', 'błędów')).toBe('błędów');
    expect(pluralize(22, 'błąd', 'błędy', 'błędów')).toBe('błędy');
    expect(pluralize(0, 'błąd', 'błędy', 'błędów')).toBe('błędów');
    expect(yearsLabel(1)).toBe('1 rok');
    expect(yearsLabel(4)).toBe('4 lata');
    expect(yearsLabel(5)).toBe('5 lat');
  });
});

describe('validateCvFile', () => {
  it('accepts PDF and DOCX within the limit', () => {
    expect(validateCvFile('CV.PDF', 1000)).toBeNull();
    expect(validateCvFile('cv.docx', MAX_CV_BYTES)).toBeNull();
  });
  it('rejects other types, old Word, empty and too large files', () => {
    expect(validateCvFile('cv.txt', 10)).toContain('PDF lub DOCX');
    expect(validateCvFile('cv.doc', 10)).toContain('.doc');
    expect(validateCvFile('cv.pdf', 0)).toContain('pusty');
    expect(validateCvFile('cv.pdf', MAX_CV_BYTES + 1)).toContain('limit to 5,0 MB');
  });
  it('formats sizes', () => {
    expect(formatBytes(500)).toBe('500 B');
    expect(formatBytes(2048)).toBe('2 KB');
    expect(formatBytes(1.5 * 1024 * 1024)).toBe('1,5 MB');
  });
});

describe('importSummary', () => {
  it('describes the imported version', () => {
    const r = { cv: { name: 'CV Anna' }, format: 'PDF', pages: 2, characters: 3000, words: 412, warnings: [] } as unknown as CvImportResult;
    expect(importSummary(r)).toBe('Zaimportowano CV jako nową wersję „CV Anna” (PDF, 2 strony, 412 słów).');
    expect(importSummary({ ...r, format: 'DOCX', pages: null, words: 1 })).toContain('(DOCX, 1 słowo)');
  });
});

describe('audit helpers', () => {
  const checks = [check('a', 'ok', 10), check('b', 'warning', 8), check('c', 'error', 5), check('d', 'error', 12)];
  it('sorts errors first, biggest loss first', () => {
    expect(sortChecks(checks).map((c) => c.code)).toEqual(['d', 'c', 'b', 'a']);
  });
  it('counts statuses and potential gain', () => {
    expect(countByStatus(checks)).toEqual({ ok: 1, warning: 1, error: 2 });
    expect(potentialGain(checks)).toBe(21);
    expect(auditCountsText(checks)).toBe('2 błędy · 1 do poprawy · 1 OK');
  });
});

describe('skills helpers', () => {
  const s = (name: string, alreadyInProfile: boolean, mentions = 2): SkillSuggestion => ({
    name, alreadyInProfile, category: null, years: null, evidence: null, yearsEvidence: null, mentions, profileYears: null,
  });
  it('preselects skills missing from profile mentioned at least twice, and toggles', () => {
    const sel = initialSelection([s('Java', true), s('Docker', false), s('Kafka', false), s('Excel', false, 1)]);
    expect([...sel]).toEqual(['Docker', 'Kafka']);
    const next = toggleSelection(sel, 'Docker');
    expect([...next]).toEqual(['Kafka']);
    expect([...sel]).toEqual(['Docker', 'Kafka']);
    expect([...toggleSelection(next, 'Java')]).toEqual(['Kafka', 'Java']);
  });
  it('labels categories with fallback', () => {
    expect(categoryLabel('jezyk-programowania')).toBe('Język programowania');
    expect(categoryLabel('nowa-kategoria')).toBe('Nowa kategoria');
    expect(categoryLabel(null)).toBe('Inne');
  });
  it('summarizes apply result', () => {
    expect(applySummary({ added: ['Docker', 'Kafka'], skipped: [], profile: {} as never }))
      .toBe('Dodano do profilu 2 umiejętności: Docker, Kafka.');
    expect(applySummary({ added: [], skipped: ['Java — już jest w profilu'], profile: {} as never })).toContain('Nic nie dodano');
  });
});

describe('scoreDelta', () => {
  it('shows gain or no change', () => {
    expect(scoreDelta(60, 74)).toBe('+14 pkt');
    expect(scoreDelta(60, 60)).toBe('bez zmian');
  });
});
