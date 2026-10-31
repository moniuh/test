import type {
  ApplicationStatus, ContractType, EventType, InterviewKind, JobOffer, MatchLevel, OfferStatus, RejectReason,
  SalaryPeriod, Seniority, WorkMode,
} from './types';

export const WORK_MODE: Record<WorkMode, string> = { REMOTE: 'Zdalnie', HYBRID: 'Hybrydowo', ONSITE: 'Stacjonarnie' };

export const CONTRACT: Record<ContractType, string> = {
  UOP: 'Umowa o pracę', B2B: 'B2B', UZ: 'Umowa zlecenie', UOD: 'Umowa o dzieło', INTERNSHIP: 'Staż',
};

export const SENIORITY: Record<Seniority, string> = {
  INTERN: 'Stażysta', JUNIOR: 'Junior', MID: 'Mid', SENIOR: 'Senior', LEAD: 'Lead',
};

export const OFFER_STATUS: Record<OfferStatus, string> = {
  NEW: 'Nowa', SAVED: 'Zapisana', REJECTED: 'Odrzucona', ARCHIVED: 'Archiwum',
};

export const REJECT_REASON: Record<RejectReason, string> = {
  SALARY: 'Wynagrodzenie', LOCATION: 'Lokalizacja', WORK_MODE: 'Tryb pracy', TECH_STACK: 'Technologie',
  SENIORITY: 'Poziom stanowiska', CONTRACT: 'Forma umowy', COMPANY: 'Firma', RED_FLAGS: 'Czerwone flagi',
  DUPLICATE: 'Duplikat', OTHER: 'Inny powód',
};

export const SALARY_PERIOD: Record<SalaryPeriod, string> = { HOUR: 'h', DAY: 'dzień', MONTH: 'mies.', YEAR: 'rok' };

export const APPLICATION_STATUS: Record<ApplicationStatus, string> = {
  PLANNED: 'Do wysłania', APPLIED: 'Wysłana', SCREENING: 'Pierwszy kontakt', INTERVIEW: 'Rozmowy',
  OFFER: 'Oferta', ACCEPTED: 'Przyjęta', REJECTED: 'Odrzucona', WITHDRAWN: 'Wycofana', NO_RESPONSE: 'Brak odpowiedzi',
};

/** Kolejność kolumn tablicy. */
export const APPLICATION_STATUSES: ApplicationStatus[] = [
  'PLANNED', 'APPLIED', 'SCREENING', 'INTERVIEW', 'OFFER', 'ACCEPTED', 'REJECTED', 'WITHDRAWN', 'NO_RESPONSE',
];

export const EVENT_TYPE: Record<EventType, string> = {
  CREATED: 'Utworzono', STATUS_CHANGED: 'Zmiana statusu', NOTE: 'Notatka', FOLLOW_UP_SENT: 'Wysłano follow-up',
  MESSAGE: 'Wiadomość', INTERVIEW_SCHEDULED: 'Zaplanowano rozmowę', INTERVIEW_DONE: 'Rozmowa odbyta',
};

export const INTERVIEW_KIND: Record<InterviewKind, string> = {
  PHONE: 'Telefoniczna', VIDEO: 'Wideo', ONSITE: 'W biurze', TECHNICAL: 'Techniczna', HR: 'HR', FINAL: 'Finałowa',
};

export const MATCH_LEVEL: Record<MatchLevel, string> = {
  FULL: 'Spełnione', PARTIAL: 'Częściowo', RELATED: 'Pokrewne', PROFILE_ONLY: 'Tylko w profilu', MISSING: 'Brak',
};

const money = new Intl.NumberFormat('pl-PL', { maximumFractionDigits: 0 });

export function formatMoney(value: number | null | undefined, currency = 'PLN'): string {
  if (value == null) return '—';
  const cur = currency === 'PLN' ? 'zł' : currency;
  return `${money.format(value)} ${cur}`;
}

export function formatDate(value: string | null | undefined): string {
  if (!value) return '—';
  const d = new Date(value.length === 10 ? `${value}T00:00:00` : value);
  if (Number.isNaN(d.getTime())) return value;
  return d.toLocaleDateString('pl-PL', { day: 'numeric', month: 'short', year: 'numeric' });
}

export function formatDateTime(value: string | null | undefined): string {
  if (!value) return '—';
  const d = new Date(value);
  if (Number.isNaN(d.getTime())) return value;
  return d.toLocaleString('pl-PL', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' });
}

/** Widełki z oferty w zapisie „14 000–18 000 zł / mies. brutto”. */
export function formatSalary(o: Pick<JobOffer, 'salaryMin' | 'salaryMax' | 'salaryCurrency' | 'salaryPeriod' | 'salaryGross' | 'contractTypes'>): string {
  if (o.salaryMin == null && o.salaryMax == null) return 'Brak widełek';
  const cur = o.salaryCurrency ?? 'PLN';
  const range =
    o.salaryMin != null && o.salaryMax != null
      ? `${money.format(o.salaryMin)}–${formatMoney(o.salaryMax, cur)}`
      : o.salaryMin != null
        ? `od ${formatMoney(o.salaryMin, cur)}`
        : `do ${formatMoney(o.salaryMax, cur)}`;
  const period = o.salaryPeriod ? ` / ${SALARY_PERIOD[o.salaryPeriod]}` : '';
  const b2b = o.contractTypes?.includes('B2B') && !o.contractTypes?.includes('UOP');
  const kind = b2b ? ' netto + VAT' : o.salaryGross === true ? ' brutto' : o.salaryGross === false ? ' netto' : '';
  return `${range}${period}${kind}`;
}

/** Dzisiejsza data w formacie ISO (YYYY-MM-DD) w strefie użytkownika. */
export function todayIso(): string {
  const d = new Date();
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

/** Liczba dni od daty ISO do dziś (dodatnia = w przeszłości). */
export function daysSince(iso: string | null | undefined): number | null {
  if (!iso) return null;
  const d = new Date(iso.length === 10 ? `${iso}T00:00:00` : iso);
  const t = new Date(`${todayIso()}T00:00:00`);
  return Math.round((t.getTime() - d.getTime()) / 86_400_000);
}
