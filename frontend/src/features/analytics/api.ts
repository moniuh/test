/** Wywołania API modułu analityki. */
import { api } from '../../api/client';
import { buildQuery } from './logic';
import type {
  ActivityReport, Dashboard, Effectiveness, Funnel, MoodOverview, MoodRequest, MoodSaved, NextAction, SkillGapReport,
  SkillTrends,
} from './types';

const BASE = '/api/analytics';

export const analyticsApi = {
  funnel: (from?: string, to?: string) => api.get<Funnel>(`${BASE}/funnel${buildQuery({ from, to })}`),
  effectiveness: () => api.get<Effectiveness>(`${BASE}/effectiveness`),
  dashboard: () => api.get<Dashboard>(`${BASE}/dashboard`),
  nextActions: () => api.get<NextAction[]>(`${BASE}/next-actions`),
  skillGaps: (cvId?: number | null) => api.get<SkillGapReport>(`${BASE}/skill-gaps${buildQuery({ cvId })}`),
  skillTrends: (days: number) => api.get<SkillTrends>(`${BASE}/skill-trends${buildQuery({ days })}`),
  report: (from: string, to: string) => api.get<ActivityReport>(`${BASE}/report${buildQuery({ from, to })}`),
  /** Adres pliku CSV do pobrania (link, nie fetch). */
  reportCsvUrl: (from: string, to: string) => `${BASE}/report.csv${buildQuery({ from, to })}`,
  mood: (days?: number) => api.get<MoodOverview>(`/api/mood${buildQuery({ days })}`),
  saveMood: (req: MoodRequest) => api.post<MoodSaved>('/api/mood', req),
};
