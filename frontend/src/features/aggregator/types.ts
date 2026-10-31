/** Typy modułu „Agregator ofert” (pl.radarpracy.aggregator). */
import type { ContractType, OfferStatus, SalaryPeriod, Seniority, WorkMode } from '../../api/types';

export type SourceType = 'RSS' | 'ATOM' | 'JSONLD_PAGE' | 'DEMO';
export type RunStatus = 'OK' | 'PARTIAL' | 'ERROR' | 'BLOCKED';
export type RunTrigger = 'MANUAL' | 'SCHEDULED' | 'STARTUP';
export type SignalKind =
  | 'INSTRUCTION_OVERRIDE'
  | 'ROLE_PLAY'
  | 'SCORING_MANIPULATION'
  | 'SYSTEM_PROMPT'
  | 'INVISIBLE_CHARACTERS'
  | 'HIDDEN_TEXT';
export type Severity = 'HIGH' | 'MEDIUM' | 'LOW';

export interface InjectionSignal {
  kind: SignalKind;
  severity: Severity;
  quote: string;
  detail: string;
}

export interface AggregatorRun {
  id: number;
  sourceId: number | null;
  sourceName: string | null;
  trigger: RunTrigger;
  startedAt: string;
  finishedAt: string | null;
  fetched: number;
  created: number;
  duplicates: number;
  filteredOut: number;
  errors: number;
  status: RunStatus;
  message: string | null;
}

export interface SourceFiltersData {
  includeKeywords: string[];
  excludeKeywords: string[];
  locations: string[];
  minSalary: number | null;
}

export interface SourceView extends SourceFiltersData {
  id: number;
  name: string;
  type: SourceType;
  url: string;
  enabled: boolean;
  lastRunAt: string | null;
  lastStatus: RunStatus | null;
  lastError: string | null;
  createdAt: string;
  usesNetwork: boolean;
  offersCount: number;
  unseenCount: number;
  lastRun: AggregatorRun | null;
}

export interface SourceRequest extends SourceFiltersData {
  name: string;
  type: SourceType;
  url: string;
  enabled: boolean;
}

export interface PreviewItem {
  externalId: string;
  title: string;
  company: string | null;
  location: string | null;
  url: string | null;
  publishedAt: string | null;
  expiresAt: string | null;
  workMode: WorkMode | null;
  contractTypes: ContractType[];
  seniority: Seniority | null;
  salaryMin: number | null;
  salaryMax: number | null;
  salaryCurrency: string | null;
  salaryPeriod: SalaryPeriod | null;
  salaryGross: boolean | null;
  requirementsCount: number;
  analysisNotes: string[];
  signals: InjectionSignal[];
  accepted: boolean;
  filterReason: string | null;
  duplicateOf: string | null;
  snippet: string | null;
}

export interface Preview {
  type: SourceType;
  format: string;
  title: string | null;
  total: number;
  items: PreviewItem[];
  warnings: string[];
}

export type Origin = 'SOURCE' | 'MANUAL';

export interface InboxItem {
  offerId: number;
  title: string;
  company: string | null;
  location: string | null;
  url: string | null;
  workMode: WorkMode | null;
  contractTypes: ContractType[];
  seniority: Seniority | null;
  salaryMin: number | null;
  salaryMax: number | null;
  salaryCurrency: string | null;
  salaryPeriod: SalaryPeriod | null;
  salaryGross: boolean | null;
  comparableMin: number | null;
  comparableMax: number | null;
  status: OfferStatus;
  origin: Origin;
  sourceId: number | null;
  sourceName: string | null;
  fetchedAt: string | null;
  publishedAt: string | null;
  expiresAt: string | null;
  score: number | null;
  mustHaveMet: number | null;
  mustHaveTotal: number | null;
  missingSkills: string[];
  signals: InjectionSignal[];
  seen: boolean;
  snippet: string;
}

export interface Inbox {
  items: InboxItem[];
  total: number;
  unseen: number;
}

export interface SeenResult {
  marked: number;
  unseen: number;
}

export interface AggregatorSummary {
  unseen: number;
  inboxTotal: number;
  sources: number;
  enabledSources: number;
  offersFromSources: number;
  offersWithSignals: number;
  lastRunAt: string | null;
  lastRunStatus: RunStatus | null;
  schedulerEnabled: boolean;
  intervalMinutes: number;
}

export interface AggregatorSettings {
  userAgent: string;
  perHostDelayMs: number;
  requestTimeoutSeconds: number;
  maxItemsPerFetch: number;
  schedulerEnabled: boolean;
  intervalMinutes: number;
  archiveAfterDays: number;
  maxResponseKb: number;
  demoFiles: string[];
}

export interface SearchResult {
  offerId: number;
  title: string;
  company: string | null;
  location: string | null;
  status: OfferStatus;
  sourceName: string | null;
  score: number;
  matchedFields: string[];
  snippet: string;
  highlights: [number, number][];
}

export interface SearchResponse {
  query: string;
  total: number;
  results: SearchResult[];
  terms: string[];
  phrases: string[];
  excluded: string[];
}

export interface ArchivedOffer {
  offerId: number;
  title: string;
  company: string | null;
  reason: string;
}

export interface ArchiveReport {
  archived: number;
  expired: number;
  stale: number;
  skippedWithApplication: number;
  offers: ArchivedOffer[];
  message: string;
}

export interface OfferSignals {
  offerId: number;
  origin: Origin;
  sourceName: string | null;
  analyzedNow: boolean;
  signals: InjectionSignal[];
}
