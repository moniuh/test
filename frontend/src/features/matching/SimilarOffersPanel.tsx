import { Link } from 'react-router-dom';
import { OFFER_STATUS } from '../../api/labels';
import { useAsync } from '../../hooks/useAsync';
import { Badge, Card, EmptyState, ErrorBox, Loading } from '../../components/ui';
import { fetchSimilar } from './api';
import { similarityPercent } from './ranking';
import './matching.css';

/** Gniazdo modułu „dopasowanie”: podobne oferty (#36) — TF-IDF i podobieństwo cosinusowe. */
export function SimilarOffersPanel({ offerId }: { offerId: number }) {
  const similar = useAsync(() => fetchSimilar(offerId, 5), [offerId]);
  return (
    <Card title="Podobne oferty" className="matching-similar">
      {similar.error && <ErrorBox message={similar.error} onRetry={similar.reload} />}
      {similar.loading && !similar.data && <Loading label="Szukam podobnych ofert…" />}
      {similar.data && similar.data.length === 0 && (
        <EmptyState title="Brak podobnych ofert">
          <p className="small">Dodaj więcej ofert — porównujemy treść ogłoszeń i wymagane technologie.</p>
        </EmptyState>
      )}
      {similar.data && similar.data.length > 0 && (
        <ul className="list-plain">
          {similar.data.map((s) => {
            const pct = similarityPercent(s.similarity);
            return (
              <li key={s.offerId}>
                <div className="spread">
                  <Link to={`/oferty/${s.offerId}`}>{s.title}</Link>
                  {s.status !== 'NEW' && <Badge>{OFFER_STATUS[s.status]}</Badge>}
                </div>
                {s.company && <span className="small muted">{s.company}</span>}
                <span className="small">
                  <span className="matching-bar" aria-hidden="true">
                    <span style={{ width: `${pct}%` }} />
                  </span>
                  Podobieństwo {pct}%
                </span>
                {s.sharedSkills.length > 0 && (
                  <div className="chips">
                    <span className="small muted">Wspólne:</span>
                    {s.sharedSkills.slice(0, 6).map((k) => (
                      <span key={k} className="chip">{k}</span>
                    ))}
                  </div>
                )}
              </li>
            );
          })}
        </ul>
      )}
    </Card>
  );
}
