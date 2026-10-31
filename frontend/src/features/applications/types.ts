/** Typy modułu „Aplikowanie i śledzenie” (odpowiadają pl.radarpracy.application.*). */
import type { ApplicationStatus, EventType, InterviewKind } from '../../api/types';

/** #41 — aplikacja z zaplanowanym follow-upem. `days` < 0 = po terminie, 0 = dziś. */
export interface FollowUpItem {
  applicationId: number;
  offerId: number;
  offerTitle: string;
  company: string | null;
  status: ApplicationStatus;
  appliedAt: string | null;
  lastContactAt: string | null;
  nextFollowUpAt: string;
  days: number;
  overdue: boolean;
  contactName: string | null;
  contactEmail: string | null;
}

export interface FollowUpList {
  today: string;
  followUpDays: number;
  horizonDays: number;
  overdue: FollowUpItem[];
  upcoming: FollowUpItem[];
}

/** #40 — pozycja osi czasu. */
export type TimelineKind = 'EVENT' | 'NOTE' | 'INTERVIEW';

export interface TimelineItem {
  key: string;
  kind: TimelineKind;
  at: string;
  title: string;
  detail: string | null;
  eventType: EventType | null;
  fromStatus: ApplicationStatus | null;
  toStatus: ApplicationStatus | null;
  eventId: number | null;
  interviewId: number | null;
  interviewKind: InterviewKind | null;
  durationMinutes: number | null;
  upcoming: boolean;
}

/** #42 — list motywacyjny oparty na dowodach. */
export type LetterTone = 'formalny' | 'swobodny';
export type LetterLanguage = 'pl' | 'en';

export interface LetterEvidence {
  requirement: string;
  quote: string;
  mustHave: boolean;
  /** Wymaganie spełnione tylko częściowo (np. mniej lat niż wymagane). */
  partial: boolean;
}

export interface CoverLetterResult {
  text: string;
  source: 'ai' | 'offline';
  evidence: LetterEvidence[];
  cvId: number | null;
  cvName: string | null;
  tone: LetterTone;
  language: LetterLanguage;
  notice: string | null;
}

/** #43 — szablony wiadomości. */
export interface MessageTemplate {
  id: string;
  name: string;
  description: string;
  subject: string;
  body: string;
  interviewContext: 'NONE' | 'PAST' | 'UPCOMING';
  suggestedFor: ApplicationStatus[];
}

export interface RenderedMessage {
  templateId: string;
  templateName: string;
  to: string | null;
  subject: string;
  body: string;
  text: string;
  missing: string[];
  eventId: number | null;
}

/** #46 — „Co wysłano”. */
export interface SnapshotView {
  applicationId: number;
  hasSnapshot: boolean;
  snapshotAt: string | null;
  cvVariantId: number | null;
  cvVariantName: string | null;
  cvSnapshot: string | null;
  coverLetterSnapshot: string | null;
  currentCvExists: boolean;
  currentCvName: string | null;
  cvChanged: boolean | null;
  linesAdded: number;
  linesRemoved: number;
  coverLetterChanged: boolean | null;
}

/** #47 — ostrzeżenie o ponownej aplikacji. */
export interface PreviousApplication {
  applicationId: number;
  offerId: number;
  offerTitle: string;
  company: string | null;
  status: ApplicationStatus;
  outcome: string;
  closed: boolean;
  date: string | null;
  similarity: number;
}

export interface ReapplyCheck {
  offerId: number;
  company: string | null;
  normalizedCompany: string;
  sameOffer: PreviousApplication[];
  sameCompany: PreviousApplication[];
  similarRole: PreviousApplication[];
  warning: string | null;
}

/** #45/#52 — dane formularza rozmowy (bez pól debriefu, które należą do modułu „rozmowy”). */
export interface InterviewDraft {
  scheduledAt: string;
  durationMinutes: number | null;
  kind: InterviewKind;
  location: string;
  interviewers: string;
  notes: string;
  questionsAsked: string;
}
