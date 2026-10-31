/** Czysta logika modułu „wynagrodzenia” (bez Reacta) — testowana w Vitest. */
import type { Tone } from '../../components/ui';
import type { ContractKind, KupVariant, NetRequest, StatsGroup, Verdict, ZusVariant } from './types';

export const CONTRACT_KINDS: ContractKind[] = ['UOP', 'UZ', 'B2B_LINIOWY', 'B2B_RYCZALT', 'B2B_SKALA'];

export const CONTRACT_KIND_LABEL: Record<ContractKind, string> = {
  UOP: 'Umowa o pracę',
  UZ: 'Umowa zlecenie',
  B2B_LINIOWY: 'B2B — podatek liniowy 19%',
  B2B_RYCZALT: 'B2B — ryczałt',
  B2B_SKALA: 'B2B — skala podatkowa',
};

export const ZUS_LABEL: Record<ZusVariant, string> = {
  FULL: 'Pełny ZUS',
  PREFERENTIAL: 'Preferencyjny (pierwsze 24 miesiące)',
  START_RELIEF: 'Ulga na start (pierwsze 6 miesięcy)',
};

export const KUP_LABEL: Record<KupVariant, string> = {
  BASIC: 'Podstawowe — 250 zł/mies.',
  RAISED: 'Podwyższone — 300 zł/mies. (dojazd z innej miejscowości)',
};

export const RYCZALT_RATES = [2, 3, 5.5, 8.5, 10, 12, 14, 15, 17];

export const SALARY_TABS = ['kalkulator', 'porownanie', 'statystyki', 'negocjacje'] as const;
export type SalaryTab = (typeof SALARY_TABS)[number];

export const SALARY_TAB_LABEL: Record<SalaryTab, string> = {
  kalkulator: 'Kalkulator netto',
  porownanie: 'Porównanie ofert',
  statystyki: 'Statystyki widełek',
  negocjacje: 'Negocjacje',
};

export function parseTab(value: string | null | undefined): SalaryTab {
  return (SALARY_TABS as readonly string[]).includes(value ?? '') ? (value as SalaryTab) : 'kalkulator';
}

export function isB2b(contract: ContractKind): boolean {
  return contract.startsWith('B2B');
}

export const MIN_COMPARE = 2;
export const MAX_COMPARE = 4;

/** Zaznaczenie/odznaczenie oferty do porównania (najwyżej `max`). */
export function toggleSelection(selected: number[], id: number, max = MAX_COMPARE): number[] {
  if (selected.includes(id)) return selected.filter((x) => x !== id);
  if (selected.length >= max) return selected;
  return [...selected, id];
}

/** Kwota wpisana przez użytkownika: „12 000”, „12000,50”, „12k”, „12 tys.”. */
export function parseAmount(text: string | null | undefined): number | null {
  if (text == null) return null;
  const t = text.trim().toLowerCase().replace(/zł|pln/g, '').trim();
  if (!t) return null;
  const m = t.match(/^(\d[\d\s ]*(?:[.,]\d+)?)\s*(k|tys\.?)?$/);
  if (!m) return null;
  let v = Number(m[1].replace(/[\s ]/g, '').replace(',', '.'));
  if (Number.isNaN(v)) return null;
  if (m[2]) v *= 1000;
  return v;
}

export interface NetForm {
  amount: string;
  contract: ContractKind;
  ryczaltRate: number;
  zus: ZusVariant;
  sickness: boolean;
  ppk: boolean;
  kup: KupVariant;
  under26: boolean;
  student: boolean;
  costsMonthly: string;
}

export const DEFAULT_NET_FORM: NetForm = {
  amount: '12000',
  contract: 'UOP',
  ryczaltRate: 12,
  zus: 'FULL',
  sickness: true,
  ppk: false,
  kup: 'BASIC',
  under26: false,
  student: false,
  costsMonthly: '',
};

