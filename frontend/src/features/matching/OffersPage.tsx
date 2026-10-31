import { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { api, errorMessage } from '../../api/client';
import type { CvVariant } from '../../api/types';
import { useAsync } from '../../hooks/useAsync';
import { useDocumentTitle } from '../../hooks/useDocumentTitle';
import { Card, EmptyState, ErrorBox, LiveMessage, Loading, PageHeader } from '../../components/ui';
import {
  createSavedSearch, deleteSavedSearch, fetchPreferences, fetchRanking, fetchSavedSearchResults, fetchSavedSearches,
  markSavedSearchSeen,
} from './api';
import { DEFAULT_FILTERS, applyFilters, filtersFromSearch, newBadge, plural, resultMessage, searchRequestFromFilters, sortRanked } from './ranking';
import type { RankingFilters, SavedSearch } from './types';
import { useDebounced } from './useDebounced';
import { RankingFiltersForm } from './RankingFiltersForm';
import { RankedOfferCard } from './RankedOfferCard';
import { SavedSearchesPanel } from './SavedSearchesPanel';
import { LearnedPreferencesCard } from './LearnedPreferencesCard';
import './matching.css';

/**
 * Oferty jako ranking (#31): dopasowanie do CV × preferencje, z twardymi filtrami deal-breakerów (#32),
 * zapisanymi wyszukiwaniami (#33) i korektą z uczenia preferencji (#35).
 */
export function OffersPage() {
  useDocumentTitle('Oferty');
  const [filters, setFilters] = useState<RankingFilters>(DEFAULT_FILTERS);
  const q = useDebounced(filters.q, 300);
  const cvs = useAsync(() => api.get<CvVariant[]>('/api/cv'), []);
  const ranking = useAsync(() => fetchRanking(filters.cvId, q), [filters.cvId, q]);
  const prefs = useAsync(fetchPreferences, []);
  const searches = useAsync(fetchSavedSearches, []);
  const [highlight, setHighlight] = useState<Set<number>>(new Set());
  const [message, setMessage] = useState<string>();
  const [actionError, setActionError] = useState<string>();
  const [busyId, setBusyId] = useState<number | null>(null);

  const result = useMemo(() => (ranking.data ? applyFilters(ranking.data, filters) : null), [ranking.data, filters]);
  const list = useMemo(() => (result ? sortRanked(result.shown, filters.sort) : []), [result, filters.sort]);
  const summary = ranking.data && result
    ? resultMessage(result.shown.length, ranking.data.length, result.excludedHidden, q)
    : undefined;

  function changeFilters(next: RankingFilters) {
    setFilters(next);
    setHighlight(new Set());
  }

  async function saveSearch(name: string) {
    const created = await createSavedSearch(searchRequestFromFilters(name, filters));
    searches.setData([...(searches.data ?? []), created]);
    setMessage(`Zapisano wyszukiwanie „${created.name}”. Nowe oferty pojawią się z odznaką przy tym wyszukiwaniu.`);
  }

  function replaceSearch(updated: SavedSearch) {
    searches.setData((searches.data ?? []).map((s) => (s.id === updated.id ? updated : s)));
  }

  async function applySearch(s: SavedSearch) {
    setActionError(undefined);
    setBusyId(s.id);
    try {
      setFilters(filtersFromSearch(s, filters.sort));
      const res = await fetchSavedSearchResults(s.id);
      setHighlight(new Set(res.newOfferIds));
      const fresh = res.newOfferIds.length;
      if (fresh > 0) replaceSearch(await markSavedSearchSeen(s.id));
      setMessage(
        `Zastosowano wyszukiwanie „${s.name}”: ${res.items.length} ${plural(res.items.length, 'oferta', 'oferty', 'ofert')}` +
          (fresh > 0 ? `, w tym ${newBadge(fresh)} (oznaczone jako przejrzane).` : ', bez nowych ofert.'),
      );
    } catch (e) {
      setActionError(errorMessage(e));
    } finally {
      setBusyId(null);
    }
  }

  async function markSeen(s: SavedSearch) {
    setActionError(undefined);
    setBusyId(s.id);
    try {
      replaceSearch(await markSavedSearchSeen(s.id));
      setMessage(`Oznaczono wyszukiwanie „${s.name}” jako przejrzane.`);
    } catch (e) {
      setActionError(errorMessage(e));
    } finally {
      setBusyId(null);
    }
  }

  async function removeSearch(s: SavedSearch) {
    if (!window.confirm(`Usunąć zapisane wyszukiwanie „${s.name}”?`)) return;
    setActionError(undefined);
    setBusyId(s.id);
    try {
      await deleteSavedSearch(s.id);
      searches.setData((searches.data ?? []).filter((x) => x.id !== s.id));
      setMessage(`Usunięto wyszukiwanie „${s.name}”.`);
    } catch (e) {
      setActionError(errorMessage(e));
    } finally {
      setBusyId(null);
    }
  }

  return (
    <>
      <PageHeader
        title="Oferty"
        subtitle="Ranking ofert: dopasowanie do CV × Twoje preferencje. Oferty z deal-breakerami są domyślnie ukryte."
        actions={<Link className="btn btn-primary" to="/oferty/nowa">Dodaj ofertę</Link>}
      />
      {actionError && <ErrorBox message={actionError} />}
      <LiveMessage message={message} />

      <div className="layout-sidebar">
        <div className="stack">
          <RankingFiltersForm
            filters={filters}
            onChange={changeFilters}
            cvs={cvs.data}
            excludedCount={result?.excludedCount ?? 0}
            onSave={saveSearch}
          />
          <Card title="Ranking ofert">
            <p className="matching-summary" role="status" aria-live="polite">
              {summary ?? ''}
            </p>
            {ranking.error && <ErrorBox message={ranking.error} onRetry={ranking.reload} />}
            {ranking.loading && !ranking.data && <Loading label="Liczę ranking…" />}
            {ranking.data && ranking.data.length === 0 && !q.trim() && (
              <EmptyState title="Nie masz jeszcze ofert">
                <Link className="btn btn-primary" to="/oferty/nowa">Dodaj pierwszą ofertę</Link>
              </EmptyState>
            )}
            {ranking.data && list.length === 0 && (ranking.data.length > 0 || q.trim() !== '') && (
              <EmptyState title="Brak ofert spełniających filtry">
                {result && result.excludedHidden > 0 ? (
                  <p className="small">Część ofert jest wykluczona — zaznacz „Pokaż wykluczone”.</p>
                ) : (
                  <p className="small">Zmień lub wyczyść filtry.</p>
                )}
              </EmptyState>
            )}
            {list.length > 0 && (
              <ol className="matching-results" aria-label="Oferty w kolejności sortowania">
                {list.map((item) => (
                  <li key={item.offer.id}>
                    <RankedOfferCard item={item} isNew={highlight.has(item.offer.id)} />
                  </li>
                ))}
              </ol>
            )}
          </Card>
        </div>
        <div className="stack">
          <SavedSearchesPanel
            searches={searches.data}
            loading={searches.loading}
            error={searches.error}
            onRetry={searches.reload}
            onApply={applySearch}
            onMarkSeen={markSeen}
            onDelete={removeSearch}
            busyId={busyId}
          />
          <LearnedPreferencesCard prefs={prefs.data} loading={prefs.loading} error={prefs.error} onRetry={prefs.reload} />
        </div>
      </div>
    </>
  );
}
