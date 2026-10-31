import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { api, errorMessage } from '../api/client';
import type { CvVariant, JobOffer, JobOfferDraft, MatchResult, NormalizedSalary, OfferStatus } from '../api/types';
import { CONTRACT, OFFER_STATUS, REJECT_REASON, SENIORITY, WORK_MODE, formatDate, formatMoney, formatSalary } from '../api/labels';
import { useAsync } from '../hooks/useAsync';
import { useDocumentTitle } from '../hooks/useDocumentTitle';
import { Badge, Card, ErrorBox, Field, Loading } from '../components/ui';
import { MatchBreakdown } from '../components/MatchBreakdown';
import { OfferForm } from '../components/OfferForm';
import { OfferAnalysisPanel } from '../features/offers/OfferAnalysisPanel';
import { RejectOfferButton } from '../features/offers/RejectOfferButton';
import { SimilarOffersPanel } from '../features/matching/SimilarOffersPanel';
import { TailoringPanel } from '../features/profile/TailoringPanel';
import { InterviewQuestionsPanel } from '../features/interviews/InterviewQuestionsPanel';
import { OfferSalaryPanel } from '../features/salary/OfferSalaryPanel';
import { ApplyPanel } from '../features/applications/ApplyPanel';

const BASIS: Record<NormalizedSalary['basis'], string> = {
  UOP_GROSS: 'brutto', B2B_NET: 'netto + VAT (B2B)', NET: 'netto', UNKNOWN: 'nie podano brutto/netto',
};

