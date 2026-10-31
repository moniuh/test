/** Typy modułu „tailoring” (odpowiadają pl.radarpracy.tailoring.*). */
import type { ApplicationStatus, MatchLevel, OfferStatus } from '../../api/types';

export interface TailoringOptions {
  hideIrrelevantBullets: boolean | null;
  maxBulletsPerRole: number | null;
  useAi: boolean | null;
}

export type ViolationKind =
  | 'NEW_SKILL' | 'NEW_NUMBER' | 'NEW_EMPLOYER' | 'NEW_DATE' | 'MISSING_ROLE' | 'UNVERIFIED_LINE';

export interface GuardViolation {
  line: number;
  kind: ViolationKind;
  detail: string;
  text: string;
}

export interface GuardReport {
  passed: boolean;
  violations: GuardViolation[];
  checkedLines: number;
  verbatimLines: number;
  rewrittenLines: number;
  generatedLines: number;
  profileOnlySkills: string[];
}

export type RequirementChangeKind = 'GAINED_EVIDENCE' | 'IMPROVED' | 'UNCHANGED' | 'STILL_MISSING' | 'WORSE';

export interface RequirementChange {
  requirement: string;
  mustHave: boolean;
  levelBefore: MatchLevel;
  levelAfter: MatchLevel;
  pointsBefore: number;
  pointsAfter: number;
  maxPoints: number;
  evidenceAfter: string | null;
  change: RequirementChangeKind;
  note: string | null;
}

export interface ScoreComparison {
  before: number;
  after: number;
  delta: number;
  requirements: RequirementChange[];
  stillMissing: string[];
  summary: string;
}

export type DiffKind = 'SAME' | 'ADDED' | 'MOVED' | 'CHANGED' | 'REMOVED';

export interface DiffEntry {
  kind: DiffKind;
  text: string;
  previous: string | null;
  baseLine: number | null;
  newLine: number | null;
}

export interface BulletChange {
  text: string;
  originalPosition: number;
  newPosition: number | null;
  relevance: number;
  hidden: boolean;
  matchedSkills: string[];
}

export interface RoleChange {
  header: string | null;
  bullets: BulletChange[];
}

export interface AiRejection {
  original: string;
  proposed: string;
  reason: string;
}

export interface TailorPreview {
  offerId: number;
  offerTitle: string;
  offerCompany: string | null;
  baseCvId: number;
  baseCvName: string;
  suggestedName: string;
  content: string;
  source: 'ai' | 'offline';
  aiAvailable: boolean;
  aiNote: string | null;
  aiRejected: AiRejection[];
  guard: GuardReport;
  comparison: ScoreComparison;
  diff: DiffEntry[];
  competences: string[];
  addedFromProfile: string[];
  roles: RoleChange[];
  options: TailoringOptions;
}

export interface CheckResult {
  guard: GuardReport;
  comparison: ScoreComparison;
  diff: DiffEntry[];
}

export interface OfferRef {
  id: number;
  title: string | null;
  company: string | null;
  status: OfferStatus | null;
  exists: boolean;
}

export interface ApplicationRef {
  id: number;
  status: ApplicationStatus;
  appliedAt: string | null;
  cvVariantId: number | null;
  cvVariantName: string | null;
  usesThisVersion: boolean;
}

export interface LibraryItem {
  id: number;
  cvVariantId: number;
  name: string;
  cvExists: boolean;
  active: boolean;
  baseCvId: number | null;
  baseCvName: string | null;
  offer: OfferRef;
  scoreBefore: number;
  scoreAfter: number;
  source: 'ai' | 'offline';
  edited: boolean;
  acceptedViolations: boolean;
  violations: number;
  createdAt: string;
  applications: ApplicationRef[];
}

export interface TailoredDetail {
  item: LibraryItem;
  content: string | null;
  guard: GuardReport;
  comparison: ScoreComparison | null;
  diff: DiffEntry[];
  options: TailoringOptions | null;
}

export interface DeleteResult {
  cvDeleted: boolean;
  message: string;
}

export interface OfferOption {
  id: number;
  title: string;
  company: string | null;
  status: OfferStatus;
  score: number;
  tailoredCount: number;
}

export interface SaveRequest {
  offerId: number;
  baseCvId: number | null;
  options: TailoringOptions;
  content: string;
  name: string;
  activate: boolean;
  acceptViolations: boolean;
  source: 'ai' | 'offline';
  edited: boolean;
}
