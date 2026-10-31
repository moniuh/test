import { useEffect, useState, type FormEvent } from 'react';
import { errorMessage } from '../../api/client';
import { INTERVIEW_KIND, formatDate, formatDateTime } from '../../api/labels';
import { Badge, Card, EmptyState, ErrorBox, Field, LiveMessage, Loading } from '../../components/ui';
import { interviewApi } from './api';
import type { Insights, InterviewRef } from './types';
import { ratingLabel, splitTopics } from './interviewUtils';

/** #62 Podsumowania rozmów: debrief, średnia samoocena, powtarzające się tematy „do poprawy”. */
export function DebriefTab({ insights, onChanged }: { insights: Insights; onChanged: () => void }) {
  const [openId, setOpenId] = useState<number | null>(insights.pendingDebriefs[0]?.interviewId ?? null);
  const [live, setLive] = useState<string>();
  const { recurring, single } = splitTopics(insights.recurringTopics);

  return (
    <div className="stack">
      <Card title="Wnioski z rozmów">
        <div className="grid-3">
          <div className="stat">
            <span className="stat-value">{insights.debriefedCount}</span>
            <span className="stat-label">podsumowanych rozmów</span>
          </div>
          <div className="stat">
            <span className="stat-value">{insights.averageSelfRating != null ? String(insights.averageSelfRating).replace('.', ',') : '—'}</span>
            <span className="stat-label">średnia samoocena (1–5)</span>
          </div>
          <div className="stat">
            <span className="stat-value">{insights.pendingDebriefs.length}</span>
            <span className="stat-label">bez podsumowania</span>
          </div>
        </div>
        {insights.notes.length > 0 && <ul className="small" style={{ marginTop: '0.75rem' }}>{insights.notes.map((n) => <li key={n}>{n}</li>)}</ul>}
      </Card>

      <div className="grid-2">
        <Card title="Samoocena w kolejnych rozmowach">
          {insights.ratings.length === 0 ? (
            <p className="muted small">Brak ocenionych rozmów.</p>
          ) : (
            <div className="bars">
              {insights.ratings.map((r) => (
                <div className="bar-row" key={r.interviewId}>
                  <span className="small">{formatDate(r.date)} · {r.offerTitle ?? 'rozmowa'}</span>
                  <div className="bar-track" aria-hidden="true">
                    <div className={`bar-fill ${r.rating >= 4 ? 'success' : r.rating <= 2 ? 'danger' : 'warning'}`} style={{ width: `${r.rating * 20}%` }} />
                  </div>
                  <span className="small">{ratingLabel(r.rating)}</span>
                </div>
              ))}
            </div>
          )}
        </Card>
        <Card title="Tematy „do poprawy”">
          {insights.recurringTopics.length === 0 ? (
            <p className="muted small">Wpisuj w podsumowaniach, co poprawić — wychwycimy powtarzające się tematy.</p>
          ) : (
            <>
              {recurring.length > 0 && (
                <>
                  <p className="small" style={{ marginBottom: '0.3rem' }}><strong>Powtarzają się:</strong></p>
                  <ul className="small">
                    {recurring.map((t) => (
                      <li key={t.topic}>
                        <strong>{t.topic}</strong> — w {t.count} rozmowach {t.kind === 'SKILL' && <Badge tone="info">umiejętność</Badge>}
                      </li>
                    ))}
                  </ul>
                </>
              )}
              {single.length > 0 && (
                <p className="small muted">Pojedynczo: {single.map((t) => t.topic).join(', ')}</p>
              )}
            </>
          )}
        </Card>
      </div>

      <Card title="Odbyte rozmowy">
        {insights.past.length === 0 && <EmptyState title="Brak odbytych rozmów">Rozmowy dodasz w szczegółach aplikacji.</EmptyState>}
        <ul className="list-plain">
          {insights.past.map((i) => (
            <li key={i.interviewId}>
              <div className="spread">
                <div>
                  <strong>{i.offerTitle ?? 'Rozmowa'}</strong>
                  <span className="muted small"> · {i.company ?? '—'} · {formatDateTime(i.scheduledAt)}{i.kind ? ` · ${INTERVIEW_KIND[i.kind]}` : ''}</span>
                  <div>
                    {i.debriefedAt ? (
                      <Badge tone="success">✓ podsumowana · {ratingLabel(i.selfRating)}</Badge>
                    ) : (
                      <Badge tone="warning">⚠ bez podsumowania</Badge>
                    )}
                  </div>
                </div>
                <button type="button" className="btn btn-small" aria-expanded={openId === i.interviewId}
                  onClick={() => setOpenId(openId === i.interviewId ? null : i.interviewId)}>
                  {i.debriefedAt ? 'Edytuj podsumowanie' : 'Podsumuj'}
                  <span className="sr-only"> rozmowy: {i.offerTitle}</span>
                </button>
              </div>
              {openId === i.interviewId && (
                <DebriefForm
                  interview={i}
                  onSaved={() => {
                    setLive('Zapisano podsumowanie rozmowy.');
                    setOpenId(null);
                    onChanged();
                  }}
                />
              )}
            </li>
          ))}
        </ul>
        <LiveMessage message={live} />
      </Card>
    </div>
  );
}

