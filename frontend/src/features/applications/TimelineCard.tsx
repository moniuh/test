import { useState, type FormEvent } from 'react';
import { formatDateTime } from '../../api/labels';
import { useAsync } from '../../hooks/useAsync';
import { Badge, Card, EmptyState, ErrorBox, Field, Loading } from '../../components/ui';
import { applicationsApi } from './api';
import { useAction, type OnChanged } from './useAction';
import type { TimelineKind } from './types';

const KIND_LABEL: Record<TimelineKind, string> = { EVENT: 'Zdarzenie', NOTE: 'Notatka', INTERVIEW: 'Rozmowa' };

/** Oś czasu aplikacji (#40): zdarzenia, rozmowy i notatki, od najnowszych; formularz nowej notatki. */
export function TimelineCard({ appId, version, onChanged }: { appId: number; version: number; onChanged: OnChanged }) {
  const timeline = useAsync(() => applicationsApi.timeline(appId), [appId, version]);
  const [note, setNote] = useState('');
  const { busy, error, run } = useAction();
  const items = timeline.data ? [...timeline.data].reverse() : [];

  async function add(e: FormEvent) {
    e.preventDefault();
    if (!note.trim()) return;
    const saved = await run(() => applicationsApi.addNote(appId, note.trim()));
    if (!saved) return;
    setNote('');
    onChanged(undefined, 'Dodano notatkę do osi czasu.');
  }

  return (
    <Card title="Oś czasu">
      <form onSubmit={add}>
        <Field label="Nowa notatka">
          {(id) => <textarea id={id} rows={3} value={note} onChange={(e) => setNote(e.target.value)} style={{ minHeight: 70 }} />}
        </Field>
        {error && <ErrorBox message={error} />}
        <button type="submit" className="btn btn-small" disabled={busy || !note.trim()}>
          Dodaj notatkę
        </button>
      </form>
      <div style={{ marginTop: '1rem' }}>
        {timeline.loading && !timeline.data && <Loading />}
        {timeline.error && <ErrorBox message={timeline.error} onRetry={timeline.reload} />}
        {timeline.data?.length === 0 && <EmptyState title="Brak wpisów na osi czasu" />}
        {items.length > 0 && (
          <ol className="timeline" aria-label="Historia aplikacji, od najnowszych">
            {items.map((i) => (
              <li
                key={i.key}
                className={i.kind === 'NOTE' ? 'applications-note' : i.kind === 'INTERVIEW' && i.upcoming ? 'applications-upcoming' : undefined}
              >
                <span className="applications-timeline-kind">{KIND_LABEL[i.kind]}</span>
                <strong>{i.title}</strong>{' '}
                {i.kind === 'INTERVIEW' && i.upcoming && <Badge tone="info">Nadchodząca</Badge>}
                <div className="small muted">
                  <time dateTime={i.at}>{formatDateTime(i.at)}</time>
                </div>
                {i.detail && <p className="applications-detail-text">{i.detail}</p>}
              </li>
            ))}
          </ol>
        )}
      </div>
    </Card>
  );
}
