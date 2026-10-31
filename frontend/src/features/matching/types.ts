/** Typy modułu „dopasowanie i rekomendacje” (pl.radarpracy.matching.ranking). */
import type {
  ContractType, OfferStatus, RejectReason, SalaryPeriod, Seniority, WorkMode,
} from '../../api/types';

/** Podstawowe pola oferty na liście rankingu (bez treści). */
export interface OfferBrief {
  id: number;
  title: string;
  company: string | null;
  location: string | null;
  source: string | null;
  workMode: WorkMode | null;
  contractTypes: ContractType[];
  seniority: Seniority | null;
  salaryMin: number | null;
  salaryMax: number | null;
  salaryCurrency: string | null;
  salaryPeriod: SalaryPeriod | null;
  salaryGross: boolean | null;
  status: OfferStatus;
  rejectReason: RejectReason | null;
  duplicateOfId: number | null;
  tags: string[];
  publishedAt: string | null;
  expiresAt: string | null;
  createdAt: string | null;
}

export type RankComponentKey = 'cv' | 'salary' | 'workMode' | 'location' | 'freshness' | 'contract' | 'learned';

/** Składnik rankingu: wartość 0–1 (dla „learned” −1…+1), waga, punkty i wyjaśnienie. */
export interface RankComponent {
  key: RankComponentKey | string;
  name: string;
  value: number;
  weight: number;
  points: number;
  neutral: boolean;
  explanation: string;
}

export type ExclusionType = 'DEAL_BREAKER' | 'WORK_MODE' | 'LOCATION' | 'CONTRACT' | 'SALARY';

export interface Exclusion {
  type: ExclusionType;
  reason: string;
  evidence: string | null;
}

export interface RankedOffer {
  offer: OfferBrief;
  matchScore: number;
  matchSummary: string | null;
  missingSkills: string[];
  rankScore: number;
  comparableSalaryMin: number | null;
  comparableSalaryMax: number | null;
  comparableSalaryMid: number | null;
  components: RankComponent[];
  excluded: boolean;
  exclusions: Exclusion[];
}

export type RankingSort = 'RANK' | 'MATCH' | 'DATE' | 'SALARY';

/** Stan filtrów strony ofert. */
export interface RankingFilters {
  /** puste = wszystkie statusy */
  statuses: OfferStatus[];
  q: string;
  minScore: number | null;
  /** puste = wszystkie tryby */
  workModes: WorkMode[];
  minSalary: number | null;
  showExcluded: boolean;
  sort: RankingSort;
  /** null = aktywne CV */
  cvId: number | null;
}

export interface SavedSearch {
  id: number;
  name: string;
  query: string | null;
  minScore: number | null;
  minSalary: number | null;
  workModes: WorkMode[];
  statuses: OfferStatus[];
  includeExcluded: boolean;
  cvId: number | null;
  lastCheckedAt: string | null;
  createdAt: string | null;
  totalCount: number;
  newCount: number;
}

export interface SavedSearchRequest {
  name: string;
  query: string | null;
  minScore: number | null;
  minSalary: number | null;
  workModes: WorkMode[];
  statuses: OfferStatus[];
  includeExcluded: boolean;
  cvId: number | null;
}

export interface SavedSearchResults {
  search: SavedSearch;
  items: RankedOffer[];
  newOfferIds: number[];
}

export type FeatureType = 'SKILL' | 'WORK_MODE' | 'CONTRACT' | 'SENIORITY' | 'COMPANY';

export interface FeatureWeight {
  type: FeatureType;
  key: string;
  label: string;
  weight: number;
  points: number;
  positive: number;
  negative: number;
  explanation: string;
}

export interface PreferencesView {
  active: boolean;
  ratings: number;
  positive: number;
  negative: number;
  minRatings: number;
  maxPoints: number;
  features: FeatureWeight[];
  note: string;
}

export interface SimilarOffer {
  offerId: number;
  title: string;
  company: string | null;
  similarity: number;
  sharedSkills: string[];
  status: OfferStatus;
}
