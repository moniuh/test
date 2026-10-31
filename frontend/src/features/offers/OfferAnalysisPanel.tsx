import { useState } from 'react';
import { Link } from 'react-router-dom';
import { errorMessage } from '../../api/client';
import type { JobOffer } from '../../api/types';
import { OFFER_STATUS } from '../../api/labels';
import { useAsync } from '../../hooks/useAsync';
import { Badge, Card, ErrorBox, LiveMessage, Loading, SourceBadge } from '../../components/ui';
import { offersApi } from './api';
import { RISK, SEVERITY, formatPercent, redFlagSummary, sortRedFlags } from './logic';
import type { DuplicateCandidate, OfferAnalysis } from './types';
import './offers.css';

/**
 * Analiza ogłoszenia: ryzyko oszustwa (#19), czerwone flagi (#18) i duplikaty (#16). Wszystko liczą
 * jawne reguły offline; każdy sygnał ma dosłowny cytat z ogłoszenia.
 */
export function OfferAnalysisPanel({
  offer, onChanged: onOfferChanged,
}: {
  offer: JobOffer;
  /** Wywoływane po zmianie oferty w panelu (np. oznaczenie duplikatu) — strona odświeża dane bez przeładowania. */
  onChanged?: (offer: JobOffer) => void;
}) {
  const analysis = useAsync(() => offersApi.analysis(offer.id), [offer.id, offer.updatedAt]);
  // po przejściu do innej oferty (ta sama strona) nie pokazuj analizy poprzedniej
  const data = analysis.data?.offerId === offer.id ? analysis.data : undefined;

  return (
    <Card title="Analiza ogłoszenia" actions={<SourceBadge source={data?.source ?? 'offline'} />}>
      {analysis.loading && !data && <Loading label="Analizuję ogłoszenie…" />}
      {analysis.error && <ErrorBox message={analysis.error} onRetry={analysis.reload} />}
      {data && (
        <>
          <ScamSection analysis={data} />
          <RedFlagsSection analysis={data} />
          <DuplicatesSection
            key={offer.id}
            offer={offer}
            analysis={data}
            onChanged={analysis.reload}
            onOfferChanged={onOfferChanged}
          />
        </>
      )}
    </Card>
  );
}

function Quote({ text, tone }: { text: string; tone: 'high' | 'warning' | 'info' | 'neutral' }) {
  return (
    <blockquote className={`offers-quote ${tone}`}>
      <span className="sr-only">Cytat z ogłoszenia: </span>„{text}”
    </blockquote>
  );
}

function ScamSection({ analysis }: { analysis: OfferAnalysis }) {
  const { scam } = analysis;
  const risk = RISK[scam.level];
  return (
    <section className="offers-section" aria-labelledby="offers-scam-h">
      <h3 id="offers-scam-h">Ryzyko oszustwa</h3>
      <div className={`offers-risk offers-risk-${scam.level}`}>
        <span className="offers-risk-icon" aria-hidden="true">
          {risk.icon}
        </span>
        <p className="offers-risk-text">
          <strong>Ryzyko: {scam.levelLabel}</strong>
          <br />
          <span className="small">
            {scam.score} / 100 pkt (średnie od {scam.mediumThreshold}, wysokie od {scam.highThreshold})
          </span>
        </p>
      </div>
      {scam.signals.length > 0 && (
        <ul className="list-plain">
          {scam.signals.map((s) => (
            <li key={s.code}>
              <div className="offers-flag-head">
                <strong>{s.title}</strong>
                <span className="small muted offers-points">+{s.points} pkt</span>
              </div>
              <p className="small offers-flag-why">{s.why}</p>
              {s.quote && <Quote text={s.quote} tone={scam.level === 'high' ? 'high' : 'warning'} />}
            </li>
          ))}
        </ul>
      )}
      {scam.recommendations.length > 0 && (
        <>
          <p className="small" style={{ margin: '0.6rem 0 0.2rem', fontWeight: 600 }}>
            Co zrobić
          </p>
          <ul className="small offers-hint-list">
            {scam.recommendations.map((r) => (
              <li key={r}>{r}</li>
            ))}
          </ul>
        </>
      )}
      <p className="small muted" style={{ margin: '0.4rem 0 0' }}>
        {scam.method}
      </p>
    </section>
  );
}

function RedFlagsSection({ analysis }: { analysis: OfferAnalysis }) {
  const flags = sortRedFlags(analysis.redFlags);
  return (
    <section className="offers-section" aria-labelledby="offers-flags-h">
      <h3 id="offers-flags-h">Czerwone flagi</h3>
      <p className="small" style={{ marginBottom: '0.4rem' }}>
        {redFlagSummary(flags)}
        {flags.length === 0 && ' w treści ogłoszenia.'}
      </p>
      {flags.length > 0 && (
        <ul className="list-plain">
          {flags.map((f) => {
            const sev = SEVERITY[f.severity];
            return (
              <li key={f.code}>
                <div className="offers-flag-head">
                  <Badge tone={sev.tone}>
                    <span aria-hidden="true">{sev.icon}</span> {sev.label}
                  </Badge>
                  <strong>{f.title}</strong>
                </div>
                <p className="small offers-flag-why">{f.why}</p>
                {f.quote && <Quote text={f.quote} tone={f.severity} />}
              </li>
            );
          })}
        </ul>
      )}
    </section>
  );
}

