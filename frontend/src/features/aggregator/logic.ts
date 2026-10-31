/** Czysta logika modułu „Agregator ofert” (etykiety, walidacja, formatowanie) — testowana w Vitest. */
import type { Tone } from '../../components/ui';
import type {
  AggregatorRun, InjectionSignal, RunStatus, RunTrigger, Severity, SignalKind, SourceRequest, SourceType,
} from './types';

export const SOURCE_TYPE_LABEL: Record<SourceType, string> = {
  RSS: 'Kanał RSS',
  ATOM: 'Kanał Atom',
  JSONLD_PAGE: 'Strona kariery (JSON-LD)',
  DEMO: 'Plik demonstracyjny',
};

export const SOURCE_TYPE_HINT: Record<SourceType, string> = {
  RSS: 'Adres kanału RSS 2.0, np. z zapisanego wyszukiwania w portalu z ofertami.',
  ATOM: 'Adres kanału Atom.',
  JSONLD_PAGE: 'Adres strony kariery firmy, która publikuje oferty jako dane schema.org JobPosting.',
  DEMO: 'Nazwa pliku z katalogu aggregator/ aplikacji, np. demo-feed.xml — bez połączenia z siecią.',
};

export const RUN_STATUS_LABEL: Record<RunStatus, string> = {
  OK: 'Pobrano',
  PARTIAL: 'Częściowo',
  ERROR: 'Błąd',
  BLOCKED: 'Zablokowane przez robots.txt',
};

export const TRIGGER_LABEL: Record<RunTrigger, string> = {
  MANUAL: 'ręcznie',
  SCHEDULED: 'harmonogram',
  STARTUP: 'przy starcie',
};

export const SIGNAL_KIND_LABEL: Record<SignalKind, string> = {
  INSTRUCTION_OVERRIDE: 'Polecenie zignorowania instrukcji',
  ROLE_PLAY: 'Zwrot do modelu AI',
  SCORING_MANIPULATION: 'Wymuszanie oceny kandydata',
  SYSTEM_PROMPT: 'Prompt systemowy',
  INVISIBLE_CHARACTERS: 'Niewidoczne znaki',
  HIDDEN_TEXT: 'Ukryty tekst w HTML',
};

export const SEVERITY_LABEL: Record<Severity, string> = { HIGH: 'wysokie', MEDIUM: 'średnie', LOW: 'niskie' };

export function runTone(status: RunStatus | null | undefined): Tone {
  switch (status) {
    case 'OK':
      return 'success';
    case 'PARTIAL':
    case 'BLOCKED':
      return 'warning';
    case 'ERROR':
      return 'danger';
    default:
      return 'neutral';
  }
}

export function severityTone(severity: Severity): Tone {
  return severity === 'HIGH' ? 'danger' : severity === 'MEDIUM' ? 'warning' : 'neutral';
}

const SEVERITY_ORDER: Severity[] = ['HIGH', 'MEDIUM', 'LOW'];

/** Najpoważniejszy sygnał (albo null). */
export function maxSeverity(signals: InjectionSignal[] | null | undefined): Severity | null {
  if (!signals?.length) return null;
  return SEVERITY_ORDER.find((s) => signals.some((x) => x.severity === s)) ?? null;
}

/** Polska odmiana liczebników: 1 oferta, 2 oferty, 5 ofert, 22 oferty, 112 ofert. */
export function plural(n: number, one: string, few: string, many: string): string {
  const abs = Math.abs(n);
  if (abs === 1) return one;
  const d = abs % 10;
  const h = abs % 100;
  if (d >= 2 && d <= 4 && !(h >= 12 && h <= 14)) return few;
  return many;
}

/** „5 nowych ofert” itp. */
export function countLabel(n: number, one: string, few: string, many: string): string {
  return `${n} ${plural(n, one, few, many)}`;
}

