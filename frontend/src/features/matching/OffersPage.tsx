import { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../../api/client';
import type { JobOffer, MatchSummary, OfferStatus } from '../../api/types';
import { OFFER_STATUS, WORK_MODE, formatSalary } from '../../api/labels';
import { useAsync } from '../../hooks/useAsync';
import { useDocumentTitle } from '../../hooks/useDocumentTitle';
import { Badge, Card, EmptyState, ErrorBox, Loading, PageHeader, ScoreBadge } from '../../components/ui';

/** Lista ofert z wynikiem dopasowania (wersja bazowa; ranking rozbudowuje moduł „dopasowanie”). */
export function OffersPage() {
  useDocumentTitle('Oferty');
  const offers = useAsync(() => api.get<JobOffer[]>('/api/offers'), []);
  const matches = useAsync(() => api.get<MatchSummary[]>('/api/matches'), []);
  const [status, setStatus] = useState<OfferStatus | 'ALL'>('ALL');
  const scores = useMemo(() => new Map(matches.data?.map((m) => [m.offerId, m.score])), [matches.data]);
  const list = (offers.data ?? []).filter((o) => status === 'ALL' || o.status === status);

  return (
    <>
      <PageHeader
        title="Oferty"
        subtitle="Zapisane ogłoszenia z dopasowaniem do aktywnego CV."
        actions={<Link className="btn btn-primary" to="/oferty/nowa">Dodaj ofertę</Link>}
      />
      <div className="tabs" role="tablist" aria-label="Filtr statusu">
        {(['ALL', 'NEW', 'SAVED', 'REJECTED', 'ARCHIVED'] as const).map((s) => (
          <button key={s} type="button" role="tab" aria-selected={status === s} onClick={() => setStatus(s)}>
            {s === 'ALL' ? 'Wszystkie' : OFFER_STATUS[s]}
          </button>
        ))}
      </div>
      {offers.error && <ErrorBox message={offers.error} onRetry={offers.reload} />}
      {offers.loading && !offers.data && <Loading />}
      {offers.data && list.length === 0 && <EmptyState title="Brak ofert w tym widoku" />}
      <div className="grid">
        {list.map((o) => (
          <Card key={o.id} className="compact">
            <div className="spread">
              <div>
                <h2 style={{ fontSize: '1.05rem', margin: 0 }}>
                  <Link to={`/oferty/${o.id}`}>{o.title}</Link>
                </h2>
                <p className="muted small" style={{ margin: 0 }}>
                  {[o.company, o.location, o.workMode && WORK_MODE[o.workMode], formatSalary(o)].filter(Boolean).join(' · ')}
                </p>
              </div>
              <div className="row">
                {o.status !== 'NEW' && <Badge>{OFFER_STATUS[o.status]}</Badge>}
                <ScoreBadge score={scores.get(o.id)} />
              </div>
            </div>
          </Card>
        ))}
      </div>
    </>
  );
}
