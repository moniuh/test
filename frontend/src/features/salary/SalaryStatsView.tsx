import { useState } from 'react';
import { Link } from 'react-router-dom';
import type { ContractType, Seniority } from '../../api/types';
import { CONTRACT, SENIORITY } from '../../api/labels';
import { useAsync } from '../../hooks/useAsync';
import { Alert, Card, EmptyState, ErrorBox, Field, Loading } from '../../components/ui';
import { salaryApi } from './api';
import type { StatsGroup } from './types';
import { formatPln, statsRows } from './salaryUtils';

/** #65 Statystyki widełek z zebranych ofert: mediana, kwartyle, min–max, grupy wg poziomu i umowy. */
export function SalaryStatsView() {
  const [seniority, setSeniority] = useState<Seniority | ''>('');
  const [contract, setContract] = useState<ContractType | ''>('');
  const stats = useAsync(() => salaryApi.stats(seniority, contract), [seniority, contract]);
  const s = stats.data;

  return (
    <div className="stack">
      <Card title="Statystyki widełek">
        <div className="grid-2">
          <Field label="Poziom stanowiska">
            {(id) => (
              <select id={id} value={seniority} onChange={(e) => setSeniority(e.target.value as Seniority | '')}>
                <option value="">Wszystkie poziomy</option>
                {(Object.keys(SENIORITY) as Seniority[]).map((k) => <option key={k} value={k}>{SENIORITY[k]}</option>)}
              </select>
            )}
          </Field>
          <Field label="Forma umowy">
            {(id) => (
              <select id={id} value={contract} onChange={(e) => setContract(e.target.value as ContractType | '')}>
                <option value="">Wszystkie formy</option>
                {(Object.keys(CONTRACT) as ContractType[]).map((k) => <option key={k} value={k}>{CONTRACT[k]}</option>)}
              </select>
            )}
          </Field>
        </div>
        {stats.loading && !s && <Loading />}
        {stats.error && <ErrorBox message={stats.error} onRetry={stats.reload} />}
        {s && (
          <div role="status" aria-live="polite">
            {s.overall.count === 0 ? (
              <EmptyState title="Brak ofert z widełkami dla tych filtrów">
                Zmień filtry albo <Link to="/oferty/nowa">dodaj oferty</Link> z wynagrodzeniem.
              </EmptyState>
            ) : (
              <>
                {s.overall.smallSample && (
                  <Alert tone="warning">
                    <span><span aria-hidden="true">⚠ </span>Mała próba: tylko {s.overall.count} {s.overall.count === 1 ? 'oferta' : 'oferty/ofert'} — traktuj wynik orientacyjnie.</span>
                  </Alert>
                )}
                <div className="grid-4 salary-tiles">
                  <Tile value={String(s.overall.count)} label="ofert w próbie" />
                  <Tile value={formatPln(s.overall.median)} label="mediana (≈ UoP brutto / mies.)" />
                  <Tile value={`${formatPln(s.overall.p25)} – ${formatPln(s.overall.p75)}`} label="25.–75. percentyl" small />
                  <Tile value={`${formatPln(s.overall.min)} – ${formatPln(s.overall.max)}`} label="minimum – maksimum" small />
                </div>
              </>
            )}
          </div>
        )}
      </Card>

      {s && s.overall.count > 0 && (
        <div className="grid-2">
          <Card title="Mediana wg poziomu">
            <Bars groups={s.bySeniority} />
          </Card>
          <Card title="Mediana wg formy umowy">
            <Bars groups={s.byContract} />
          </Card>
        </div>
      )}

      {s && s.overall.count > 0 && (
        <Card title="Skąd te liczby">
          <ul className="small">
            {s.notes.map((n) => <li key={n}>{n}</li>)}
          </ul>
          <details>
            <summary className="label">Oferty w próbie ({s.offers.length})</summary>
            <div className="table-wrap">
              <table>
                <caption className="sr-only">Oferty użyte w statystykach</caption>
                <thead>
                  <tr>
                    <th scope="col">Oferta</th>
                    <th scope="col">Poziom</th>
                    <th scope="col">Umowa</th>
                    <th scope="col" className="right">Środek widełek ≈ UoP brutto</th>
                  </tr>
                </thead>
                <tbody>
                  {s.offers.map((o) => (
                    <tr key={o.offerId}>
                      <th scope="row"><Link to={`/oferty/${o.offerId}`}>{o.title}</Link> <span className="muted small">{o.company}</span></th>
                      <td>{o.seniority ? SENIORITY[o.seniority] : '—'}</td>
                      <td>{o.contracts.map((c) => CONTRACT[c]).join(', ') || '—'}</td>
                      <td className="right nowrap">{formatPln(o.comparableMid)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </details>
        </Card>
      )}
    </div>
  );
}

function Tile({ value, label, small = false }: { value: string; label: string; small?: boolean }) {
  return (
    <div className="stat">
      <span className={`stat-value ${small ? 'salary-stat-small' : ''}`}>{value}</span>
      <span className="stat-label">{label}</span>
    </div>
  );
}

/** Wykres słupkowy HTML (klasy `.bars`): długość słupka = mediana; wartości zawsze także tekstem. */
function Bars({ groups }: { groups: StatsGroup[] }) {
  const rows = statsRows(groups);
  if (rows.length === 0) return <p className="muted small">Brak danych.</p>;
  return (
    <div className="bars salary-bars">
      {rows.map((r) => (
        <div className="bar-row" key={r.key}>
          <span>
            {r.label} <span className="muted small">({r.count}{r.small ? ', mała próba' : ''})</span>
            <span className="small muted salary-range">p25–p75: {r.range}</span>
          </span>
          <div className="bar-track" aria-hidden="true">
            <div className={`bar-fill ${r.small ? 'warning' : ''}`} style={{ width: `${r.width}%` }} />
          </div>
          <span className="small nowrap">{formatPln(r.median)}</span>
        </div>
      ))}
    </div>
  );
}