/** Zdanie podsumowujące przebieg pobierania (do ogłoszenia w aria-live). */
export function formatRunResult(run: Pick<AggregatorRun, 'status' | 'fetched' | 'created' | 'duplicates' | 'filteredOut' | 'errors' | 'sourceName' | 'message'>): string {
  const name = run.sourceName ? `„${run.sourceName}”: ` : '';
  if (run.status === 'ERROR' || run.status === 'BLOCKED') {
    return `${name}${RUN_STATUS_LABEL[run.status].toLowerCase()} — ${run.message ?? 'nie udało się pobrać źródła'}`;
  }
  const parts = [
    countLabel(run.created, 'nowa oferta', 'nowe oferty', 'nowych ofert'),
    countLabel(run.duplicates, 'duplikat', 'duplikaty', 'duplikatów'),
    countLabel(run.filteredOut, 'odfiltrowana', 'odfiltrowane', 'odfiltrowanych'),
  ];
  const errors = run.errors > 0 ? `, ${countLabel(run.errors, 'błąd', 'błędy', 'błędów')}` : '';
  return `${name}pobrano ${countLabel(run.fetched, 'pozycję', 'pozycje', 'pozycji')}: ${parts.join(', ')}${errors}.`;
}

/** Podsumowanie kilku przebiegów („Pobierz wszystkie”). */
export function formatRunsSummary(runs: AggregatorRun[]): string {
  if (runs.length === 0) return 'Brak włączonych źródeł do pobrania.';
  const created = runs.reduce((s, r) => s + r.created, 0);
  const failed = runs.filter((r) => r.status === 'ERROR' || r.status === 'BLOCKED').length;
  const base = `Pobrano ${countLabel(runs.length, 'źródło', 'źródła', 'źródeł')}: ${countLabel(created, 'nowa oferta', 'nowe oferty', 'nowych ofert')}`;
  return failed > 0 ? `${base}, ${countLabel(failed, 'źródło', 'źródła', 'źródeł')} z błędem.` : `${base}.`;
}

/** Lista z pola tekstowego: przecinki, średniki lub nowe linie; bez pustych i powtórzeń. */
export function parseList(text: string): string[] {
  const seen = new Set<string>();
  const out: string[] = [];
  for (const raw of text.split(/[,;\n]/)) {
    const v = raw.trim();
    const key = v.toLocaleLowerCase('pl');
    if (v && !seen.has(key)) {
      seen.add(key);
      out.push(v);
    }
  }
  return out;
}

export function joinList(list: string[] | null | undefined): string {
  return (list ?? []).join(', ');
}

export type SourceFormErrors = Partial<Record<'name' | 'url' | 'minSalary', string>>;

/** Walidacja formularza źródła po stronie przeglądarki (serwer sprawdza ponownie, m.in. SSRF). */
export function validateSource(form: Pick<SourceRequest, 'name' | 'type' | 'url' | 'minSalary'>): SourceFormErrors {
  const errors: SourceFormErrors = {};
  if (!form.name.trim()) errors.name = 'Podaj nazwę źródła.';
  else if (form.name.trim().length > 120) errors.name = 'Nazwa może mieć najwyżej 120 znaków.';
  const url = form.url.trim();
  if (!url) {
    errors.url = form.type === 'DEMO' ? 'Podaj nazwę pliku, np. demo-feed.xml.' : 'Podaj adres URL.';
  } else if (form.type === 'DEMO') {
    if (!/^[A-Za-z0-9][A-Za-z0-9._-]*$/.test(url) || url.includes('..')) {
      errors.url = 'Podaj samą nazwę pliku z katalogu aggregator/, np. demo-feed.xml.';
    }
  } else if (!/^https?:\/\/[^\s/]+/i.test(url)) {
    errors.url = 'Adres musi zaczynać się od http:// lub https://.';
  }
  if (form.minSalary != null && (!Number.isInteger(form.minSalary) || form.minSalary < 0 || form.minSalary > 1_000_000)) {
    errors.minSalary = 'Podaj liczbę całkowitą od 0 do 1 000 000.';
  }
  return errors;
}

