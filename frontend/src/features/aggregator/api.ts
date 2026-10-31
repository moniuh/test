/** Wywołania REST modułu „Agregator ofert” (/api/aggregator/**). */
import { api } from '../../api/client';
import type { JobOffer, OfferStatus } from '../../api/types';
import type {
  AggregatorRun, AggregatorSettings, AggregatorSummary, ArchiveReport, Inbox, OfferSignals, Preview, SearchResponse,
  SeenResult, SourceRequest, SourceView,
} from './types';

export interface InboxQuery {
  onlyUnseen?: boolean;
  sourceId?: number | null;
  origin?: 'SOURCE' | 'MANUAL' | null;
  minScore?: number | null;
  sort?: 'rank' | 'match' | 'fresh';
}

/** Parametry zapytania bez pustych wartości. */
export function queryString(params: Record<string, string | number | boolean | null | undefined>): string {
  const q = new URLSearchParams();
  for (const [k, v] of Object.entries(params)) {
    if (v === null || v === undefined || v === '' || v === false) continue;
    q.set(k, String(v));
  }
  const s = q.toString();
  return s ? `?${s}` : '';
}

export const aggregatorApi = {
  sources: () => api.get<SourceView[]>('/api/aggregator/sources'),
  createSource: (req: SourceRequest) => api.post<SourceView>('/api/aggregator/sources', req),
  updateSource: (id: number, req: SourceRequest) => api.put<SourceView>(`/api/aggregator/sources/${id}`, req),
  setEnabled: (id: number, enabled: boolean) =>
    api.post<SourceView>(`/api/aggregator/sources/${id}/enabled`, { enabled }),
  deleteSource: (id: number) => api.del(`/api/aggregator/sources/${id}`),
  testSource: (req: Partial<SourceRequest>) => api.post<Preview>('/api/aggregator/sources/test', req),
  runSource: (id: number) => api.post<AggregatorRun>(`/api/aggregator/sources/${id}/run`),
  runAll: () => api.post<AggregatorRun[]>('/api/aggregator/run-all'),
  runs: (sourceId?: number | null, limit = 50) =>
    api.get<AggregatorRun[]>(`/api/aggregator/runs${queryString({ sourceId, limit })}`),
  settings: () => api.get<AggregatorSettings>('/api/aggregator/settings'),
  summary: () => api.get<AggregatorSummary>('/api/aggregator/summary'),
  inbox: (q: InboxQuery) =>
    api.get<Inbox>(
      `/api/aggregator/inbox${queryString({
        onlyUnseen: q.onlyUnseen, sourceId: q.sourceId, origin: q.origin, minScore: q.minScore, sort: q.sort,
      })}`,
    ),
  markSeen: (offerIds: number[]) => api.post<SeenResult>('/api/aggregator/inbox/seen', { offerIds }),
  markAllSeen: (sourceId?: number | null) =>
    api.post<SeenResult>('/api/aggregator/inbox/seen', { all: true, sourceId: sourceId ?? null }),
  search: (q: string, status?: OfferStatus | null) =>
    api.get<SearchResponse>(`/api/aggregator/search${queryString({ q, status })}`),
  archiveExpired: () => api.post<ArchiveReport>('/api/aggregator/archive-expired'),
  signals: (offerId: number) => api.get<OfferSignals>(`/api/aggregator/offers/${offerId}/signals`),
  setOfferStatus: (offerId: number, status: OfferStatus) =>
    api.post<JobOffer>(`/api/offers/${offerId}/status`, { status }),
};
