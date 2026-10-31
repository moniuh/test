import { useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { errorMessage } from '../../api/client';
import { OFFER_STATUS } from '../../api/labels';
import type { OfferStatus } from '../../api/types';
import { Badge, EmptyState, ErrorBox, Field, Loading } from '../../components/ui';
import { aggregatorApi } from './api';
import { countLabel, describeQuery, highlightSegments } from './logic';
import type { SearchResponse } from './types';

const STATUSES: OfferStatus[] = ['NEW', 'SAVED', 'REJECTED', 'ARCHIVED'];

/** Wyszukiwarka pełnotekstowa BM25 po wszystkich ofertach (pomysł #61). */
export function SearchTab({ announce }: { announce: (message: string) => void }) {
  const [q, setQ] = useState('');
  const [status, setStatus] = useState<OfferStatus | ''>('');
  const [result, setResult] = useState<SearchResponse>();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string>();

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (!q.trim()) {
      setResult(undefined);
      announce('Wpisz, czego szukasz.');
      return;
    }
    setLoading(true);
    setError(undefined);
    try {
      const r = await aggregatorApi.search(q.trim(), status || null);
      setResult(r);
      announce(r.total === 0 ? 'Nie znaleziono ofert.' : `Znaleziono ${countLabel(r.total, 'ofertę', 'oferty', 'ofert')}.`);
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setLoading(false);
    }
  }

  return (
    <>
      <h2>Szukaj w ofertach</h2>
      <form role="search" className="aggregator-search" onSubmit={submit} aria-label="Wyszukiwarka ofert">
        <Field
          label="Szukane słowa"
          hint='Polskie znaki są opcjonalne, odmiana jest uwzględniana. "Fraza w cudzysłowie" musi wystąpić, -słowo wyklucza.'
        >
          {(id, describedBy) => (
            <input id={id} type="search" aria-describedby={describedBy} value={q} onChange={(e) => setQ(e.target.value)}
              placeholder='np. java "spring boot" -senior' />
          )}
        </Field>
        <Field label="Status oferty">
          {(id) => (
            <select id={id} value={status} onChange={(e) => setStatus(e.target.value as OfferStatus | '')}>
              <option value="">Wszystkie</option>
              {STATUSES.map((s) => <option key={s} value={s}>{OFFER_STATUS[s]}</option>)}
            </select>
          )}
        </Field>
        <button type="submit" className="btn btn-primary" disabled={loading}>Szukaj</button>
      </form>

      {error && <ErrorBox message={error} />}
      {loading && <Loading label="Szukam…" />}
      {result && !loading && (
        <>
          <p className="small muted">
            {countLabel(result.total, 'wynik', 'wyniki', 'wyników')}
            {describeQuery(result.terms, result.phrases, result.excluded) && ` · ${describeQuery(result.terms, result.phrases, result.excluded)}`}
            {' '}· ranking BM25: tytuł ×3, firma ×2, umiejętności ×2, treść ×1
          </p>
          {result.results.length === 0 && <EmptyState title="Brak wyników">Spróbuj krótszego zapytania lub innego słowa.</EmptyState>}
          <ol className="aggregator-list">
            {result.results.map((r) => (
              <li key={r.offerId} className="card compact aggregator-offer">
                <div className="aggregator-head">
                  <h3><Link to={`/oferty/${r.offerId}`}>{r.title}</Link></h3>
                  <span className="badges">
                    <Badge>{OFFER_STATUS[r.status]}</Badge>
                    <Badge title="Trafność BM25 — im wyżej, tym lepiej pasuje do zapytania">trafność {r.score.toLocaleString('pl-PL')}</Badge>
                  </span>
                </div>
                <p className="aggregator-meta">
                  {[r.company, r.location, r.sourceName && `źródło: ${r.sourceName}`].filter(Boolean).join(' · ')}
                  {r.matchedFields.length > 0 && ` · dopasowano w: ${r.matchedFields.join(', ')}`}
                </p>
                {r.snippet && (
                  <p className="aggregator-snippet">
                    {highlightSegments(r.snippet, r.highlights).map((seg, i) =>
                      seg.mark ? <mark key={i}>{seg.text}</mark> : <span key={i}>{seg.text}</span>)}
                  </p>
                )}
              </li>
            ))}
          </ol>
        </>
      )}
    </>
  );
}
