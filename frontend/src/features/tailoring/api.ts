/** Wywołania API modułu „tailoring” (`/api/tailoring…`). */
import { api } from '../../api/client';
import type {
  CheckResult, DeleteResult, LibraryItem, OfferOption, SaveRequest, TailoredDetail, TailoringOptions, TailorPreview,
} from './types';

export const tailoringApi = {
  preview: (offerId: number, baseCvId: number | null, options: TailoringOptions) =>
    api.post<TailorPreview>('/api/tailoring/preview', { offerId, baseCvId, options }),
  check: (offerId: number, baseCvId: number | null, content: string) =>
    api.post<CheckResult>('/api/tailoring/check', { offerId, baseCvId, content }),
  save: (req: SaveRequest) => api.post<TailoredDetail>('/api/tailoring', req),
  list: (offerId?: number) =>
    api.get<LibraryItem[]>(offerId == null ? '/api/tailoring' : `/api/tailoring?offerId=${offerId}`),
  detail: (cvVariantId: number) => api.get<TailoredDetail>(`/api/tailoring/${cvVariantId}`),
  remove: (cvVariantId: number) => api.del(`/api/tailoring/${cvVariantId}`) as unknown as Promise<DeleteResult>,
  offers: () => api.get<OfferOption[]>('/api/tailoring/offers'),
};

/** Adres pliku DOCX dopasowanej wersji (link do pobrania). */
export const docxUrl = (cvVariantId: number) => `/api/tailoring/${cvVariantId}/docx`;
