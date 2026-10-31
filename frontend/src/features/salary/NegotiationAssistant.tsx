import { useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../../api/client';
import type { JobOffer } from '../../api/types';
import { formatSalary } from '../../api/labels';
import { useAsync } from '../../hooks/useAsync';
import { Alert, Badge, Card, EmptyState, ErrorBox, Field, LiveMessage, Loading } from '../../components/ui';
import { salaryApi } from './api';
import type { Amount, NegotiationPlan } from './types';
import { formatPln } from './salaryUtils';

/** #66 Asystent negocjacji: kotwica, cel, kontroferta, próg wyjścia i gotowe sformułowania dla wybranej oferty. */
export function NegotiationAssistant({ offerId, onOfferChange }: { offerId: number | null; onOfferChange: (id: number | null) => void }) {
  const offers = useAsync(() => api.get<JobOffer[]>('/api/offers'), []);
  const plan = useAsync(() => (offerId ? salaryApi.negotiation(offerId) : Promise.resolve(undefined)), [offerId]);

  return (
    <div className="stack">
      <Card title="Asystent negocjacji">
        {offers.error && <ErrorBox message={offers.error} onRetry={offers.reload} />}
        <Field label="Oferta" hint="Plan liczy aplikacja z widełek oferty, zebranych ofert i minimum z Twojego profilu.">
          {(id, describedBy) => (
            <select id={id} aria-describedby={describedBy} value={offerId ?? ''}
              onChange={(e) => onOfferChange(e.target.value ? Number(e.target.value) : null)}>
              <option value="">— wybierz ofertę —</option>
              {offers.data?.map((o) => (
                <option key={o.id} value={o.id}>
                  {o.title}{o.company ? ` · ${o.company}` : ''} · {formatSalary(o)}
                </option>
              ))}
            </select>
          )}
        </Field>
        {!offerId && <p className="muted small">Wybierz ofertę, dla której przygotowujesz rozmowę o wynagrodzeniu.</p>}
      </Card>
      {offerId && plan.loading && !plan.data && <Loading label="Przygotowuję plan…" />}
      {plan.error && <ErrorBox message={plan.error} onRetry={plan.reload} />}
      {offerId && plan.data && <PlanView plan={plan.data} />}
    </div>
  );
}

function AmountTile({ label, amount, hint }: { label: string; amount: Amount | null; hint: string }) {
  return (
    <div className="stat salary-amount">
      <span className="stat-label">{label}</span>
      <span className="stat-value salary-stat-small">{amount ? amount.text : '—'}</span>
      {amount && amount.text.indexOf('brutto miesięcznie') < 0 && (
        <span className="small muted">≈ {formatPln(amount.comparable)} UoP brutto</span>
      )}
      <span className="small muted">{hint}</span>
    </div>
  );
}

function PlanView({ plan }: { plan: NegotiationPlan }) {
  const [copied, setCopied] = useState<string>();

  async function copy(text: string) {
    try {
      await navigator.clipboard.writeText(text);
      setCopied('Skopiowano sformułowanie do schowka.');
    } catch {
      setCopied('Nie udało się skopiować — zaznacz tekst ręcznie.');
    }
  }

  return (
    <>
      <Card title={`Plan: ${plan.offerTitle}`} actions={<Link to={`/oferty/${plan.offerId}`}>Otwórz ofertę</Link>}>
        {plan.notes.map((n) => (
          <Alert key={n} tone="info"><span><span aria-hidden="true">ℹ </span>{n}</span></Alert>
        ))}
        {plan.anchor ? (
          <>
            <div className="grid-4 salary-tiles">
              <AmountTile label="Kotwica — pierwsza kwota" amount={plan.anchor} hint="Podaj ją jako pierwszą, z uzasadnieniem." />
              <AmountTile label="Cel" amount={plan.target} hint="Realny wynik, na który się nastawiasz." />
              <AmountTile label="Kontroferta" amount={plan.counterOffer} hint="Odpowiedź na propozycję poniżej celu." />
              <AmountTile label="Próg wyjścia" amount={plan.walkAway} hint="Poniżej — negocjuj pozapłacowo albo odmów." />
            </div>
            {plan.anchorExplanation && <p className="small">{plan.anchorExplanation}</p>}
            <ul className="list-plain small">
              {plan.anchorComponents.map((c) => (
                <li key={c.label}>
                  {c.label}: <strong>{formatPln(c.value)}</strong>{' '}
                  {c.decisive && <Badge tone="info">decyduje o kotwicy</Badge>}
                </li>
              ))}
            </ul>
          </>
        ) : (
          <EmptyState title="Brak danych do wyliczenia kwot">Zacznij od pytania o budżet przewidziany na stanowisko.</EmptyState>
        )}
        <p className="small muted">
          Rynek ({plan.marketGroup}): {plan.marketCount} ofert, mediana {formatPln(plan.marketMedian)}, 75. percentyl {formatPln(plan.marketP75)}
          {plan.smallSample ? ' — mała próba, dane orientacyjne' : ''}. Twoje minimum z profilu: {formatPln(plan.candidateMinimum)}.
        </p>
      </Card>

      <div className="grid-2">
        <Card title="Gotowe sformułowania">
          <ol className="salary-phrases">
            {plan.phrases.map((p) => (
              <li key={p}>
                <p>{p}</p>
                <button type="button" className="btn btn-small" onClick={() => copy(p)}>
                  Kopiuj<span className="sr-only"> sformułowanie</span>
                </button>
              </li>
            ))}
          </ol>
          <LiveMessage message={copied} />
        </Card>
        <div className="stack">
          <Card title="Twoje argumenty">
            <ul className="small">{plan.arguments.map((a) => <li key={a}>{a}</li>)}</ul>
          </Card>
          <Card title="Dźwignie pozapłacowe">
            <ul className="small">{plan.levers.map((l) => <li key={l}>{l}</li>)}</ul>
          </Card>
          <Card title="Czego unikać">
            <ul className="small">{plan.avoid.map((a) => <li key={a}><span aria-hidden="true">✗ </span>{a}</li>)}</ul>
          </Card>
        </div>
      </div>
    </>
  );
}
