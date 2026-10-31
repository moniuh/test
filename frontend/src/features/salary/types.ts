/** Typy modułu „wynagrodzenia” (pl.radarpracy.salary.tools). */
import type { ContractType, Seniority } from '../../api/types';

export type ContractKind = 'UOP' | 'UZ' | 'B2B_LINIOWY' | 'B2B_RYCZALT' | 'B2B_SKALA';
export type ZusVariant = 'FULL' | 'PREFERENTIAL' | 'START_RELIEF';
export type KupVariant = 'BASIC' | 'RAISED';

export interface NetRequest {
  amount: number;
  contract: ContractKind;
  ryczaltRate?: number | null;
  zus?: ZusVariant | null;
  sickness?: boolean | null;
  ppk?: boolean | null;
  kup?: KupVariant | null;
  under26?: boolean | null;
  student?: boolean | null;
  costsMonthly?: number | null;
}

export interface NetItem {
  label: string;
  monthly: number;
  yearly: number;
  kind: 'INCOME' | 'DEDUCTION' | 'RESULT' | 'INFO';
}

export interface NetResult {
  year: number;
  contract: ContractKind;
  contractLabel: string;
  amount: number;
  items: NetItem[];
  socialMonthly: number;
  healthMonthly: number;
  pitMonthly: number;
  otherMonthly: number;
  netMonthly: number;
  netYearly: number;
  netFirstMonth: number | null;
  netLastMonth: number | null;
  assumptions: string[];
  estimates: string[];
  disclaimer: string;
}

export interface TaxParameters {
  year: number;
  ryczaltRates: number[];
  sources: string;
  estimates: string[];
  disclaimer: string;
  [key: string]: unknown;
}

export interface BenefitValue {
  name: string;
  matched: string | null;
  monthly: number;
  note: string;
}

export interface ComparedOffer {
  offerId: number;
  title: string;
  company: string | null;
  salaryPresent: boolean;
  contract: ContractKind;
  contractLabel: string;
  basis: string;
  basisLabel: string;
  monthlyAmount: number | null;
  comparableMonthly: number | null;
  netMonthly: number | null;
  paidLeave: boolean;
  leaveCorrectionMonthly: number;
  benefits: BenefitValue[];
  benefitsMonthly: number;
  extraBenefitsMonthly: number;
  commuteMonthly: number;
  totalMonthly: number | null;
  totalYearly: number | null;
  rank: number | null;
  notes: string[];
}

export interface PerOfferSettings {
  commuteMonthly?: number | null;
  extraBenefitsMonthly?: number | null;
  contract?: ContractKind | null;
  ryczaltRate?: number | null;
}

export interface CompareRequest {
  offerIds: number[];
  perOffer?: Record<number, PerOfferSettings>;
  b2bVariant?: ContractKind | null;
}

export interface Comparison {
  offers: ComparedOffer[];
  assumptions: string[];
  estimates: string[];
  disclaimer: string;
}

export interface Distribution {
  count: number;
  median: number | null;
  p25: number | null;
  p75: number | null;
  min: number | null;
  max: number | null;
  smallSample: boolean;
}

export interface StatsGroup {
  key: string;
  label: string;
  stats: Distribution;
}

export interface StatsSample {
  offerId: number;
  title: string;
  company: string | null;
  seniority: Seniority | null;
  contracts: ContractType[];
  comparableMid: number;
}

export interface SalaryStats {
  seniority: Seniority | null;
  contract: ContractType | null;
  basis: string;
  overall: Distribution;
  bySeniority: StatsGroup[];
  byContract: StatsGroup[];
  offers: StatsSample[];
  notes: string[];
}

export interface Amount {
  comparable: number;
  inOffer: number | null;
  text: string;
}

export interface AnchorComponent {
  label: string;
  value: number;
  decisive: boolean;
}

export interface NegotiationPlan {
  offerId: number;
  offerTitle: string;
  company: string | null;
  salaryPresent: boolean;
  basis: string;
  basisLabel: string;
  offerMin: number | null;
  offerMax: number | null;
  marketGroup: string;
  marketCount: number;
  marketMedian: number | null;
  marketP75: number | null;
  smallSample: boolean;
  candidateMinimum: number | null;
  anchor: Amount | null;
  target: Amount | null;
  counterOffer: Amount | null;
  walkAway: Amount | null;
  anchorComponents: AnchorComponent[];
  anchorExplanation: string | null;
  arguments: string[];
  phrases: string[];
  levers: string[];
  avoid: string[];
  notes: string[];
}

export type Verdict = 'BELOW' | 'IN_RANGE' | 'ABOVE' | 'NO_SALARY' | 'NO_DATA';

export interface SalaryBenchmark {
  offerId: number;
  salaryPresent: boolean;
  offerMid: number | null;
  peerGroup: string;
  sameLevel: boolean;
  peerCount: number;
  median: number | null;
  p25: number | null;
  p75: number | null;
  ratio: number | null;
  verdict: Verdict;
  message: string;
  smallSample: boolean;
  netContract: ContractKind | null;
  netAmount: number | null;
  net: NetResult | null;
  netNote: string | null;
  notes: string[];
}
