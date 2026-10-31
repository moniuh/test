/** Typy modułu „Profil i CV” (pl.radarpracy.profile.cv). */
import type { CandidateProfile, CvVariant } from '../../api/types';

/** #1 — wynik importu CV z pliku. */
export interface CvImportResult {
  cv: CvVariant;
  format: 'PDF' | 'DOCX';
  pages: number | null;
  characters: number;
  words: number;
  warnings: string[];
}

/** #2 — umiejętność rozpoznana w CV. */
export interface SkillSuggestion {
  name: string;
  category: string | null;
  years: number | null;
  evidence: string | null;
  yearsEvidence: string | null;
  mentions: number;
  alreadyInProfile: boolean;
  profileYears: number | null;
}

export interface CvSkillsResult {
  cvId: number;
  cvName: string;
  suggestions: SkillSuggestion[];
  newCount: number;
}

export interface ApplySkillsResult {
  added: string[];
  skipped: string[];
  profile: CandidateProfile;
}

/** #4 — audyt CV pod ATS. */
export type CheckStatus = 'ok' | 'warning' | 'error';

export interface AuditCheck {
  code: string;
  status: CheckStatus;
  title: string;
  details: string;
  fix: string | null;
  weight: number;
  points: number;
  examples: string[];
}

export interface AuditStats {
  words: number;
  bullets: number;
  sections: string[];
  recognizedSkills: string[];
}

export interface CvAudit {
  cvId: number;
  cvName: string;
  score: number;
  grade: string;
  checks: AuditCheck[];
  stats: AuditStats;
}

/** #8 — dane wrażliwe. */
export type Severity = 'high' | 'medium' | 'info';

export interface SensitiveFinding {
  category: string;
  label: string;
  severity: Severity;
  quote: string;
  why: string;
  suggestion: string;
}

export interface SensitiveReport {
  cvId: number;
  cvName: string;
  findings: SensitiveFinding[];
  summary: string;
}

/** #5 — tailoring CV pod ofertę. */
export interface ProfileAddition {
  skill: string;
  requirement: string;
  mustHave: boolean;
  profileYears: number | null;
  cvLine: string;
  sentence: string;
  scoreGain: number;
}

export interface RelatedSkill {
  skill: string;
  relatedSkill: string;
  requirement: string;
  mustHave: boolean;
  evidence: string | null;
  advice: string;
  sentence: string;
}

export type MissingAction = 'LEARN' | 'SKIP' | 'DESCRIBE_IF_TRUE';

export interface MissingItem {
  skill: string | null;
  requirement: string;
  mustHave: boolean;
  action: MissingAction;
  advice: string;
}

export interface RequirementNote {
  requirement: string;
  mustHave: boolean;
  note: string;
}

export interface TailoringResult {
  offerId: number;
  offerTitle: string;
  cvId: number | null;
  cvName: string | null;
  currentScore: number;
  projectedScore: number;
  fromProfile: ProfileAddition[];
  related: RelatedSkill[];
  missing: MissingItem[];
  notes: RequirementNote[];
  cvAddition: string;
  summary: string;
}