function DuplicatesSection({
  offer, analysis, onChanged, onOfferChanged,
}: {
  offer: JobOffer;
  analysis: OfferAnalysis;
  onChanged: () => void;
  onOfferChanged?: (offer: JobOffer) => void;
}) {
  const report = analysis.duplicates;
  const [busy, setBusy] = useState<number | null>(null);
  const [error, setError] = useState<string>();
  const [message, setMessage] = useState<string>();
  // stan oznaczenia po akcji w tym panelu (rodzic nie dostaje zaktualizowanej oferty — sygnatura gniazda)
  const [override, setOverride] = useState<{ duplicateOfId: number | null }>();
  const candidates = report.candidates;

  async function markAs(c: DuplicateCandidate) {
    setBusy(c.offerId);
    setError(undefined);
    try {
      const updated = await offersApi.markDuplicate(offer.id, c.offerId);
      setOverride({ duplicateOfId: updated.duplicateOfId ?? c.offerId });
      setMessage(`Oznaczono jako duplikat oferty „${c.title}” i przeniesiono do archiwum.`);
      onChanged();
      onOfferChanged?.(updated);
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(null);
    }
  }

  async function unmark() {
    setBusy(-1);
    setError(undefined);
    try {
      const updated = await offersApi.unmarkDuplicate(offer.id);
      setOverride({ duplicateOfId: null });
      setMessage('Cofnięto oznaczenie duplikatu — oferta wróciła jako nowa.');
      onChanged();
      onOfferChanged?.(updated);
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(null);
    }
  }

  const duplicateOf = override ? override.duplicateOfId : offer.duplicateOfId;
  // bez onOfferChanged strona nie dostanie nowego stanu — wtedy proponujemy odświeżenie
  const statusChanged = override != null && !onOfferChanged;
  const num = (n: number) => n.toLocaleString('pl-PL');

  return (
    <section className="offers-section" aria-labelledby="offers-dup-h">
      <h3 id="offers-dup-h">Duplikaty</h3>
      {error && <ErrorBox message={error} />}
      {duplicateOf != null && (
        <p className="small">
          Ta oferta jest oznaczona jako duplikat <Link to={`/oferty/${duplicateOf}`}>oferty #{duplicateOf}</Link>.{' '}
          <button type="button" className="btn btn-link" onClick={unmark} disabled={busy != null}>
            Cofnij oznaczenie
          </button>
        </p>
      )}
      {candidates.length === 0 ? (
        <p className="small muted">Nie znaleziono podobnych ofert.</p>
      ) : (
        <ul className="list-plain">
          {candidates.map((c) => (
            <li key={c.offerId}>
              <div className="spread">
                <span>
                  <Link to={`/oferty/${c.offerId}`}>{c.title}</Link>
                  <span className="small muted">
                    {' '}
                    {[c.company, c.source, OFFER_STATUS[c.status]].filter(Boolean).join(' · ')}
                  </span>
                </span>
                <Badge tone={c.duplicate ? 'warning' : 'neutral'}>
                  {c.duplicate ? 'prawdopodobny duplikat' : 'podobna'} · {formatPercent(c.score)}
                </Badge>
              </div>
              <p className="small muted" style={{ margin: '0.2rem 0 0' }}>
                Firma {formatPercent(c.companyScore)} · tytuł {formatPercent(c.titleScore)} · treść{' '}
                {formatPercent(c.contentScore)}
                {c.reasons.length > 0 && ` — ${c.reasons.join(', ')}`}
              </p>
              {c.alreadyMarked ? (
                <p className="small" style={{ margin: '0.2rem 0 0' }}>
                  Już powiązana jako duplikat.
                </p>
              ) : (
                duplicateOf == null && (
                  <button
                    type="button"
                    className="btn btn-small"
                    style={{ marginTop: '0.35rem' }}
                    disabled={busy != null}
                    onClick={() => markAs(c)}
                    aria-label={`Oznacz tę ofertę jako duplikat oferty „${c.title}”${c.older ? ' (dodanej wcześniej)' : ''}`}
                  >
                    {busy === c.offerId ? 'Zapisywanie…' : 'Oznacz tę ofertę jako duplikat'}
                  </button>
                )
              )}
            </li>
          ))}
        </ul>
      )}
      <p className="small muted" style={{ margin: '0.4rem 0 0' }}>
        Wynik 0–100% = firma × {num(report.weights.company)} + tytuł × {num(report.weights.title)} + treść ×{' '}
        {num(report.weights.content)}; duplikat od {formatPercent(report.threshold)}.
      </p>
      <LiveMessage message={message} />
      {statusChanged && (
        <p className="small">
          Status oferty się zmienił.{' '}
          <button type="button" className="btn btn-link" onClick={() => window.location.reload()}>
            Odśwież widok oferty
          </button>
        </p>
      )}
    </section>
  );
}
