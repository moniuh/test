/** Czysta logika modułu „rozmowy” (bez Reacta) — testowana w Vitest. */
import type { Tone } from '../../components/ui';
import type { InterviewQuestion, QuestionCategory, StarAssessment, StarPart, StarStory, Topic } from './types';

export const CATEGORY_ORDER: QuestionCategory[] = ['luka', 'techniczne', 'behawioralne', 'motywacyjne', 'o-firmę'];

export const CATEGORY_LABEL: Record<QuestionCategory, string> = {
  luka: 'Luki — o to zapytają najpewniej',
  techniczne: 'Techniczne — kluczowe umiejętności',
  behawioralne: 'Behawioralne',
  motywacyjne: 'Motywacyjne',
  'o-firmę': 'O firmę i branżę',
};

export const CATEGORY_SHORT: Record<QuestionCategory, string> = {
  luka: 'luka', techniczne: 'techniczne', behawioralne: 'behawioralne', motywacyjne: 'motywacyjne', 'o-firmę': 'o firmę',
};

export const CATEGORY_TONE: Record<QuestionCategory, Tone> = {
  luka: 'warning', techniczne: 'info', behawioralne: 'success', motywacyjne: 'neutral', 'o-firmę': 'neutral',
};

export const INTERVIEW_TABS = ['trening', 'historie', 'podsumowania', 'pytania'] as const;
export type InterviewTab = (typeof INTERVIEW_TABS)[number];

export const INTERVIEW_TAB_LABEL: Record<InterviewTab, string> = {
  trening: 'Trening odpowiedzi',
  historie: 'Bank historii STAR',
  podsumowania: 'Podsumowania rozmów',
  pytania: 'Pytania z rozmów',
};

/** Zakładka z adresu; bez niej (np. link z samym `?question=`) otwiera się trening. */
export function parseInterviewTab(tab: string | null | undefined): InterviewTab {
  return (INTERVIEW_TABS as readonly string[]).includes(tab ?? '') ? (tab as InterviewTab) : 'trening';
}

/** Grupy pytań w stałej kolejności kategorii (puste pomijamy), z zachowaniem kolejności w grupie. */
export function groupByCategory(questions: InterviewQuestion[]): { category: QuestionCategory; items: InterviewQuestion[] }[] {
  return CATEGORY_ORDER.map((category) => ({ category, items: questions.filter((q) => q.category === category) }))
    .filter((g) => g.items.length > 0);
}

/** Link do treningu odpowiedzi z pytaniem (i opcjonalnie ofertą). */
export function practiceUrl(question: string, offerId?: number | null): string {
  const params = new URLSearchParams({ tab: 'trening', question });
  if (offerId) params.set('offerId', String(offerId));
  return `/rozmowy?${params.toString()}`;
}

/** Historia STAR jako szkic odpowiedzi do treningu. */
export function composeStoryAnswer(story: Pick<StarStory, 'situation' | 'task' | 'action' | 'result'>): string {
  return [story.situation, story.task, story.action, story.result]
    .map((p) => (p ?? '').trim())
    .filter(Boolean)
    .join(' ');
}

/** „Java, spring boot,  , Kafka” → ['Java', 'spring boot', 'Kafka'] (bez duplikatów). */
export function splitSkills(text: string): string[] {
  const seen = new Set<string>();
  const out: string[] = [];
  for (const raw of text.split(/[,;\n]/)) {
    const s = raw.trim();
    if (s && !seen.has(s.toLowerCase())) {
      seen.add(s.toLowerCase());
      out.push(s);
    }
  }
  return out;
}

/** Liczba słów (jak w ocenie na serwerze — litery i cyfry). */
export function wordCount(text: string): number {
  return (text.match(/[\p{L}\p{N}]+(?:['’]\p{L}+)?/gu) ?? []).length;
}

export interface PartView {
  key: 'situation' | 'task' | 'action' | 'result';
  letter: string;
  label: string;
  status: string;
  tone: Tone;
  part: StarPart;
}

const PART_META: { key: PartView['key']; letter: string; label: string }[] = [
  { key: 'situation', letter: 'S', label: 'Sytuacja' },
  { key: 'task', letter: 'T', label: 'Zadanie' },
  { key: 'action', letter: 'A', label: 'Działanie' },
  { key: 'result', letter: 'R', label: 'Rezultat' },
];

/** Elementy STAR do wyświetlenia: status zawsze tekstem (nie tylko kolorem). */
export function starParts(a: StarAssessment): PartView[] {
  return PART_META.map((m) => {
    const part = a.star[m.key];
    const status = part.confidence >= 1 ? 'wykryto' : part.confidence > 0 ? 'słaby sygnał' : 'brak';
    const tone: Tone = part.confidence >= 1 ? 'success' : part.confidence > 0 ? 'warning' : 'danger';
    return { ...m, status, tone, part };
  });
}

export const LENGTH_LABEL: Record<StarAssessment['length'], string> = {
  TOO_SHORT: 'za krótka', OK: 'w sam raz', TOO_LONG: 'za długa',
};

export const PERSON_LABEL: Record<StarAssessment['firstPersonSingularVsPlural']['verdict'], string> = {
  SINGULAR: 'głównie „ja” — dobrze', PLURAL: 'głównie „my” — pokaż swój wkład', BALANCED: '„ja” i „my” po równo', NONE: 'brak 1. osoby',
};

/** Tematy z „do poprawy”: najpierw powtarzające się (≥ 2 rozmowy). */
export function splitTopics(topics: Topic[]): { recurring: Topic[]; single: Topic[] } {
  return { recurring: topics.filter((t) => t.count >= 2), single: topics.filter((t) => t.count < 2) };
}

/** Filtr banku pytań po tekście, firmie i umiejętnościach (bez wielkości liter i polskich znaków). */
export function matchesFilter(text: string, filter: string): boolean {
  const f = fold(filter.trim());
  return !f || fold(text).includes(f);
}

export function fold(s: string): string {
  return s.toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, '').replace(/ł/g, 'l');
}

export function ratingLabel(r: number | null | undefined): string {
  if (r == null) return '—';
  return `${r}/5`;
}
