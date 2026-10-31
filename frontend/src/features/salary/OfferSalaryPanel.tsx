import { Link } from 'react-router-dom';
import type { JobOffer } from '../../api/types';
import { useAsync } from '../../hooks/useAsync';
import { Alert, Card, ErrorBox, Loading } from '../../components/ui';
import { salaryApi } from './api';
import { CONTRACT_KIND_LABEL, calculatorLink, formatPln, formatRatio, negotiationLink, verdictView } from './salaryUtils';
import './salary.css';

/** Gniazdo modułu „wynagrodzenia”: netto (#63), na tle rynku (#69), link do negocjacji (#66). */
export function OfferSalaryPanel({ offer }: { offer: JobOffer }) {
  const bench = useAsync(() => salaryApi.benchmark(offer.id), [offer.id, offer.updatedAt]);
  const b = bench.data;
  const view = b ? verdictView(b.verdict) : null;

  return (
    <Card title="Na rękę i na tle rynku">
      {bench.loading && !b && <Loading />}
      {bench.error && <ErrorBox message={bench.error} onRetry={bench.reload} />}
      {b && view && (
        <div className="stack salary-panel">
          <Alert tone={view.tone}>
            <span>
              <strong><span aria-hidden="true">{view.icon} </span>{view.label}.</strong> {b.message}
            </span>
          </Alert>
          {b.salaryPresent && b.median != null && (
            <dl className="kv small">
              <dt>Ta oferta (środek)</dt>
              <dd>{formatPln(b.offerMid)} ≈ UoP brutto ({formatRatio(b.ratio)} mediany)</dd>
              <dt>Mediana rynku</dt>
              <dd>{formatPln(b.median)} — {b.peerGroup}, {b.peerCount} ofert</dd>
              {b.p25 != null && b.p75 != null && (
                <>
                  <dt>Typowy zakres</dt>
                  <dd>{formatPln(b.p25)} – {formatPln(b.p75)} (25.–75. percentyl)</dd>
                </>
              )}
            </dl>
          )}
          {b.notes.length > 0 && (
            <ul className="small muted salary-notes">
              {b.notes.map((n) => <li key={n}>{n}</li>)}
            </ul>
          )}
          {b.net && (
            <div>
              <p style={{ margin: 0 }}>
                <span className="muted small">Na rękę (szacunek): </span>
                <strong className="salary-net">{formatPln(b.net.netMonthly)}</strong>
                <span className="muted small"> / mies.</span>
              </p>
              <p className="small muted" style={{ margin: 0 }}>{b.netNote}</p>
            </div>
          )}
          {!b.net && b.netNote && <p className="small">{b.netNote}</p>}
          <div className="actions no-print">
            {b.net && b.netContract && (
              <Link className="btn btn-small" to={calculatorLink(b.netAmount, b.netContract)}>
                Przelicz w kalkulatorze<span className="sr-only"> ({CONTRACT_KIND_LABEL[b.netContract]})</span>
              </Link>
            )}
            <Link className="btn btn-small" to={negotiationLink(offer.id)}>Przygotuj negocjacje</Link>
          </div>
          <p className="small muted" style={{ margin: 0 }}>Szacunek na parametrach 2026 r., nie porada podatkowa.</p>
        </div>
      )}
    </Card>
  );
}
