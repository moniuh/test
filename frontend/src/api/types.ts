/** Typy odpowiadające modelowi backendu (pl.radarpracy.*). */

export type WorkMode = 'REMOTE' | 'HYBRID' | 'ONSITE';
export type ContractType = 'UOP' | 'B2B' | 'UZ' | 'UOD' | 'INTERNSHIP';
export type Seniority = 'INTERN' | 'JUNIOR' | 'MID' | 'SENIOR' | 'LEAD';
export type OfferStatus = 'NEW' | 'SAVED' | 'REJECTED' | 'ARCHIVED';
export type RejectReason =
  | 'SALARY' | 'LOCATION' | 'WORK_MODE' | 'TECH_STACK' | 'SENIORITY' | 'CONTRACT' | 'COMPANY' | 'RED_FLAGS'
  | 'DUPLICATE' | 'OTHER';
export type SalaryPeriod = 'HOUR' | 'DAY' | 'MONTH' | 'YEAR';
export type ApplicationStatus =
  | 'PLANNED' | 'APPLIED' | 'SCREENING' | 'INTERVIEW' | 'OFFER' | 'ACCEPTED' | 'REJECTED' | 'WITHDRAWN'
  | 'NO_RESPONSE';
export type EventType =
  | 'CREATED' | 'STATUS_CHANGED' | 'NOTE' | 'FOLLOW_UP_SENT' | 'MESSAGE' | 'INTERVIEW_SCHEDULED' | 'INTERVIEW_DONE';
export type InterviewKind = 'PHONE' | 'VIDEO' | 'ONSITE' | 'TECHNICAL' | 'HR' | 'FINAL';
export type MatchLevel = 'FULL' | 'PARTIAL' | 'RELATED' | 'PROFILE_ONLY' | 'MISSING';

export interface SkillEntry {
  name: string;
  level: number | null;
  years: number | null;
}

export interface CandidateProfile {
  id: number;
  fullName: string | null;
  email: string | null;
  phone: string | null;
  location: string | null;
  headline: string | null;
  skills: SkillEntry[];
  desiredRoles: string[];
  preferredLocations: string[];
  workModes: WorkMode[];
  contractTypes: ContractType[];
  minMonthlySalary: number | null;
  willingToRelocate: boolean;
  dealBreakers: string[];
  weeklyApplicationGoal: number | null;
  followUpDays: number | null;
  activeCvId: number | null;
}

export interface CvVariant {
  id: number;
  name: string;
  targetRole: string | null;
  content: string;
  origin: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface Requirement {
  text: string;
  mustHave: boolean;
  minYears: number | null;
}

export interface JobOffer {
  id: number;
  title: string;
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
  tags: string[];
  notes: string | null;
  status: OfferStatus;
  rejectReason: RejectReason | null;
  rejectNote: string | null;
  duplicateOfId: number | null;
  publishedAt: string | null;
  expiresAt: string | null;
  createdAt: string;
  updatedAt: string;
}

/** Dane do utworzenia/edycji oferty (bez pól nadawanych przez serwer). */
export type JobOfferDraft = Partial<Omit<JobOffer, 'id' | 'createdAt' | 'updatedAt'>> & { title: string };

export interface RequirementMatch {
  requirement: string;
  mustHave: boolean;
  weight: number;
  level: MatchLevel;
  credit: number;
  points: number;
  maxPoints: number;
  skills: string[];
  evidence: string | null;
  evidenceVerified: boolean;
  note: string | null;
  minYears: number | null;
  candidateYears: number | null;
}

export interface MatchResult {
  offerId: number;
  cvId: number | null;
  cvName: string | null;
  score: number;
  mustHaveMet: number;
  mustHaveTotal: number;
  niceMet: number;
  niceTotal: number;
  requirements: RequirementMatch[];
  matchedSkills: string[];
  missingSkills: string[];
  summary: string;
  explanation: string[];
}

export interface MatchSummary {
  offerId: number;
  score: number;
  mustHaveMet: number;
  mustHaveTotal: number;
  missingSkills: string[];
}

export interface NormalizedSalary {
  present: boolean;
  monthlyMin: number | null;
  monthlyMax: number | null;
  basis: 'UOP_GROSS' | 'B2B_NET' | 'NET' | 'UNKNOWN';
  comparableMin: number | null;
  comparableMax: number | null;
  explanation: string;
}

export interface SalaryRange {
  min: number | null;
  max: number | null;
  currency: string;
  period: SalaryPeriod;
  gross: boolean | null;
  contractHint: ContractType | null;
  matchedText: string;
}

export interface ApplicationView {
  id: number;
  offerId: number;
  offerTitle: string;
  company: string | null;
  location: string | null;
  offerSource: string | null;
  status: ApplicationStatus;
  appliedAt: string | null;
  source: string | null;
  cvVariantId: number | null;
  cvVariantName: string | null;
  cvSnapshot: string | null;
  coverLetter: string | null;
  nextFollowUpAt: string | null;
  lastContactAt: string | null;
  firstResponseAt: string | null;
  boardPosition: number | null;
  contactName: string | null;
  contactEmail: string | null;
  salaryExpectation: number | null;
  notes: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface ApplicationRequest {
  offerId?: number;
  status?: ApplicationStatus;
  appliedAt?: string | null;
  source?: string | null;
  cvVariantId?: number | null;
  coverLetter?: string | null;
  nextFollowUpAt?: string | null;
  contactName?: string | null;
  contactEmail?: string | null;
  salaryExpectation?: number | null;
  notes?: string | null;
}

export interface ApplicationEvent {
  id: number;
  applicationId: number;
  type: EventType;
  fromStatus: ApplicationStatus | null;
  toStatus: ApplicationStatus | null;
  note: string | null;
  occurredAt: string;
}

export interface Interview {
  id: number;
  applicationId: number;
  scheduledAt: string;
  durationMinutes: number | null;
  kind: InterviewKind | null;
  location: string | null;
  interviewers: string | null;
  notes: string | null;
  questionsAsked: string | null;
  wentWell: string | null;
  toImprove: string | null;
  selfRating: number | null;
  debriefedAt: string | null;
}

export interface AiStatus {
  available: boolean;
  model: string;
  reason: string | null;
  maskedCategories: string[];
}
