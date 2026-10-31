import { formatDateTime } from '../../api/labels';
import { Badge, Card, EmptyState, ErrorBox, Loading } from '../../components/ui';
import { describeFilters, filtersFromSearch, newBadge, plural } from './ranking';
import type { SavedSearch } from './types';

/** Zapisane wyszukiwania z odznaką „N nowych” (#33). */
export function SavedSearchesPanel({
  searches, loading, error, onRetry, onApply, onMarkSeen, onDelete, busyId,
}: {
  searches: SavedSearch[] | undefined;
  loading: boolean;
  error: string | undefined;
  onRetry: () => void;
  onApply: (s: SavedSearch) => void;
  onMarkSeen: (s: SavedSearch) => void;
  onDelete: (s: SavedSearch) => void;
  busyId: number | null;
}) {
  return (
    <Card title="Zapisane wyszukiwania" className="matching-saved">
      {error && <ErrorBox message={error} onRetry={onRetry} />}
      {loading && !searches && <Loading />}
      {searches && searches.length === 0 && (
        <EmptyState title="Brak zapisanych wyszukiwań">
          <p className="small">Ustaw filtry i wybierz „Zapisz wyszukiwanie” — policzymy nowe oferty od ostatniego sprawdzenia.</p>
        </EmptyState>
      )}
      {searches && searches.length > 0 && (
        <ul className="list-plain">
          {searches.map((s) => (
            <li key={s.id}>
              <div className="spread">
                <strong>{s.name}</strong>
                {s.newCount > 0 ? (
                  <Badge tone="info" title="Oferty dodane od ostatniego sprawdzenia">{newBadge(s.newCount)}</Badge>
                ) : (
                  <Badge>brak nowych</Badge>
                )}
              </div>
              <span className="small muted">
                {describeFilters(filtersFromSearch(s))} · {s.totalCount} {plural(s.totalCount, 'oferta', 'oferty', 'ofert')}
                {s.lastCheckedAt ? ` · sprawdzono ${formatDateTime(s.lastCheckedAt)}` : ''}
              </span>
              <div className="actions">
                <button type="button" className="btn btn-small btn-primary" onClick={() => onApply(s)} disabled={busyId === s.id}>
                  Pokaż<span className="sr-only"> wyniki wyszukiwania {s.name}</span>
                </button>
                {s.newCount > 0 && (
                  <button type="button" className="btn btn-small" onClick={() => onMarkSeen(s)} disabled={busyId === s.id}>
                    Oznacz jako przejrzane<span className="sr-only">: {s.name}</span>
                  </button>
                )}
                <button type="button" className="btn btn-small btn-danger" onClick={() => onDelete(s)} disabled={busyId === s.id}>
                  Usuń<span className="sr-only"> wyszukiwanie {s.name}</span>
                </button>
              </div>
            </li>
          ))}
        </ul>
      )}
    </Card>
  );
}
