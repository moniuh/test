/** Wywołania API modułu „Aplikowanie i śledzenie”. */
import { api } from '../../api/client';
import type { ApplicationEvent, ApplicationRequest, ApplicationStatus, ApplicationView, Interview } from '../../api/types';
import type {
  CoverLetterResult, FollowUpList, LetterLanguage, LetterTone, MessageTemplate, ReapplyCheck, RenderedMessage,
  SnapshotView, TimelineItem,
} from './types';

const base = (id: number) => `/api/applications/${id}`;

export const applicationsApi = {
  list: () => api.get<ApplicationView[]>('/api/applications'),
  forOffer: (offerId: number) => api.get<ApplicationView[]>(`/api/applications?offerId=${offerId}`),
  get: (id: number) => api.get<ApplicationView>(base(id)),
  create: (req: ApplicationRequest) => api.post<ApplicationView>('/api/applications', req),
  update: (id: number, req: ApplicationRequest) => api.put<ApplicationView>(base(id), req),
  remove: (id: number) => api.del(base(id)),
  changeStatus: (id: number, status: ApplicationStatus, note?: string) =>
    api.post<ApplicationView>(`${base(id)}/status`, { status, note: note || null }),

  /** #39 — przeniesienie karty na tablicy. */
  move: (id: number, status: ApplicationStatus, position: number | null) =>
    api.post<ApplicationView>(`${base(id)}/move`, { status, position }),

  /** #40 — oś czasu i notatki. */
  timeline: (id: number) => api.get<TimelineItem[]>(`${base(id)}/timeline`),
  addNote: (id: number, note: string) => api.post<ApplicationEvent>(`${base(id)}/notes`, { note }),

  /** #41 — follow-upy. */
  followUps: (days?: number) => api.get<FollowUpList>(`/api/applications/follow-ups${days ? `?days=${days}` : ''}`),
  followUpSent: (id: number, note?: string) => api.post<ApplicationView>(`${base(id)}/follow-up`, { note: note || null }),
  snooze: (id: number, days: number) => api.post<ApplicationView>(`${base(id)}/snooze`, { days }),

  /** #42 — list motywacyjny. */
  coverLetter: (offerId: number, cvId: number | null, tone: LetterTone, language: LetterLanguage) =>
    api.post<CoverLetterResult>('/api/cover-letters', { offerId, cvId, tone, language }),
  saveCoverLetter: (id: number, coverLetter: string) =>
    api.put<ApplicationView>(`${base(id)}/cover-letter`, { coverLetter }),

  /** #43 — szablony wiadomości. */
  templates: () => api.get<MessageTemplate[]>('/api/message-templates'),
  renderMessage: (id: number, templateId: string) =>
    api.post<RenderedMessage>(`${base(id)}/messages`, { templateId }),
  saveMessage: (id: number, templateId: string, subject: string, body: string) =>
    api.post<RenderedMessage>(`${base(id)}/messages`, { templateId, save: true, subject, body }),

  /** #45/#52 — rozmowy (rdzeniowe /api/interviews) i eksport .ics. */
  interviews: (applicationId: number) => api.get<Interview[]>(`/api/interviews?applicationId=${applicationId}`),
  createInterview: (body: Partial<Interview> & { applicationId: number }) => api.post<Interview>('/api/interviews', body),
  updateInterview: (id: number, body: Partial<Interview>) => api.put<Interview>(`/api/interviews/${id}`, body),
  deleteInterview: (id: number) => api.del(`/api/interviews/${id}`),

  /** #46 — snapshot wysłanych dokumentów. */
  snapshot: (id: number) => api.get<SnapshotView>(`${base(id)}/snapshot`),
  takeSnapshot: (id: number) => api.post<SnapshotView>(`${base(id)}/snapshot`),

  /** #47 — sprawdzenie przed aplikowaniem. */
  check: (offerId: number) => api.get<ReapplyCheck>(`/api/applications/check?offerId=${offerId}`),
};

/** Adres pliku .ics jednej rozmowy (#45). */
export const interviewIcsUrl = (id: number) => `/api/interviews/${id}/ics`;
/** Adres pliku .ics ze wszystkimi nadchodzącymi rozmowami (#45). */
export const CALENDAR_ICS_URL = '/api/interviews/calendar.ics';
