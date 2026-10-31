import { useState } from 'react';
import type { Interview } from '../../api/types';
import { INTERVIEW_KIND, formatDateTime } from '../../api/labels';
import { useAsync } from '../../hooks/useAsync';
import { Badge, Card, EmptyState, ErrorBox, Loading } from '../../components/ui';
import { CALENDAR_ICS_URL, applicationsApi, interviewIcsUrl } from './api';
import { fromLocalInput, isUrl } from './format';
import { InterviewForm } from './InterviewForm';
import { useAction, type OnChanged } from './useAction';
import type { InterviewDraft } from './types';

/** Rozmowy w ramach aplikacji (#45, #52): planowanie, edycja, notatki, eksport .ics; debrief tylko do odczytu. */
export function InterviewsCard({ appId, version, onChanged }: { appId: number; version: number; onChanged: OnChanged }) {
  const list = useAsync(() => applicationsApi.interviews(appId), [appId, version]);
  const [editing, setEditing] = useState<Interview | 'new' | null>(null);
  const { busy, error, run, setError } = useAction();

  async function save(draft: InterviewDraft) {
    const body = {
      scheduledAt: fromLocalInput(draft.scheduledAt),
      durationMinutes: draft.durationMinutes,
      kind: draft.kind,
      location: draft.location.trim() || null,
      interviewers: draft.interviewers.trim() || null,
      notes: draft.notes.trim() || null,
      questionsAsked: draft.questionsAsked.trim() || null,
    };
    const isNew = editing === 'new';
    const saved = await run(() =>
      editing === 'new' || editing == null
        ? applicationsApi.createInterview({ applicationId: appId, ...body })
        : // PUT nadpisuje wszystkie pola — przekazujemy debrief bez zmian (należy do modułu „Rozmowy”)
          applicationsApi.updateInterview(editing.id, { ...editing, ...body }),
    );
    if (!saved) return;
    setEditing(null);
    onChanged(undefined, isNew ? `Zaplanowano rozmowę: ${formatDateTime(saved.scheduledAt)}.` : 'Zapisano zmiany rozmowy.');
  }

  async function remove(i: Interview) {
    if (!window.confirm(`Usunąć rozmowę z ${formatDateTime(i.scheduledAt)}?`)) return;
    const ok = await run(async () => {
      await applicationsApi.deleteInterview(i.id);
      return true;
    });
    if (ok) onChanged(undefined, 'Usunięto rozmowę.');
  }

  function edit(target: Interview | 'new' | null) {
    setError(undefined);
    setEditing(target);
  }

  return (
    <Card
      title="Rozmowy"
      actions={
        <>
          <a className="btn btn-small" href={CALENDAR_ICS_URL} download>
            Wszystkie nadchodzące (.ics)
          </a>
          {editing == null && (
            <button type="button" className="btn btn-small btn-primary" onClick={() => edit('new')}>
              Zaplanuj rozmowę
            </button>
          )}
        </>
      }
    >
      {editing === 'new' && (
        <InterviewForm busy={busy} error={error} onSubmit={save} onCancel={() => edit(null)} />
      )}
      {list.loading && !list.data && <Loading />}
      {list.error && <ErrorBox message={list.error} onRetry={list.reload} />}
      {list.data?.length === 0 && editing !== 'new' && (
        <EmptyState title="Brak rozmów">
          <p className="small">Zaplanuj rozmowę, a potem dodaj ją do kalendarza plikiem .ics.</p>
        </EmptyState>
      )}
      {editing == null && error && <ErrorBox message={error} />}
      {list.data?.map((i) =>
        editing !== 'new' && editing?.id === i.id ? (
          <InterviewForm key={i.id} initial={i} busy={busy} error={error} onSubmit={save} onCancel={() => edit(null)} />
        ) : (
          <InterviewItem key={i.id} interview={i} onEdit={() => edit(i)} onDelete={() => remove(i)} disabled={busy} />
        ),
      )}
    </Card>
  );
}

function InterviewItem({
  interview: i, onEdit, onDelete, disabled,
}: {
  interview: Interview;
  onEdit: () => void;
  onDelete: () => void;
  disabled: boolean;
}) {
  const when = formatDateTime(i.scheduledAt);
  const upcoming = new Date(i.scheduledAt).getTime() > Date.now();
  const questions = (i.questionsAsked ?? '').split('\n').map((q) => q.trim()).filter(Boolean);
  const hasDebrief = i.wentWell || i.toImprove || i.selfRating != null;
  const headingId = `aplikacje-rozmowa-${i.id}`;
  return (
    <article className="applications-interview" aria-labelledby={headingId}>
      <div className="spread">
        <h3 id={headingId}>
          {when} · {i.kind ? INTERVIEW_KIND[i.kind] : 'Rozmowa'}
        </h3>
        {upcoming ? <Badge tone="info">Nadchodząca</Badge> : <Badge>Odbyta</Badge>}
      </div>
      <dl className="kv small" style={{ marginTop: '0.4rem' }}>
        <dt>Czas trwania</dt>
        <dd>{i.durationMinutes ?? 60} min</dd>
        {i.location && (
          <>
            <dt>Miejsce</dt>
            <dd>
              {isUrl(i.location) ? (
                <a href={i.location.trim()} target="_blank" rel="noreferrer noopener">
                  {i.location}
                  <span className="sr-only"> (nowa karta)</span>
                </a>
              ) : (
                i.location
              )}
            </dd>
          </>
        )}
        {i.interviewers && (
          <>
            <dt>Prowadzący</dt>
            <dd>{i.interviewers}</dd>
          </>
        )}
      </dl>
      {i.notes && (
        <>
          <h4 className="small" style={{ margin: '0.6rem 0 0' }}>Notatki</h4>
          <p className="applications-detail-text">{i.notes}</p>
        </>
      )}
      {questions.length > 0 && (
        <>
          <h4 className="small" style={{ margin: '0.6rem 0 0' }}>Zadane pytania</h4>
          <ul className="small" style={{ margin: '0.2rem 0 0' }}>
            {questions.map((q, idx) => (
              <li key={idx}>{q}</li>
            ))}
          </ul>
        </>
      )}
      {hasDebrief && (
        <div className="applications-debrief">
          <strong>Debrief</strong> <span className="muted small">(tylko do odczytu — uzupełniasz go w module „Rozmowy”)</span>
          {i.wentWell && <p className="applications-detail-text">Poszło dobrze: {i.wentWell}</p>}
          {i.toImprove && <p className="applications-detail-text">Do poprawy: {i.toImprove}</p>}
          {i.selfRating != null && <p className="applications-detail-text">Samoocena: {i.selfRating} / 5</p>}
        </div>
      )}
      <div className="actions" style={{ marginTop: '0.6rem' }}>
        <button type="button" className="btn btn-small" onClick={onEdit} disabled={disabled}>
          Edytuj<span className="sr-only"> rozmowę z {when}</span>
        </button>
        <a className="btn btn-small" href={interviewIcsUrl(i.id)} download>
          Dodaj do kalendarza (.ics)<span className="sr-only"> — rozmowa z {when}</span>
        </a>
        <button type="button" className="btn btn-small btn-danger" onClick={onDelete} disabled={disabled}>
          Usuń<span className="sr-only"> rozmowę z {when}</span>
        </button>
      </div>
    </article>
  );
}
