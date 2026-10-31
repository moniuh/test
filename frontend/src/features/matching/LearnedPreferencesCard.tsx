import { Badge, Card, ErrorBox, Loading } from '../../components/ui';
import type { FeatureWeight, PreferencesView } from './types';

function FeatureList({ items, limit }: { items: FeatureWeight[]; limit: number }) {
  return (
    <ul>
      {items.slice(0, limit).map((f) => (
        <li key={`${f.type}-${f.key}`}>
          {f.explanation}{' '}
          <span className="muted small">
            ({f.positive} zapis., {f.negative} odrz.)
          </span>
        </li>
      ))}
    </ul>
  );
}

/** Podgląd wyuczonych preferencji (#35): co podnosi, a co obniża ranking. */
export function LearnedPreferencesCard({
  prefs, loading, error, onRetry,
}: {
  prefs: PreferencesView | undefined;
  loading: boolean;
  error: string | undefined;
  onRetry: () => void;
}) {
  const visible = (prefs?.features ?? []).filter((f) => Math.round(f.points) !== 0);
  const likes = visible.filter((f) => f.weight > 0);
  const dislikes = visible.filter((f) => f.weight < 0);
  // przed włączeniem uczenia pokazujemy krótszy podgląd
  const limit = prefs?.active ? 6 : 3;
  return (
    <Card
      title="Twoje preferencje (wyuczone)"
      className="matching-prefs"
      actions={prefs && <Badge tone={prefs.active ? 'success' : 'neutral'}>{prefs.active ? 'aktywne' : 'nieaktywne'}</Badge>}
    >
      {error && <ErrorBox message={error} onRetry={onRetry} />}
      {loading && !prefs && <Loading />}
      {prefs && (
        <>
          <p className="small" style={{ marginBottom: '0.4rem' }}>{prefs.note}</p>
          {!prefs.active && (
            <>
              <div className="progress" aria-hidden="true">
                <span style={{ width: `${Math.min(100, (prefs.ratings / prefs.minRatings) * 100)}%` }} />
              </div>
              <p className="small muted" style={{ margin: '0.3rem 0 0' }}>
                Ocen: {prefs.ratings} z {prefs.minRatings} wymaganych.
              </p>
            </>
          )}
          {visible.length > 0 && !prefs.active && (
            <p className="small muted" style={{ margin: '0.5rem 0 0' }}>Podgląd — jeszcze bez wpływu na ranking:</p>
          )}
          {likes.length > 0 && (
            <>
              <h3>Podnosi ranking</h3>
              <FeatureList items={likes} limit={limit} />
            </>
          )}
          {dislikes.length > 0 && (
            <>
              <h3>Obniża ranking</h3>
              <FeatureList items={dislikes} limit={limit} />
            </>
          )}
          <p className="small muted" style={{ margin: '0.75rem 0 0' }}>
            Uczymy się z ofert, które zapisujesz i odrzucasz (z powodem) na stronie oferty. Wpływ na ranking najwyżej
            ±{Math.round(prefs.maxPoints)} pkt.
          </p>
        </>
      )}
    </Card>
  );
}