export function OfferDetailPage() {
  const { id } = useParams();
  const offerId = Number(id);
  const navigate = useNavigate();
  const offer = useAsync(() => api.get<JobOffer>(`/api/offers/${offerId}`), [offerId]);
  const cvs = useAsync(() => api.get<CvVariant[]>('/api/cv'), []);
  const [cvId, setCvId] = useState<number | null>(null);
  const match = useAsync(
    () => api.get<MatchResult>(`/api/offers/${offerId}/match${cvId ? `?cvId=${cvId}` : ''}`),
    [offerId, cvId, offer.data?.updatedAt],
  );
  const salary = useAsync(() => api.get<NormalizedSalary>(`/api/offers/${offerId}/salary`), [offerId, offer.data?.updatedAt]);
  const [editing, setEditing] = useState(false);
  const [actionError, setActionError] = useState<string>();
  useDocumentTitle(offer.data?.title ?? 'Oferta');

  if (offer.loading && !offer.data) return <Loading />;
  if (offer.error) return <ErrorBox message={offer.error} onRetry={offer.reload} />;
  const o = offer.data!;
  const activeCvId = cvId ?? match.data?.cvId ?? null;

  async function setStatus(status: OfferStatus) {
    setActionError(undefined);
    try {
      offer.setData(await api.post<JobOffer>(`/api/offers/${offerId}/status`, { status }));
    } catch (e) {
      setActionError(errorMessage(e));
    }
  }

  async function remove() {
    if (!window.confirm('Usunąć ofertę? Tej operacji nie można cofnąć.')) return;
    try {
      await api.del(`/api/offers/${offerId}`);
      navigate('/oferty');
    } catch (e) {
      setActionError(errorMessage(e));
    }
  }

  async function save(draft: JobOfferDraft) {
    offer.setData(await api.put<JobOffer>(`/api/offers/${offerId}`, { ...o, ...draft }));
    setEditing(false);
  }

  if (editing) {
    return (
      <>
        <div className="page-header">
          <h1>Edycja oferty</h1>
        </div>
        <Card>
          <OfferForm initial={o} onSubmit={save} onCancel={() => setEditing(false)} submitLabel="Zapisz zmiany" />
        </Card>
      </>
    );
  }

  return (
    <>
      <p className="small no-print">
        <Link to="/oferty">← Wszystkie oferty</Link>
      </p>
      <div className="page-header">
        <div>
          <h1>{o.title}</h1>
          <p className="muted" style={{ marginBottom: '0.4rem' }}>
            {[o.company, o.location, o.source].filter(Boolean).join(' · ')}
          </p>
          <div className="badges">
            <Badge tone={o.status === 'REJECTED' ? 'danger' : o.status === 'SAVED' ? 'success' : 'neutral'}>
              {OFFER_STATUS[o.status]}
            </Badge>
            {o.workMode && <Badge tone="info">{WORK_MODE[o.workMode]}</Badge>}
            {o.contractTypes.map((c) => (
              <Badge key={c}>{CONTRACT[c]}</Badge>
            ))}
            {o.seniority && <Badge>{SENIORITY[o.seniority]}</Badge>}
            <Badge tone={o.salaryMin == null && o.salaryMax == null ? 'warning' : 'neutral'}>{formatSalary(o)}</Badge>
            {o.tags.map((t) => (
              <Badge key={t}>#{t}</Badge>
            ))}
          </div>
          {o.status === 'REJECTED' && o.rejectReason && (
            <p className="small muted" style={{ marginTop: '0.4rem' }}>
              Odrzucona: {REJECT_REASON[o.rejectReason]}
              {o.rejectNote ? ` — ${o.rejectNote}` : ''}
            </p>
          )}
        </div>
        <div className="actions no-print">
          {o.status !== 'SAVED' && (
            <button type="button" className="btn" onClick={() => setStatus('SAVED')}>
              Zapisz
            </button>
          )}
          {o.status !== 'NEW' && (
            <button type="button" className="btn" onClick={() => setStatus('NEW')}>
              Przywróć jako nową
            </button>
          )}
          <RejectOfferButton offer={o} onChanged={(updated) => offer.setData(updated)} />
          {o.status !== 'ARCHIVED' && (
            <button type="button" className="btn" onClick={() => setStatus('ARCHIVED')}>
              Archiwizuj
            </button>
          )}
          <button type="button" className="btn" onClick={() => setEditing(true)}>
            Edytuj
          </button>
          <button type="button" className="btn btn-danger" onClick={remove}>
            Usuń
          </button>
        </div>
      </div>
      {actionError && <ErrorBox message={actionError} />}

      <div className="layout-sidebar">
        <div className="stack">
          <Card
            title="Dopasowanie do CV"
            actions={
              cvs.data && cvs.data.length > 1 ? (
                <Field label="Wersja CV">
                  {(fid) => (
                    <select id={fid} value={activeCvId ?? ''} onChange={(e) => setCvId(Number(e.target.value))}>
                      {cvs.data!.map((c) => (
                        <option key={c.id} value={c.id}>
                          {c.name}
                        </option>
                      ))}
                    </select>
                  )}
                </Field>
              ) : undefined
            }
          >
            {match.loading && !match.data && <Loading />}
            {match.error && <ErrorBox message={match.error} onRetry={match.reload} />}
            {match.data && <MatchBreakdown match={match.data} />}
          </Card>
          <TailoringPanel offerId={offerId} cvId={activeCvId} />
          <InterviewQuestionsPanel offerId={offerId} cvId={activeCvId} />
          <Card title="Treść ogłoszenia">
            {o.url && (
              <p className="small">
                <a href={o.url} target="_blank" rel="noreferrer noopener">
                  Otwórz oryginalne ogłoszenie<span className="sr-only"> (nowa karta)</span>
                </a>
              </p>
            )}
            {o.description ? (
              // przewijany obszar musi być osiągalny klawiaturą (WCAG 2.1.1)
              <div className="offer-desc" tabIndex={0} role="region" aria-label="Treść ogłoszenia">
                {o.description}
              </div>
            ) : (
              <p className="muted">Brak treści.</p>
            )}
          </Card>
        </div>
        <div className="stack">
          <ApplyPanel offer={o} cvId={activeCvId} />
          <OfferAnalysisPanel offer={o} />
          <Card title="Wynagrodzenie">
            <p style={{ margin: 0 }}>{formatSalary(o)}</p>
            {salary.data?.present && (
              <dl className="kv small" style={{ marginTop: '0.5rem' }}>
                <dt>Miesięcznie</dt>
                <dd>
                  {formatMoney(salary.data.monthlyMin)} – {formatMoney(salary.data.monthlyMax)} ({BASIS[salary.data.basis]})
                </dd>
                <dt>≈ UoP brutto</dt>
                <dd>
                  {formatMoney(salary.data.comparableMin)} – {formatMoney(salary.data.comparableMax)}
                </dd>
                <dt>Jak liczone</dt>
                <dd>{salary.data.explanation}</dd>
              </dl>
            )}
          </Card>
          <OfferSalaryPanel offer={o} />
          <SimilarOffersPanel offerId={offerId} />
          <Card title="Szczegóły">
            <dl className="kv small">
              <dt>Dodano</dt>
              <dd>{formatDate(o.createdAt)}</dd>
              <dt>Opublikowano</dt>
              <dd>{formatDate(o.publishedAt)}</dd>
              <dt>Ważne do</dt>
              <dd>{formatDate(o.expiresAt)}</dd>
              {o.benefits.length > 0 && (
                <>
                  <dt>Benefity</dt>
                  <dd>{o.benefits.join(', ')}</dd>
                </>
              )}
              {o.notes && (
                <>
                  <dt>Notatki</dt>
                  <dd>{o.notes}</dd>
                </>
              )}
            </dl>
          </Card>
        </div>
      </div>
    </>
  );
}
