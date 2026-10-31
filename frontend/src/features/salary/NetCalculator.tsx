import { useEffect, useState, type FormEvent } from 'react';
import { errorMessage } from '../../api/client';
import { useAsync } from '../../hooks/useAsync';
import { Alert, Card, ErrorBox, Field, LiveMessage } from '../../components/ui';
import { salaryApi } from './api';
import type { ContractKind, KupVariant, NetResult, ZusVariant } from './types';
import {
  CONTRACT_KINDS, CONTRACT_KIND_LABEL, DEFAULT_NET_FORM, KUP_LABEL, RYCZALT_RATES, ZUS_LABEL, buildNetRequest,
  formatPln, isB2b, type NetForm,
} from './salaryUtils';

/** #63 Kalkulator netto: UoP, zlecenie, B2B (liniowy, ryczałt, skala) — parametry 2026, zawsze szacunek. */
export function NetCalculator({ initialAmount, initialContract }: { initialAmount?: string | null; initialContract?: ContractKind | null }) {
  const [form, setForm] = useState<NetForm>({
    ...DEFAULT_NET_FORM,
    amount: initialAmount ?? DEFAULT_NET_FORM.amount,
    contract: initialContract ?? DEFAULT_NET_FORM.contract,
  });
  const [result, setResult] = useState<NetResult>();
  const [error, setError] = useState<string>();
  const [busy, setBusy] = useState(false);
  const [live, setLive] = useState<string>();
  const params = useAsync(() => salaryApi.taxParameters(), []);

  async function calculate(current: NetForm) {
    const req = buildNetRequest(current);
    if ('error' in req) {
      setError(req.error);
      return;
    }
    setBusy(true);
    setError(undefined);
    try {
      const r = await salaryApi.net(req);
      setResult(r);
      setLive(`Obliczono: około ${formatPln(r.netMonthly)} na rękę miesięcznie.`);
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }

  useEffect(() => {
    void calculate(form);
    // pierwsze obliczenie dla wartości startowych (np. z panelu oferty)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  function update<K extends keyof NetForm>(key: K, value: NetForm[K]) {
    setForm((f) => ({ ...f, [key]: value }));
  }

  function submit(e: FormEvent) {
    e.preventDefault();
    void calculate(form);
  }

  const b2b = isB2b(form.contract);
  const varies = result && result.netFirstMonth != null && result.netLastMonth != null
    && Math.abs(result.netFirstMonth - result.netLastMonth) >= 1;

  return (
    <div className="stack">
      <Card title="Kalkulator netto">
        <form onSubmit={submit} noValidate>
          <div className="grid-2">
            <Field
              label={b2b ? 'Przychód miesięcznie (netto, bez VAT)' : 'Wynagrodzenie miesięcznie brutto'}
              hint={b2b ? 'Kwota z faktury przed doliczeniem VAT, np. 20 000' : 'Np. 12 000 albo 12k'}
            >
              {(id, describedBy) => (
                <input
                  id={id}
                  aria-describedby={describedBy}
                  inputMode="decimal"
                  value={form.amount}
                  onChange={(e) => update('amount', e.target.value)}
                />
              )}
            </Field>
            <Field label="Forma umowy">
              {(id) => (
                <select id={id} value={form.contract} onChange={(e) => update('contract', e.target.value as ContractKind)}>
                  {CONTRACT_KINDS.map((c) => (
                    <option key={c} value={c}>{CONTRACT_KIND_LABEL[c]}</option>
                  ))}
                </select>
              )}
            </Field>
          </div>

          {form.contract === 'UOP' && (
            <fieldset>
              <legend>Opcje umowy o pracę</legend>
              <Field label="Koszty uzyskania przychodu">
                {(id) => (
                  <select id={id} value={form.kup} onChange={(e) => update('kup', e.target.value as KupVariant)}>
                    {(Object.keys(KUP_LABEL) as KupVariant[]).map((k) => (
                      <option key={k} value={k}>{KUP_LABEL[k]}</option>
                    ))}
                  </select>
                )}
              </Field>
              <div className="checks">
                <label><input type="checkbox" checked={form.ppk} onChange={(e) => update('ppk', e.target.checked)} /> Uczestniczę w PPK (2%)</label>
                <label><input type="checkbox" checked={form.under26} onChange={(e) => update('under26', e.target.checked)} /> Mam mniej niż 26 lat (ulga dla młodych)</label>
              </div>
            </fieldset>
          )}

          {form.contract === 'UZ' && (
            <fieldset>
              <legend>Opcje umowy zlecenia</legend>
              <div className="checks">
                <label><input type="checkbox" checked={form.sickness} disabled={form.student} onChange={(e) => update('sickness', e.target.checked)} /> Dobrowolne chorobowe</label>
                <label><input type="checkbox" checked={form.ppk} disabled={form.student} onChange={(e) => update('ppk', e.target.checked)} /> Uczestniczę w PPK</label>
                <label><input type="checkbox" checked={form.under26} onChange={(e) => update('under26', e.target.checked)} /> Mam mniej niż 26 lat</label>
                <label><input type="checkbox" checked={form.student} onChange={(e) => update('student', e.target.checked)} /> Jestem uczniem lub studentem (do 26 lat)</label>
              </div>
            </fieldset>
          )}

          {b2b && (
            <fieldset>
              <legend>Opcje działalności (B2B)</legend>
              <div className="grid-2">
                <Field label="Składki ZUS">
                  {(id) => (
                    <select id={id} value={form.zus} onChange={(e) => update('zus', e.target.value as ZusVariant)}>
                      {(Object.keys(ZUS_LABEL) as ZusVariant[]).map((z) => (
                        <option key={z} value={z}>{ZUS_LABEL[z]}</option>
                      ))}
                    </select>
                  )}
                </Field>
                <Field label="Koszty działalności miesięcznie (netto)" hint="Np. księgowość, sprzęt, coworking; puste = 0">
                  {(id, describedBy) => (
                    <input id={id} aria-describedby={describedBy} inputMode="decimal" value={form.costsMonthly}
                      onChange={(e) => update('costsMonthly', e.target.value)} />
                  )}
                </Field>
                {form.contract === 'B2B_RYCZALT' && (
                  <Field label="Stawka ryczałtu" hint="12% — usługi programistyczne; 8,5% — wiele innych usług; 15% — doradztwo">
                    {(id, describedBy) => (
                      <select id={id} aria-describedby={describedBy} value={form.ryczaltRate}
                        onChange={(e) => update('ryczaltRate', Number(e.target.value))}>
                        {RYCZALT_RATES.map((r) => (
                          <option key={r} value={r}>{String(r).replace('.', ',')}%</option>
                        ))}
                      </select>
                    )}
                  </Field>
                )}
              </div>
              <div className="checks">
                <label>
                  <input type="checkbox" checked={form.sickness && form.zus !== 'START_RELIEF'} disabled={form.zus === 'START_RELIEF'}
                    onChange={(e) => update('sickness', e.target.checked)} /> Dobrowolne chorobowe
                </label>
              </div>
            </fieldset>
          )}

          {error && <ErrorBox message={error} />}
          <div className="form-actions">
            <button type="submit" className="btn btn-primary" disabled={busy}>
              {busy ? 'Liczę…' : 'Oblicz'}
            </button>
          </div>
          <LiveMessage message={live} />
        </form>
      </Card>

      {result && (
        <Card title={`Wynik — ${result.contractLabel}`}>
          <div className="grid-3 salary-tiles">
            <div className="stat">
              <span className="stat-value">{formatPln(result.netMonthly)}</span>
              <span className="stat-label">na rękę miesięcznie{varies ? ' (średnio)' : ''}</span>
            </div>
            <div className="stat">
              <span className="stat-value">{formatPln(result.netYearly)}</span>
              <span className="stat-label">na rękę rocznie</span>
            </div>
            {varies ? (
              <div className="stat">
                <span className="stat-value salary-stat-small">
                  {formatPln(result.netFirstMonth)} → {formatPln(result.netLastMonth)}
                </span>
                <span className="stat-label">netto w styczniu → w grudniu</span>
              </div>
            ) : (
              <div className="stat">
                <span className="stat-value salary-stat-small">{formatPln(result.amount - result.netMonthly)}</span>
                <span className="stat-label">potrącenia razem miesięcznie (podatek, składki{isB2b(result.contract) ? ', koszty' : ''})</span>
              </div>
            )}
          </div>

          <div className="table-wrap">
            <table className="salary-table">
              <caption className="sr-only">Rozbicie wynagrodzenia na składki, podatek i kwotę na rękę</caption>
              <thead>
                <tr>
                  <th scope="col">Pozycja</th>
                  <th scope="col" className="right">Miesięcznie</th>
                  <th scope="col" className="right">Rocznie</th>
                </tr>
              </thead>
              <tbody>
                {result.items.map((i) => (
                  <tr key={i.label} className={`salary-row-${i.kind.toLowerCase()}`}>
                    <th scope="row">
                      {i.kind === 'DEDUCTION' && <span aria-hidden="true">− </span>}
                      {i.kind === 'DEDUCTION' && <span className="sr-only">potrącenie: </span>}
                      {i.kind === 'INFO' && <span className="sr-only">informacyjnie: </span>}
                      {i.label}
                    </th>
                    <td className="right nowrap">{formatPln(i.monthly, 2)}</td>
                    <td className="right nowrap">{formatPln(i.yearly, 2)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <Alert tone="warning">
            <span><span aria-hidden="true">⚠ </span>{result.disclaimer}</span>
          </Alert>
          <details>
            <summary className="label">Założenia i uproszczenia ({result.assumptions.length})</summary>
            <ul className="small">
              {result.assumptions.map((a) => <li key={a}>{a}</li>)}
              {result.estimates.map((a) => <li key={a}>Szacunek: {a}</li>)}
            </ul>
          </details>
          {params.data && <p className="small muted" style={{ marginTop: '0.5rem' }}>{params.data.sources}</p>}
        </Card>
      )}
    </div>
  );
}
