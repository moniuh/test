/** Wywołania REST modułu „dopasowanie i rekomendacje”. */
import { api } from '../../api/client';
import type {
  PreferencesView, RankedOffer, SavedSearch, SavedSearchRequest, SavedSearchResults, SimilarOffer,
} from './types';

/**
 * Ranking wszystkich ofert (także wykluczonych — UI ukrywa je sam, żeby pokazać licznik).
 * Fraza wyszukiwania jest filtrowana na serwerze (przeszukuje pełną treść ogłoszeń).
 */
export function fetchRanking(cvId: number | null, q: string): Promise<RankedOffer[]> {
  const params = new URLSearchParams({ includeExcluded: 'true' });
  if (cvId != null) params.set('cvId', String(cvId));
  if (q.trim()) params.set('q', q.trim());
  return api.get<RankedOffer[]>(`/api/ranking?${params.toString()}`);
}

export const fetchPreferences = () => api.get<PreferencesView>('/api/ranking/preferences');

export const fetchSimilar = (offerId: number, limit = 5) =>
  api.get<SimilarOffer[]>(`/api/offers/${offerId}/similar?limit=${limit}`);

export const fetchSavedSearches = () => api.get<SavedSearch[]>('/api/saved-searches');

export const createSavedSearch = (request: SavedSearchRequest) =>
  api.post<SavedSearch>('/api/saved-searches', request);

export const deleteSavedSearch = (id: number) => api.del(`/api/saved-searches/${id}`);

export const fetchSavedSearchResults = (id: number) =>
  api.get<SavedSearchResults>(`/api/saved-searches/${id}/results`);

export const markSavedSearchSeen = (id: number) => api.post<SavedSearch>(`/api/saved-searches/${id}/mark-seen`);
