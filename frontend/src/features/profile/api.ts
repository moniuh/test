/** Wywołania API modułu „Profil i CV”. */
import { api } from '../../api/client';
import type {
  ApplySkillsResult, CvAudit, CvImportResult, CvSkillsResult, SensitiveReport, TailoringResult,
} from './types';

export const cvToolsApi = {
  /** #1 — import pliku PDF/DOCX jako nowej wersji CV. */
  importCv(file: File, name?: string): Promise<CvImportResult> {
    const form = new FormData();
    form.append('file', file);
    if (name && name.trim()) form.append('name', name.trim());
    return api.upload<CvImportResult>('/api/cv/import', form);
  },
  /** #2 — umiejętności rozpoznane w CV. */
  skills: (cvId: number) => api.get<CvSkillsResult>(`/api/cv/${cvId}/skills`),
  applySkills: (cvId: number, names: string[]) =>
    api.post<ApplySkillsResult>(`/api/cv/${cvId}/skills/apply`, { names }),
  /** #4 — audyt ATS. */
  audit: (cvId: number) => api.get<CvAudit>(`/api/cv/${cvId}/audit`),
  /** #8 — dane wrażliwe. */
  sensitive: (cvId: number) => api.get<SensitiveReport>(`/api/cv/${cvId}/sensitive`),
  /** #5 — tailoring CV pod ofertę (bez cvId: aktywna wersja CV). */
  tailoring: (offerId: number, cvId: number | null) =>
    api.get<TailoringResult>(`/api/offers/${offerId}/tailoring${cvId != null ? `?cvId=${cvId}` : ''}`),
};
