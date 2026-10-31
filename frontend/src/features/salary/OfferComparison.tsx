import { useState, type FormEvent, type ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { api, errorMessage } from '../../api/client';
import type { JobOffer } from '../../api/types';
import { formatSalary } from '../../api/labels';
import { useAsync } from '../../hooks/useAsync';
import { Alert, Badge, Card, EmptyState, ErrorBox, Field, LiveMessage, Loading } from '../../components/ui';
import { salaryApi } from './api';
import type { ComparedOffer, Comparison, ContractKind, PerOfferSettings } from './types';
import {
  CONTRACT_KIND_LABEL, MAX_COMPARE, MIN_COMPARE, formatPln, parseAmount, toggleSelection,
} from './salaryUtils';

const B2B_VARIANTS: ContractKind[] = ['B2B_LINIOWY', 'B2B_RYCZALT', 'B2B_SKALA'];

interface SettingsForm {
  commute: string;
  extra: string;
  contract: ContractKind;
}

function b2bOnly(o: JobOffer): boolean {
  return o.contractTypes.includes('B2B') && !o.contractTypes.includes('UOP');
}

/** #64 Porównywarka 2–4 ofert: netto, benefity, płatny urlop, dojazd, wartość łączna i ranking. */
export function OfferComparison() {
  const offers = useAsync(() => api.get<JobOffer[]>('/api/offers'), []);
  const [selected, setSelected] = useState<number[]>([]);
  const [settings, setSettings] = useState<Record<number, SettingsForm>>({});
  const [result, setResult] = useState<Comparison>();
  const [error, setError] = useState<string>();
  const [busy, setBusy] = useState(false);
  const [live, setLive] = useState<string>();

  if (offers.loading && !offers.data) return <Loading />;
  if (offers.error) return <ErrorBox message={offers.error} onRetry={offers.reload} />;
  const list = [...(offers.data ?? [])]
    .filter((o) => o.status !== 'REJECTED' || selected.includes(o.id))
    .sort((a, b) => Number(b.salaryMin != null || b.salaryMax != null) - Number(a.salaryMin != null || a.salaryMax != null));
  if (list.length < MIN_COMPARE) {
    return <EmptyState title="Za mało ofert do porównania">Dodaj co najmniej dwie oferty z widełkami.</EmptyState>;
  }

  function settingsFor(id: number): SettingsForm {
    return settings[id] ?? { commute: '', extra: '', contract: 'B2B_LINIOWY' };
  }

  function updateSettings(id: number, patch: Partial<SettingsForm>) {
    setSettings((s) => ({ ...s, [id]: { ...settingsFor(id), ...patch } }));
  }

  async function compare(e: FormEvent) {
    e.preventDefault();
    if (selected.length < MIN_COMPARE) {
      setError(`Zaznacz od ${MIN_COMPARE} do ${MAX_COMPARE} ofert.`);
      return;
    }
    const perOffer: Record<number, PerOfferSettings> = {};
    for (const id of selected) {
      const s = settingsFor(id);
      const commute = parseAmount(s.commute);
      const extra = parseAmount(s.extra);
      if ((s.commute.trim() && commute == null) || (s.extra.trim() && extra == null)) {
        setError('Koszty dojazdu i inne benefity podaj jako kwoty, np. 250.');
        return;
      }
      perOffer[id] = { commuteMonthly: commute, extraBenefitsMonthly: extra, contract: s.contract };
    }
    setBusy(true);
    setError(undefined);
    try {
      const r = await salaryApi.compare({ offerIds: selected, perOffer });
      setResult(r);
      const best = r.offers.find((o) => o.rank === 1);
      setLive(best ? `Porównano ${r.offers.length} oferty. Najwyżej: ${best.title}.` : 'Porównano oferty.');
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  }

  const byId = new Map(list.map((o) => [o.id, o]));

  return (
    <div className="stack">
      <Card title="Porównanie ofert">
        <form onSubmit={compare} noValidate>
          <fieldset>
            <legend>Wybierz {MIN_COMPARE}–{MAX_COMPARE} oferty (zaznaczono {selected.length})</legend>
            <ul className="list-plain salary-offer-picker">
              {list.map((o) => {
                const checked = selected.includes(o.id);
                return (
                  <li key={o.id}>
                    <label className="salary-pick">
                      <input
                        type="checkbox"
                        checked={checked}
                        disabled={!checked && selected.length >= MAX_COMPARE}
                        onChange={() => setSelected((s) => toggleSelection(s, o.id))}
                      />
                      <span>
                        <strong>{o.title}</strong>
                        <span className="muted small"> · {o.company ?? 'firma nieznana'} · {formatSalary(o)}</span>
                      </span>
                    </label>
                  </li>
                );
              })}
            </ul>
          </fieldset>

          {selected.length > 0 && (
            <fieldset>
              <legend>Twoje koszty i dodatki (opcjonalnie)</legend>
              {selected.map((id) => {
                const o = byId.get(id);
                if (!o) return null;
                const s = settingsFor(id);
                return (
                  <div key={id} className="salary-settings">
                    <p className="label" style={{ margin: '0 0 0.35rem' }}>{o.title}</p>
                    <div className="grid-3">
                      <Field label="Dojazd miesięcznie (zł)" hint="Bilety, paliwo, parking">
                        {(fid, describedBy) => (
                          <input id={fid} aria-describedby={describedBy} inputMode="decimal" value={s.commute}
                            onChange={(e) => updateSettings(id, { commute: e.target.value })} />
                        )}
                      </Field>
                      <Field label="Inne benefity miesięcznie (zł)" hint="Np. średnia premia, której tabela nie wycenia">
                        {(fid, describedBy) => (
                          <input id={fid} aria-describedby={describedBy} inputMode="decimal" value={s.extra}
                            onChange={(e) => updateSettings(id, { extra: e.target.value })} />
                        )}
                      </Field>
                      {b2bOnly(o) && (
                        <Field label="Rozliczenie B2B">
                          {(fid) => (
                            <select id={fid} value={s.contract} onChange={(e) => updateSettings(id, { contract: e.target.value as ContractKind })}>
                              {B2B_VARIANTS.map((c) => <option key={c} value={c}>{CONTRACT_KIND_LABEL[c]}</option>)}
                            </select>
                          )}
                        </Field>
                      )}
                    </div>
                  </div>
                );
              })}
            </fieldset>
          )}
          {error && <ErrorBox message={error} />}
          <div className="form-actions">
            <button type="submit" className="btn btn-primary" disabled={busy || selected.length < MIN_COMPARE}>
              {busy ? 'Porównuję…' : 'Porównaj'}
            </button>
          </div>
          <LiveMessage message={live} />
        </form>
      </Card>

      {result && <ComparisonTable result={result} />}
    </div>
  );
}

function ComparisonTable({ result }: { result: Comparison }) {
  const cols = result.offers;
  const row = (label: string, cell: (o: ComparedOffer) => ReactNode, strong = false) => (
    <tr className={strong ? 'salary-row-result' : undefined}>
      <th scope="row">{label}</th>
      {cols.map((o) => <td key={o.offerId}>{cell(o)}</td>)}
    </tr>
  );
  return (
    <Card title="Zestawienie">
      <div className="table-wrap">
        <table className="salary-compare">
          <caption className="sr-only">Porównanie ofert — kolumny to oferty uporządkowane według rankingu</caption>
          <thead>
            <tr>
              <th scope="col">Pozycja</th>
              {cols.map((o) => (
                <th scope="col" key={o.offerId}>
                  <Link to={`/oferty/${o.offerId}`}>{o.title}</Link>
                  <div className="small muted salary-company">{o.company}</div>
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {row('Ranking', (o) => (o.rank ? (
              <Badge tone={o.rank === 1 ? 'success' : 'neutral'}>{o.rank === 1 ? '★ ' : ''}{o.rank}. miejsce</Badge>
            ) : <Badge tone="warning">bez rankingu</Badge>))}
            {row('Rozliczenie (szacunek)', (o) => o.contractLabel)}
            {row('Kwota miesięcznie (środek widełek)', (o) => (o.monthlyAmount != null ? `${formatPln(o.monthlyAmount)} ${o.basisLabel}` : 'brak widełek'))}
            {row('≈ UoP brutto', (o) => formatPln(o.comparableMonthly))}
            {row('Płatny urlop', (o) => (o.paidLeave ? '✓ tak' : `✗ nie (korekta −${formatPln(o.leaveCorrectionMonthly)})`))}
            {row('Na rękę miesięcznie', (o) => formatPln(o.netMonthly))}
            {row('Benefity (wycena)', (o) => (
              <>
                {formatPln(o.benefitsMonthly)}
                {o.benefits.length > 0 && (
                  <details className="small">
                    <summary>szczegóły</summary>
                    <ul className="salary-benefits">
                      {o.benefits.map((b) => (
                        <li key={b.name}>
                          {b.name}: {formatPln(b.monthly)} <span className="muted">({b.note})</span>
                        </li>
                      ))}
                    </ul>
                  </details>
                )}
              </>
            ))}
            {row('Inne benefity', (o) => formatPln(o.extraBenefitsMonthly))}
            {row('Dojazd', (o) => (o.commuteMonthly ? `−${formatPln(o.commuteMonthly)}` : formatPln(0)))}
            {row('Wartość miesięcznie', (o) => <strong>{formatPln(o.totalMonthly)}</strong>, true)}
            {row('Wartość rocznie', (o) => <strong>{formatPln(o.totalYearly)}</strong>, true)}
            {row('Uwagi', (o) => (o.notes.length ? <ul className="small salary-notes">{o.notes.map((n) => <li key={n}>{n}</li>)}</ul> : '—'))}
          </tbody>
        </table>
      </div>
      <Alert tone="warning">
        <span><span aria-hidden="true">⚠ </span>{result.disclaimer}</span>
      </Alert>
      <details>
        <summary className="label">Jak liczymy</summary>
        <ul className="small">
          {result.assumptions.map((a) => <li key={a}>{a}</li>)}
          {result.estimates.map((a) => <li key={a}>Szacunek: {a}</li>)}
        </ul>
      </details>
    </Card>
  );
}
