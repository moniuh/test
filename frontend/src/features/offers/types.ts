/** Typy modułu „Pozyskiwanie i analiza ofert” (pl.radarpracy.offer.analysis). */
import type {
  ContractType, OfferStatus, Requirement, SalaryPeriod, Seniority, WorkMode,
} from '../../api/types';

/** Szkic oferty z parsera (#13) / importu z URL (#14) — pola jak w JobOffer. */
export interface OfferDraftDto {
  title: string | null;
  company: string | null;
  location: string | null;
  url: string | null;
  source: string | null;
  description: string | null;
  workMode: WorkMode | null;
  contractTypes: ContractType[];
  seniority: Seniority | null;
  salaryMin: number | null;
  salaryMax: number | null;
  salaryCurrency: string | null;
  salaryPeriod: SalaryPeriod | null;
  salaryGross: boolean | null;
  requirements: Requirement[];
  benefits: string[];
  publishedAt: string | null;
  expiresAt: string | null;
}

export interface ParsedField {
  field: string;
  label: string;
  value: string | null;
  /** 0–1; 0 = nie znaleziono */
  confidence: number;
  /** dosłowny fragment źródła */
  evidence: string | null;
  method: string;
  review: boolean;
}

export type ParseOrigin = 'text' | 'json-ld' | 'html';

export interface ParsedOffer {
  draft: OfferDraftDto;
  fields: ParsedField[];
  recognized: string[];
  toReview: string[];
  confidence: number;
  reviewThreshold: number;
  origin: ParseOrigin;
  warnings: string[];
}

export type Severity = 'info' | 'warning' | 'high';

export interface RedFlag {
  code: string;
  severity: Severity;
  title: string;
  why: string;
  quote: string | null;
}

export type RiskLevel = 'low' | 'medium' | 'high';

export interface ScamSignal {
  code: string;
  title: string;
  points: number;
  why: string;
  quote: string | null;
}

export interface ScamAssessment {
  level: RiskLevel;
  levelLabel: string;
  score: number;
  mediumThreshold: number;
  highThreshold: number;
  signals: ScamSignal[];
  recommendations: string[];
  method: string;
}

export interface DuplicateCandidate {
  offerId: number;
  title: string;
  company: string | null;
  source: string | null;
  status: OfferStatus;
  score: number;
  companyScore: number | null;
  titleScore: number | null;
  contentScore: number | null;
  sameUrl: boolean;
  duplicate: boolean;
  alreadyMarked: boolean;
  older: boolean;
  reasons: string[];
}

export interface DuplicateReport {
  offerId: number | null;
  threshold: number;
  minScore: number;
  weights: { company: number; title: number; content: number };
  candidates: DuplicateCandidate[];
}

export interface OfferAnalysis {
  offerId: number;
  redFlags: RedFlag[];
  scam: ScamAssessment;
  duplicates: DuplicateReport;
  source: 'offline' | 'ai';
}

export interface DuplicateCheckRequest {
  title: string | null;
  company: string | null;
  description: string | null;
  url: string | null;
  excludeId?: number | null;
}