/** Żądanie do kalkulatora — tylko opcje istotne dla wybranej formy umowy. */
export function buildNetRequest(form: NetForm): NetRequest | { error: string } {
  const amount = parseAmount(form.amount);
  if (amount == null || amount <= 0) return { error: 'Podaj kwotę miesięczną większą od zera, np. 12 000.' };
  const req: NetRequest = { amount, contract: form.contract };
  if (form.contract === 'UOP') {
    req.kup = form.kup;
    req.ppk = form.ppk;
    req.under26 = form.under26;
  } else if (form.contract === 'UZ') {
    req.sickness = form.sickness;
    req.ppk = form.ppk && !form.student;
    req.under26 = form.under26 || form.student;
    req.student = form.student;
  } else {
    req.zus = form.zus;
    req.sickness = form.zus === 'START_RELIEF' ? false : form.sickness;
    const costs = parseAmount(form.costsMonthly);
    if (form.costsMonthly.trim() && costs == null) return { error: 'Koszty działalności podaj jako liczbę.' };
    req.costsMonthly = costs ?? 0;
    if (form.contract === 'B2B_RYCZALT') req.ryczaltRate = form.ryczaltRate;
  }
  return req;
}

const plnFormats = new Map<number, Intl.NumberFormat>();

/** „12 345 zł” albo z groszami „12 345,67 zł”. */
export function formatPln(value: number | null | undefined, decimals = 0): string {
  if (value == null || Number.isNaN(value)) return '—';
  let f = plnFormats.get(decimals);
  if (!f) {
    f = new Intl.NumberFormat('pl-PL', { minimumFractionDigits: decimals, maximumFractionDigits: decimals });
    plnFormats.set(decimals, f);
  }
  return `${f.format(value)} zł`;
}

/** Szerokość słupka w % (0–100) względem maksimum. */
export function barWidth(value: number | null | undefined, max: number): number {
  if (value == null || max <= 0 || value <= 0) return 0;
  return Math.min(100, Math.round((value / max) * 1000) / 10);
}

/** „0.814” → „81%”. */
export function formatRatio(ratio: number | null | undefined): string {
  if (ratio == null) return '—';
  return `${Math.round(ratio * 100)}%`;
}

export interface VerdictView {
  tone: Tone;
  icon: string;
  label: string;
}

/** Werdykt porównania z rynkiem: kolor + ikona + tekst (kolor nie jest jedynym nośnikiem informacji). */
export function verdictView(verdict: Verdict): VerdictView {
  switch (verdict) {
    case 'BELOW':
      return { tone: 'warning', icon: '⚠', label: 'Poniżej rynku' };
    case 'ABOVE':
      return { tone: 'info', icon: '▲', label: 'Powyżej rynku' };
    case 'IN_RANGE':
      return { tone: 'success', icon: '✓', label: 'W granicach rynku' };
    case 'NO_SALARY':
      return { tone: 'neutral', icon: '?', label: 'Brak widełek' };
    default:
      return { tone: 'neutral', icon: 'ℹ', label: 'Za mało danych' };
  }
}

export interface StatsRow {
  key: string;
  label: string;
  count: number;
  median: number | null;
  width: number;
  range: string;
  small: boolean;
}

/** Wiersze wykresu słupkowego: mediana grupy względem największej mediany. */
export function statsRows(groups: StatsGroup[]): StatsRow[] {
  const max = Math.max(0, ...groups.map((g) => g.stats.median ?? 0));
  return groups.map((g) => ({
    key: g.key,
    label: g.label,
    count: g.stats.count,
    median: g.stats.median,
    width: barWidth(g.stats.median, max),
    range: g.stats.p25 != null && g.stats.p75 != null ? `${formatPln(g.stats.p25)} – ${formatPln(g.stats.p75)}` : '—',
    small: g.stats.smallSample,
  }));
}

export function calculatorLink(amount: number | null | undefined, contract: ContractKind | null | undefined): string {
  const params = new URLSearchParams({ tab: 'kalkulator' });
  if (amount != null) params.set('amount', String(Math.round(amount)));
  if (contract) params.set('contract', contract);
  return `/wynagrodzenia?${params.toString()}`;
}

export function negotiationLink(offerId: number): string {
  return `/wynagrodzenia?tab=negocjacje&offerId=${offerId}`;
}

/** Forma umowy z adresu URL (np. z panelu oferty); nieznana → null. */
export function parseContractKind(value: string | null | undefined): ContractKind | null {
  return CONTRACT_KINDS.includes(value as ContractKind) ? (value as ContractKind) : null;
}
