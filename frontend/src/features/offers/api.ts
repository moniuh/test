/** Wywołania API modułu „Pozyskiwanie i analiza ofert”. */
import { api } from '../../api/client';
import type { JobOffer, JobOfferDraft, RejectReason } from '../../api/types';
import type { DuplicateCheckRequest, DuplicateReport, OfferAnalysis, ParsedOffer } from './types';

export const offersApi = {
  /** #13: rozpoznanie pól z wklejonej treści (bez zapisu). */
  parse: (text: string, url?: string) => api.post<ParsedOffer>('/api/offers/parse', { text, url: url?.trim() || null }),
  /** #14: pobranie strony ogłoszenia i rozpoznanie pól. */
  importUrl: (url: string) => api.post<ParsedOffer>('/api/offers/import-url', { url: url.trim() }),
  /** #16: czy podobna oferta już istnieje (przed zapisem). */
  checkDuplicates: (req: DuplicateCheckRequest) => api.post<DuplicateReport>('/api/offers/duplicates/check', req),
  duplicates: (id: number) => api.get<DuplicateReport>(`/api/offers/${id}/duplicates`),
  markDuplicate: (id: number, originalId: number) =>
    api.post<JobOffer>(`/api/offers/${id}/mark-duplicate`, { originalId }),
  unmarkDuplicate: (id: number) => api.post<JobOffer>(`/api/offers/${id}/unmark-duplicate`),
  /** #18 + #19 + #16: zbiorcza analiza ogłoszenia. */
  analysis: (id: number) => api.get<OfferAnalysis>(`/api/offers/${id}/analysis`),
  /** #24: odrzucenie z powodem. */
  reject: (id: number, reason: RejectReason, note: string) =>
    api.post<JobOffer>(`/api/offers/${id}/reject`, { reason, note: note.trim() || null }),
  create: (draft: JobOfferDraft) => api.post<JobOffer>('/api/offers', draft),
};
