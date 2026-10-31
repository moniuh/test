/** Typy odpowiedzi modułu „Analityka i motywacja” (pl.radarpracy.analytics). */
import type { ApplicationStatus, InterviewKind, OfferStatus } from '../../api/types';

// --- #71 lejek ---
export type FunnelStageKey = 'SENT' | 'RESPONSE' | 'INTERVIEW' | 'OFFER' | 'ACCEPTED';

export interface FunnelStage {
  key: FunnelStageKey;
  label: string;
  count: number;
  conversionFromPrevious: number | null;
  conversionFromStart: number | null;
}

export interface Funnel {
  from: string | null;
  to: string | null;
  plannedExcluded: number;
  stages: FunnelStage[];
  summary: string;
}

// --- #72 skuteczność ---
export interface EffectivenessGroup {
  name: string;
  applications: number;
  responses: number;
  responseRate: number | null;
  interviews: number;
  interviewRate: number | null;
  medianDaysToResponse: number | null;
  smallSample: boolean;
}

export interface Insight {
  conclusive: boolean;
  text: string;
}

export interface Effectiveness {
  applications: number;
  minSample: number;
  bySource: EffectivenessGroup[];
  sourceInsight: Insight;
  byCv: EffectivenessGroup[];
  cvInsight: Insight;
}

// --- #73 pulpit ---
export interface Week {
  start: string;
  end: string;
  applications: number;
  goal: number;
  percent: number;
  remaining: number;
  daysLeft: number;
  achieved: boolean;
}

export interface WeekBar {
  start: string;
  end: string;
  applications: number;
  achieved: boolean;
}

export interface UpcomingInterview {
  interviewId: number;
  applicationId: number;
  offerId: number;
  company: string;
  position: string;
  scheduledAt: string;
  kind: InterviewKind | null;
  kindLabel: string;
}

export interface FollowUp {
  applicationId: number;
  offerId: number;
  company: string;
  position: string;
  dueDate: string;
  daysOverdue: number;
  status: ApplicationStatus;
  statusLabel: string;
}

export interface TopOffer {
  offerId: number;
  title: string;
  company: string | null;
  score: number;
  summary: string;
  status: OfferStatus;
  missingSkills: string[];
  sameCompanyApplicationId: number | null;
}

export interface Counters {
  activeOffers: number;
  openApplications: number;
  upcomingInterviews: number;
}

export interface Dashboard {
  candidateName: string | null;
  today: string;
  week: Week;
  streak: number;
  recentWeeks: WeekBar[];
  upcomingInterviews: UpcomingInterview[];
  overdueFollowUps: FollowUp[];
  topOffers: TopOffer[];
  counters: Counters;
}

// --- #97 co dalej ---
export type ActionLevel = 'HIGH' | 'MEDIUM' | 'LOW';

export interface NextAction {
  priority: number;
  level: ActionLevel;
  kind: string;
  title: string;
  reason: string;
  link: string;
}

// --- #30 luki ---
export type GapType = 'MISSING' | 'SOFT_SKILL' | 'LEVEL';

export interface UnlockedOffer {
  offerId: number;
  title: string;
  company: string | null;
  scoreBefore: number;
  scoreAfter: number;
}

export interface SkillGap {
  skill: string;
  category: string | null;
  offersMissing: number;
  mustHaveMissing: number;
  avgPointsLost: number;
  unlockedOffers: number;
  unlocked: UnlockedOffer[];
  type: GapType;
  hint: string | null;
}

export interface SkillGapReport {
  cvId: number | null;
  cvName: string | null;
  offersAnalyzed: number;
  threshold: number;
  gaps: SkillGap[];
  note: string;
}

// --- #75 trendy ---
export type Trend = 'UP' | 'DOWN' | 'STABLE' | 'UNKNOWN';

export interface SkillTrend {
  skill: string;
  category: string | null;
  offers: number;
  mustHave: number;
  niceToHave: number;
  share: number | null;
  previousOffers: number;
  previousShare: number | null;
  deltaPp: number | null;
  trend: Trend;
  candidateHas: boolean;
  candidateSource: 'CV' | 'profil' | null;
}

export interface SkillTrends {
  days: number;
  from: string;
  to: string;
  offersInWindow: number;
  previousFrom: string;
  previousTo: string;
  offersInPreviousWindow: number;
  comparable: boolean;
  candidateHasCount: number;
  skills: SkillTrend[];
  summary: string;
}

// --- #76 nastrój ---
export interface MoodEntry {
  id: number;
  date: string;
  mood: number;
  energy: number;
  note: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface MoodWarning {
  reasons: string[];
  message: string;
  suggestedGoal: number;
  support: string;
}

export interface MoodOverview {
  days: number;
  entries: MoodEntry[];
  today: MoodEntry | null;
  warning: MoodWarning | null;
}

export interface MoodSaved {
  entry: MoodEntry;
  warning: MoodWarning | null;
}

export interface MoodRequest {
  date?: string;
  mood: number;
  energy: number;
  note?: string | null;
}

// --- #79 raport ---
export interface ReportApplicationRow {
  applicationId: number;
  date: string;
  company: string;
  position: string;
  source: string | null;
  status: ApplicationStatus;
  statusLabel: string;
}

export interface ReportInterviewRow {
  interviewId: number;
  applicationId: number;
  date: string;
  company: string;
  position: string;
  kind: InterviewKind | null;
  kindLabel: string;
}

export interface ReportSummary {
  applicationsSent: number;
  distinctCompanies: number;
  responses: number;
  interviews: number;
  offers: number;
  followUpsSent: number;
}

export interface ActivityReport {
  candidateName: string | null;
  from: string;
  to: string;
  generatedAt: string;
  applications: ReportApplicationRow[];
  interviews: ReportInterviewRow[];
  summary: ReportSummary;
}
