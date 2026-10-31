/** Czysta logika modułu „Profil i CV” (bez Reacta i DOM) — testowana w cvTools.test.ts. */
import type { Tone } from '../../components/ui';
import type {
  ApplySkillsResult, AuditCheck, CheckStatus, CvImportResult, MissingAction, Severity, SkillSuggestion,
} from './types';

/** Limit zgodny z backendem (CvTextExtractor.MAX_BYTES). */
export const MAX_CV_BYTES = 5 * 1024 * 1024;
export const ACCEPTED_EXTENSIONS = ['.pdf', '.docx'] as const;
export const ACCEPT_ATTRIBUTE =
  '.pdf,.docx,application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document';

/** Polska odmiana po liczebniku: 1 błąd, 2 błędy, 5 błędów. */
export function pluralize(n: number, one: string, few: string, many: string): string {
  if (n === 1) return one;
  const mod10 = Math.abs(n) % 10;
  const mod100 = Math.abs(n) % 100;
  return mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14) ? few : many;
}

export function yearsLabel(n: number): string {
  return `${n} ${pluralize(n, 'rok', 'lata', 'lat')}`;
}

export function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} KB`;
  return `${(bytes / 1024 / 1024).toFixed(1).replace('.', ',')} MB`;
}

/** Walidacja pliku przed wysyłką (backend sprawdza dodatkowo sygnaturę i treść). Zwraca komunikat błędu albo null. */
export function validateCvFile(name: string, size: number): string | null {
  const lower = name.trim().toLowerCase();
  if (lower.endsWith('.doc')) {
    return 'Format .doc (Word 97–2003) nie jest obsługiwany — zapisz CV jako DOCX lub PDF.';
  }
  if (!ACCEPTED_EXTENSIONS.some((ext) => lower.endsWith(ext))) {
    return 'Wybierz plik PDF lub DOCX.';
  }
  if (size === 0) return 'Plik jest pusty.';
  if (size > MAX_CV_BYTES) {
    return `Plik ma ${formatBytes(size)} — limit to ${formatBytes(MAX_CV_BYTES)}.`;
  }
  return null;
}

export function importSummary(r: CvImportResult): string {
  const parts: string[] = [r.format];
  if (r.pages != null) parts.push(`${r.pages} ${pluralize(r.pages, 'strona', 'strony', 'stron')}`);
  parts.push(`${r.words} ${pluralize(r.words, 'słowo', 'słowa', 'słów')}`);
  return `Zaimportowano CV jako nową wersję „${r.cv.name}” (${parts.join(', ')}).`;
}

// ------------------------------------------------------------------ audyt ATS

export const CHECK_STATUS: Record<CheckStatus, { label: string; tone: Tone; icon: string }> = {
  error: { label: 'Błąd', tone: 'danger', icon: '✕' },
  warning: { label: 'Do poprawy', tone: 'warning', icon: '!' },
  ok: { label: 'OK', tone: 'success', icon: '✓' },
};

const STATUS_ORDER: Record<CheckStatus, number> = { error: 0, warning: 1, ok: 2 };

/** Najpierw błędy, potem uwagi, na końcu spełnione; w grupie — malejąco według możliwych do zdobycia punktów. */
export function sortChecks(checks: AuditCheck[]): AuditCheck[] {
  return checks
    .map((c, i) => ({ c, i }))
    .sort((a, b) =>
      STATUS_ORDER[a.c.status] - STATUS_ORDER[b.c.status]
      || (b.c.weight - b.c.points) - (a.c.weight - a.c.points)
      || a.i - b.i)
    .map((x) => x.c);
}

export function countByStatus(checks: AuditCheck[]): Record<CheckStatus, number> {
  const out: Record<CheckStatus, number> = { ok: 0, warning: 0, error: 0 };
  for (const c of checks) out[c.status]++;
  return out;
}

/** Ile punktów można jeszcze zdobyć (suma strat). */
export function potentialGain(checks: AuditCheck[]): number {
  return Math.round(checks.reduce((sum, c) => sum + (c.weight - c.points), 0));
}

export function auditCountsText(checks: AuditCheck[]): string {
  const c = countByStatus(checks);
  return [
    `${c.error} ${pluralize(c.error, 'błąd', 'błędy', 'błędów')}`,
    `${c.warning} do poprawy`,
    `${c.ok} OK`,
  ].join(' · ');
}

// ------------------------------------------------------------------ umiejętności z CV

const CATEGORY_LABEL: Record<string, string> = {
  'jezyk-programowania': 'Język programowania',
  framework: 'Framework',
  'baza-danych': 'Baza danych',
  devops: 'DevOps',
  chmura: 'Chmura',
  testy: 'Testy',
  dane: 'Dane',
  analityka: 'Analityka',
  architektura: 'Architektura',
  bezpieczenstwo: 'Bezpieczeństwo',
  biuro: 'Narzędzia biurowe',
  biznes: 'Biznes',
  design: 'Design',
  frontend: 'Frontend',
  inzynieria: 'Inżynieria',
  'jezyk-obcy': 'Język obcy',
  marketing: 'Marketing',
  metodyka: 'Metodyka',
  miekkie: 'Kompetencje miękkie',
  narzedzie: 'Narzędzie',
  platforma: 'Platforma',
  uprawnienia: 'Uprawnienia',
  wyksztalcenie: 'Wykształcenie',
};

export function categoryLabel(code: string | null): string {
  if (!code) return 'Inne';
  return CATEGORY_LABEL[code] ?? code.charAt(0).toUpperCase() + code.slice(1).replace(/-/g, ' ');
}

/** Od ilu wzmianek w CV umiejętność jest wstępnie zaznaczona (pojedyncza wzmianka bywa przypadkowa). */
export const PRESELECT_MIN_MENTIONS = 2;

/** Wstępnie zaznaczone: umiejętności spoza profilu, wspomniane w CV co najmniej {@link PRESELECT_MIN_MENTIONS} razy. */
export function initialSelection(suggestions: SkillSuggestion[]): Set<string> {
  return new Set(
    suggestions.filter((s) => !s.alreadyInProfile && s.mentions >= PRESELECT_MIN_MENTIONS).map((s) => s.name),
  );
}

export function toggleSelection(selected: Set<string>, name: string): Set<string> {
  const next = new Set(selected);
  if (next.has(name)) next.delete(name);
  else next.add(name);
  return next;
}

export function applySummary(r: ApplySkillsResult): string {
  if (r.added.length === 0) return 'Nic nie dodano — wybrane umiejętności są już w profilu.';
  const n = r.added.length;
  return `Dodano do profilu ${n} ${pluralize(n, 'umiejętność', 'umiejętności', 'umiejętności')}: ${r.added.join(', ')}.`;
}

// ------------------------------------------------------------------ dane wrażliwe

export const SEVERITY: Record<Severity, { label: string; tone: Tone }> = {
  high: { label: 'Usuń koniecznie', tone: 'danger' },
  medium: { label: 'Zbędne w CV', tone: 'warning' },
  info: { label: 'Twoja decyzja', tone: 'info' },
};

// ------------------------------------------------------------------ tailoring

export const MISSING_ACTION: Record<MissingAction, { label: string; tone: Tone }> = {
  LEARN: { label: 'Do nauki', tone: 'danger' },
  SKIP: { label: 'Można pominąć', tone: 'neutral' },
  DESCRIBE_IF_TRUE: { label: 'Opisz, jeśli to prawda', tone: 'info' },
};

/** Zmiana wyniku jako tekst: „+12 pkt” / „bez zmian”. */
export function scoreDelta(current: number, projected: number): string {
  const d = projected - current;
  return d > 0 ? `+${d} pkt` : 'bez zmian';
}
