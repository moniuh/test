import { useEffect, useState, type FormEvent } from 'react';
import type { ApplicationStatus, ApplicationView } from '../../api/types';
import { APPLICATION_STATUS, APPLICATION_STATUSES, formatDate } from '../../api/labels';
import { Card, ErrorBox, Field } from '../../components/ui';
import { applicationsApi } from './api';
import { useAction, type OnChanged } from './useAction';

/** Zmiana statusu (z notatką na osi czasu) i najważniejsze daty aplikacji. */
export function StatusCard({ app, onChanged }: { app: ApplicationView; onChanged: OnChanged }) {
  const [status, setStatus] = useState<ApplicationStatus>(app.status);
  const [note, setNote] = useState('');
  const { busy, error, run } = useAction();

  useEffect(() => setStatus(app.status), [app.status]);

  async function save(e: FormEvent) {
    e.preventDefault();
    if (status === app.status) return;
    const updated = await run(() => applicationsApi.changeStatus(app.id, status, note.trim()));
    if (!updated) return;
    setNote('');
    onChanged(updated, `Zmieniono status na „${APPLICATION_STATUS[updated.status]}”.`);
  }

  return (
    <Card title="Status">
      <form onSubmit={save}>
        <Field label="Etap rekrutacji">
          {(id) => (
            <select id={id} value={status} onChange={(e) => setStatus(e.target.value as ApplicationStatus)}>
              {APPLICATION_STATUSES.map((s) => (
                <option key={s} value={s}>
                  {APPLICATION_STATUS[s]}
                </option>
              ))}
            </select>
          )}
        </Field>
        <Field label="Notatka do zmiany (opcjonalnie)" hint="Trafi na oś czasu, np. „telefon od rekruterki”.">
          {(id, describedBy) => (
            <input id={id} aria-describedby={describedBy} type="text" value={note} onChange={(e) => setNote(e.target.value)} />
          )}
        </Field>
        {error && <ErrorBox message={error} />}
        <button type="submit" className="btn btn-primary btn-small" disabled={busy || status === app.status}>
          Zmień status
        </button>
      </form>
      <dl className="kv small" style={{ marginTop: '0.9rem' }}>
        <dt>Wysłana</dt>
        <dd>{formatDate(app.appliedAt)}</dd>
        <dt>Kanał</dt>
        <dd>{app.source ?? '—'}</dd>
        <dt>Wersja CV</dt>
        <dd>{app.cvVariantName ?? '—'}</dd>
        <dt>Pierwsza odpowiedź</dt>
        <dd>{formatDate(app.firstResponseAt)}</dd>
        <dt>Ostatni kontakt</dt>
        <dd>{formatDate(app.lastContactAt)}</dd>
      </dl>
    </Card>
  );
}
