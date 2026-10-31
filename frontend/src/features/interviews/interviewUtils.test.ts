import { describe, expect, it } from 'vitest';
import {
  composeStoryAnswer, groupByCategory, matchesFilter, parseInterviewTab, practiceUrl, splitSkills, splitTopics,
  starParts, wordCount,
} from './interviewUtils';
import type { InterviewQuestion, StarAssessment, StarPart } from './types';

const q = (category: InterviewQuestion['category'], question: string): InterviewQuestion => ({
  question, category, skill: null, why: '', tip: '', requirement: null, evidence: null,
});

describe('groupByCategory', () => {
  it('keeps the fixed category order and skips empty groups', () => {
    const groups = groupByCategory([q('motywacyjne', 'M'), q('luka', 'L1'), q('techniczne', 'T'), q('luka', 'L2')]);
    expect(groups.map((g) => g.category)).toEqual(['luka', 'techniczne', 'motywacyjne']);
    expect(groups[0].items.map((i) => i.question)).toEqual(['L1', 'L2']);
  });
});

describe('links and tabs', () => {
  it('builds practice link with encoded question', () => {
    const url = practiceUrl('Jak działa @Transactional?', 3);
    expect(url.startsWith('/rozmowy?tab=trening&question=')).toBe(true);
    expect(new URLSearchParams(url.split('?')[1]).get('question')).toBe('Jak działa @Transactional?');
    expect(url).toContain('offerId=3');
  });
  it('defaults to the practice tab', () => {
    expect(parseInterviewTab('historie')).toBe('historie');
    expect(parseInterviewTab(null)).toBe('trening');
    expect(parseInterviewTab('nieznana')).toBe('trening');
  });
});

describe('text helpers', () => {
  it('composes a STAR story into an answer draft', () => {
    expect(composeStoryAnswer({ situation: 'Sytuacja.', task: '', action: ' Działanie. ', result: null })).toBe('Sytuacja. Działanie.');
  });
  it('splits skills and removes duplicates', () => {
    expect(splitSkills('Java, spring boot,  , Kafka; java')).toEqual(['Java', 'spring boot', 'Kafka']);
  });
  it('counts words like the server', () => {
    expect(wordCount('Skróciłam czas o 40% w 2 tygodnie.')).toBe(7);
    expect(wordCount('')).toBe(0);
  });
  it('filters without diacritics and case', () => {
    expect(matchesFilter('Jakie usługi AWS znasz?', 'uslugi')).toBe(true);
    expect(matchesFilter('Jakie usługi AWS znasz?', '')).toBe(true);
    expect(matchesFilter('Jakie usługi AWS znasz?', 'kafka')).toBe(false);
  });
  it('splits recurring topics', () => {
    const { recurring, single } = splitTopics([
      { topic: 'AWS', kind: 'SKILL', count: 2, interviewIds: [1, 2] },
      { topic: 'stres', kind: 'WORD', count: 1, interviewIds: [3] },
    ]);
    expect(recurring.map((t) => t.topic)).toEqual(['AWS']);
    expect(single.map((t) => t.topic)).toEqual(['stres']);
  });
});

describe('starParts', () => {
  const part = (confidence: number): StarPart => ({ detected: confidence > 0, confidence, evidence: null, markers: [] });
  it('describes each part with text status and tone', () => {
    const a = { star: { situation: part(1), task: part(0.6), action: part(0), result: part(1) } } as unknown as StarAssessment;
    const parts = starParts(a);
    expect(parts.map((p) => p.letter).join('')).toBe('STAR');
    expect(parts.map((p) => p.status)).toEqual(['wykryto', 'słaby sygnał', 'brak', 'wykryto']);
    expect(parts[2].tone).toBe('danger');
  });
});
