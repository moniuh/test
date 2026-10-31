/** Typy modułu „rozmowy” (pl.radarpracy.interview). */
import type { ApplicationStatus, InterviewKind } from '../../api/types';

export type QuestionCategory = 'techniczne' | 'behawioralne' | 'motywacyjne' | 'luka' | 'o-firmę';

export interface InterviewQuestion {
  question: string;
  category: QuestionCategory;
  skill: string | null;
  why: string;
  tip: string;
  requirement: string | null;
  evidence: string | null;
}

export interface StarPart {
  detected: boolean;
  confidence: number;
  evidence: string | null;
  markers: string[];
}

export interface StarAssessment {
  score: number;
  star: { situation: StarPart; task: StarPart; action: StarPart; result: StarPart };
  hasNumbers: boolean;
  numbers: string[];
  wordCount: number;
  length: 'TOO_SHORT' | 'OK' | 'TOO_LONG';
  speakingMinutes: number;
  firstPersonSingularVsPlural: { singular: number; plural: number; verdict: 'SINGULAR' | 'PLURAL' | 'BALANCED' | 'NONE' };
  fillerWords: { word: string; count: number }[];
  fillerTotal: number;
  offerSkillsMentioned: string[];
  offerSkillsMissing: string[];
  feedback: string[];
  scoreBreakdown: { label: string; points: number; max: number }[];
  aiComment: string | null;
  source: 'ai' | 'offline';
  savedId: number | null;
}

export interface AssessRequest {
  question: string;
  answer: string;
  offerId?: number | null;
  save?: boolean;
  ai?: boolean;
}

export interface PracticeAnswer {
  id: number;
  question: string;
  answer: string;
  offerId: number | null;
  score: number;
  situation: boolean;
  task: boolean;
  action: boolean;
  result: boolean;
  hasNumbers: boolean;
  wordCount: number;
  feedback: string[];
  aiComment: string | null;
  createdAt: string;
}

export interface StarStory {
  id: number;
  title: string;
  situation: string | null;
  task: string | null;
  action: string | null;
  result: string | null;
  skills: string[];
  detectedSkills: string[];
  createdAt: string;
  updatedAt: string;
}

export interface StarStoryRequest {
  title: string;
  situation: string;
  task: string;
  action: string;
  result: string;
  skills: string[];
}

export interface StorySuggestion {
  story: StarStory;
  score: number;
  matchedSkills: string[];
  requirements: string[];
}

export interface StorySuggestions {
  offerId: number;
  offerTitle: string;
  suggestions: StorySuggestion[];
  uncoveredSkills: string[];
}

export interface InterviewRef {
  interviewId: number;
  applicationId: number;
  offerId: number | null;
  offerTitle: string | null;
  company: string | null;
  scheduledAt: string;
  kind: InterviewKind | null;
  durationMinutes: number | null;
  location: string | null;
  interviewers: string | null;
  applicationStatus: ApplicationStatus | null;
  selfRating: number | null;
  debriefedAt: string | null;
}

export interface Topic {
  topic: string;
  kind: 'SKILL' | 'WORD';
  count: number;
  interviewIds: number[];
}

export interface AskedQuestion {
  question: string;
  interviewId: number;
  applicationId: number;
  offerId: number | null;
  offerTitle: string | null;
  company: string | null;
  date: string;
  skills: string[];
}

export interface RatingPoint {
  interviewId: number;
  date: string;
  rating: number;
  offerTitle: string | null;
}

export interface Insights {
  upcoming: InterviewRef[];
  pendingDebriefs: InterviewRef[];
  past: InterviewRef[];
  debriefedCount: number;
  averageSelfRating: number | null;
  ratings: RatingPoint[];
  recurringTopics: Topic[];
  askedQuestions: AskedQuestion[];
  notes: string[];
}

export interface DebriefRequest {
  wentWell: string;
  toImprove: string;
  selfRating: number;
  questionsAsked: string;
}