/** „dziś”, „wczoraj”, „3 dni temu” albo data — względem `today` (YYYY-MM-DD). */
export function relativeDay(iso: string | null | undefined, today: string): string {
  if (!iso) return '—';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  const pad = (n: number) => String(n).padStart(2, '0');
  const local = `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
  const days = Math.round((Date.parse(`${today}T00:00:00Z`) - Date.parse(`${local}T00:00:00Z`)) / 86_400_000);
  if (days === 0) return 'dziś';
  if (days === 1) return 'wczoraj';
  if (days > 1 && days < 30) return `${days} ${plural(days, 'dzień', 'dni', 'dni')} temu`;
  return d.toLocaleDateString('pl-PL', { day: 'numeric', month: 'short', year: 'numeric' });
}

export interface Segment {
  text: string;
  mark: boolean;
}

/** Podział fragmentu na części z wyróżnieniem (zakresy mogą się nakładać lub wychodzić poza tekst). */
export function highlightSegments(text: string, highlights: [number, number][] | null | undefined): Segment[] {
  const ranges = (highlights ?? [])
    .map(([s, e]) => [Math.max(0, s), Math.min(text.length, e)] as [number, number])
    .filter(([s, e]) => e > s)
    .sort((a, b) => a[0] - b[0]);
  const merged: [number, number][] = [];
  for (const r of ranges) {
    const last = merged[merged.length - 1];
    if (last && r[0] <= last[1]) last[1] = Math.max(last[1], r[1]);
    else merged.push([r[0], r[1]]);
  }
  const out: Segment[] = [];
  let pos = 0;
  for (const [s, e] of merged) {
    if (s > pos) out.push({ text: text.slice(pos, s), mark: false });
    out.push({ text: text.slice(s, e), mark: true });
    pos = e;
  }
  if (pos < text.length) out.push({ text: text.slice(pos), mark: false });
  return out;
}

/** Opis rozbioru zapytania wyszukiwarki po polsku. */
export function describeQuery(terms: string[], phrases: string[], excluded: string[]): string {
  const parts: string[] = [];
  if (phrases.length) parts.push(`frazy: ${phrases.map((p) => `„${p}”`).join(', ')}`);
  if (terms.length) parts.push(`słowa (rdzenie): ${terms.join(', ')}`);
  if (excluded.length) parts.push(`bez: ${excluded.join(', ')}`);
  return parts.join('; ');
}

/**
 * Równoważnik miesięczny UoP brutto, gdy różni się od kwot z ogłoszenia (stawka godzinowa, waluta, B2B, netto)
 * — np. „≈ 16 130–19 350 zł/mies. brutto UoP”. Null, gdy nie ma czego pokazać.
 */
export function comparableNote(o: {
  salaryMin: number | null; salaryMax: number | null; comparableMin: number | null; comparableMax: number | null;
}): string | null {
  if (o.comparableMin == null && o.comparableMax == null) return null;
  if (o.comparableMin === o.salaryMin && o.comparableMax === o.salaryMax) return null;
  const fmt = (n: number | null) => (n == null ? '?' : n.toLocaleString('pl-PL'));
  const range = o.comparableMin != null && o.comparableMax != null && o.comparableMin !== o.comparableMax
    ? `${fmt(o.comparableMin)}–${fmt(o.comparableMax)}`
    : fmt(o.comparableMax ?? o.comparableMin);
  return `≈ ${range} zł/mies. brutto UoP`;
}

/** Pusty formularz nowego źródła. */
export function emptySource(type: SourceType = 'RSS'): SourceRequest {
  return {
    name: '', type, url: '', enabled: true, includeKeywords: [], excludeKeywords: [], locations: [], minSalary: null,
  };
}
