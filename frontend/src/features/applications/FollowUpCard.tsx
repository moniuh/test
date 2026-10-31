import { useEffect, useState, type FormEvent } from 'react';
import type { ApplicationView } from '../../api/types';
import { formatDate, todayIso } from '../../api/labels';
import { Badge, Card, ErrorBox, Field } from '../../components/ui';
import { applicationsApi } from './api';
import { isClosed } from './board';
import { daysUntil, followUpLabel } from './format';
import { useAction, type OnChanged } from './useAction';

/** Follow-up w szczegółach aplikacji (#41): termin, „wysłany”, odłożenie, ręczna zmiana terminu. */
export function FollowUpCard({ app, onChanged }: { app: ApplicationView; onChanged: OnChanged }) {
  const [note, setNote] = useState('');
  const [date, setDate] = useState(app.nextFollowUpAt ?? '');
  const { busy, error, run } = useAction();
  const closed = isClosed(app.status);
  const days = app.nextFollowUpAt ? daysUntil(app.nextFollowUpAt, todayIso()) : null;

  useEffect(() => setDate(app.nextFollowUpAt ?? ''), [app.nextFollowUpAt]);

  async function sent() {
    const a = await run(() => applicationsApi.followUpSent(app.id, note.trim()));
    if (!a) return;
    setNote('');
    onChanged(a, a.nextFollowUpAt ? `Zapisano follow-up. Kolejne przypomnienie: ${formatDate(a.nextFollowUpAt)}.` : 'Zapisano follow-up.');
  }

  async function snooze(n: number) {
    const a = await run(() => applicationsApi.snooze(app.id, n));
    if (a) onChanged(a, `Odłożono przypomnienie do ${formatDate(a.nextFollowUpAt)}.`);
  }

  async function setManual(e: FormEvent) {
    e.preventDefault();
    const a = await run(() => applicationsApi.update(app.id, { nextFollowUpAt: date || null }));
    if (a) onChanged(a, a.nextFollowUpAt ? `Ustawiono termin follow-upu: ${formatDate(a.nextFollowUpAt)}.` : 'Usunięto przypomnienie.');
  }

  async function clear() {
    const a = await run(() => applicationsApi.update(app.id, { nextFollowUpAt: null }));
    if (a) onChanged(a, 'Usunięto przypomnienie.');
  }

  return (
    <Card title="Follow-up">
      {closed ? (
        <p className="muted small">Aplikacja jest zamknięta — przypomnienia są wyłączone.</p>
      ) : (
        <>
          {app.nextFollowUpAt && days != null ? (
            <p>
              Następny follow-up: <strong>{formatDate(app.nextFollowUpAt)}</strong>{' '}
              <Badge tone={days <= 0 ? 'warning' : 'neutral'}>{followUpLabel(days)}</Badge>
            </p>
          ) : (
            <p className="muted small">
              Brak zaplanowanego przypomnienia.{' '}
              {app.status === 'PLANNED'
                ? 'Termin ustawi się sam po oznaczeniu aplikacji jako wysłanej.'
                : 'Firma odpowiedziała — termin możesz ustawić ręcznie.'}
            </p>
          )}
          <Field label="Notatka do follow-upu (opcjonalnie)" hint="Np. „mail do rekruterki z pytaniem o termin decyzji”.">
            {(id, describedBy) => (
              <input id={id} aria-describedby={describedBy} type="text" value={note} onChange={(e) => setNote(e.target.value)} />
            )}
          </Field>
          <div className="actions">
            <button type="button" className="btn btn-small btn-primary" onClick={sent} disabled={busy}>
              Follow-up wysłany
            </button>
            {app.nextFollowUpAt && (
              <>
                <button type="button" className="btn btn-small" onClick={() => snooze(3)} disabled={busy}>
                  Odłóż o 3 dni
                </button>
                <button type="button" className="btn btn-small" onClick={() => snooze(7)} disabled={busy}>
                  Odłóż o 7 dni
                </button>
              </>
            )}
          </div>
          <form onSubmit={setManual} className="row" style={{ alignItems: 'flex-end', marginTop: '0.75rem' }}>
            <Field label="Termin ręcznie">
              {(id) => <input id={id} type="date" value={date} onChange={(e) => setDate(e.target.value)} />}
            </Field>
            <div className="field">
              <button type="submit" className="btn btn-small" disabled={busy || date === (app.nextFollowUpAt ?? '')}>
                Ustaw termin
              </button>
            </div>
            {app.nextFollowUpAt && (
              <div className="field">
                <button type="button" className="btn btn-small btn-link" onClick={clear} disabled={busy}>
                  Usuń przypomnienie
                </button>
              </div>
            )}
          </form>
        </>
      )}
      {error && <ErrorBox message={error} />}
      <p className="small muted" style={{ margin: 0 }}>
        Ostatni kontakt: {formatDate(app.lastContactAt)}
      </p>
    </Card>
  );
}