function DebriefForm({ interview, onSaved }: { interview: InterviewRef; onSaved: () => void }) {
  const [loading, setLoading] = useState(true);
  const [wentWell, setWentWell] = useState('');
  const [toImprove, setToImprove] = useState('');
  const [rating, setRating] = useState<number>(interview.selfRating ?? 0);
  const [questions, setQuestions] = useState('');
  const [error, setError] = useState<string>();
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    let active = true;
    interviewApi.interview(interview.interviewId)
      .then((i) => {
        if (!active) return;
        setWentWell(i.wentWell ?? '');
        setToImprove(i.toImprove ?? '');
        setRating(i.selfRating ?? 0);
        setQuestions(i.questionsAsked ?? '');
      })
      .catch((e) => active && setError(errorMessage(e)))
      .finally(() => active && setLoading(false));
    return () => {
      active = false;
    };
  }, [interview.interviewId]);

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (rating < 1 || rating > 5) {
      setError('Zaznacz samoocenę od 1 do 5.');
      return;
    }
    setBusy(true);
    setError(undefined);
    try {
      await interviewApi.debrief(interview.interviewId, { wentWell, toImprove, selfRating: rating, questionsAsked: questions });
      onSaved();
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  }

  if (loading) return <Loading />;
  const name = `iv-rating-${interview.interviewId}`;
  return (
    <form onSubmit={submit} className="card compact" style={{ marginTop: '0.6rem' }} noValidate>
      <Field label="Co poszło dobrze">
        {(id) => <textarea id={id} rows={3} value={wentWell} onChange={(e) => setWentWell(e.target.value)} />}
      </Field>
      <Field label="Co poprawić" hint="Np. „AWS — nadrobić IAM”, „za długo o oczekiwaniach finansowych”">
        {(id, describedBy) => <textarea id={id} aria-describedby={describedBy} rows={3} value={toImprove} onChange={(e) => setToImprove(e.target.value)} />}
      </Field>
      <fieldset className="iv-rating">
        <legend>Samoocena (1 — słabo, 5 — świetnie)</legend>
        {[1, 2, 3, 4, 5].map((n) => (
          <label key={n}>
            <input type="radio" name={name} value={n} checked={rating === n} onChange={() => setRating(n)} /> {n}
          </label>
        ))}
      </fieldset>
      <Field label="Pytania, które padły" hint="Każde pytanie w osobnej linii — trafią do banku pytań">
        {(id, describedBy) => <textarea id={id} aria-describedby={describedBy} rows={4} value={questions} onChange={(e) => setQuestions(e.target.value)} />}
      </Field>
      {error && <ErrorBox message={error} />}
      <div className="form-actions">
        <button type="submit" className="btn btn-primary" disabled={busy}>Zapisz podsumowanie</button>
      </div>
    </form>
  );
}
