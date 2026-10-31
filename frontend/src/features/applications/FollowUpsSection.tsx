import { useState } from 'react';
import { Link } from 'react-router-dom';
import { errorMessage } from '../../api/client';
import { APPLICATION_STATUS, formatDate } from '../../api/labels';
import type { AsyncState } from '../../hooks/useAsync';
import { Badge, Card, EmptyState, ErrorBox, LiveMessage, Loading } from '../../components/ui';
import { applicationsApi } from './api';
import { daysText, followUpLabel } from './format';
import type { FollowUpItem, FollowUpList } from './types';

/** Sekcja „Follow-upy” nad tablicą (#41): zaległe i nadchodzące przypomnienia z szybkimi akcjami. */
export function FollowUpsSection({ state, onChanged }: { state: AsyncState<FollowUpList>; onChanged: () => void }) {
  const [message, setMessage] = useState<string>();
  const [error, setError] = useState<string>();
  const [busy, setBusy] = useState<number | null>(null);
  const d = state.data;

  async function act(item: FollowUpItem, action: 'sent' | 'snooze') {
    setBusy(item.applicationId);
    setError(undefined);
    try {
      if (action === 'sent') {
        const a = await applicationsApi.followUpSent(item.applicationId);
        setMessage(`Zapisano follow-up do „${item.offerTitle}”. Kolejne przypomnienie: ${formatDate(a.nextFollowUpAt)}.`);
      } else {
        const a = await applicationsApi.snooze(item.applicationId, 3);
        setMessage(`Odłożono przypomnienie „${item.offerTitle}” do ${formatDate(a.nextFollowUpAt)}.`);
      }
      onChanged();
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(null);
    }
  }

  return (
    <Card
      title="Follow-upy"
      actions={
        d && (
          <span className="small muted">
            Przypomnienie {daysText(d.followUpDays)} po wysłaniu · <Link to="/profil">zmień w profilu</Link>
          </span>
        )
      }
    >
      {state.loading && !d && <Loading label="Ładowanie przypomnień…" />}
      {state.error && <ErrorBox message={state.error} onRetry={state.reload} />}
      {error && <ErrorBox message={error} />}
      {d && d.overdue.length === 0 && d.upcoming.length === 0 && (
        <EmptyState title="Brak zaplanowanych follow-upów">
          <p className="small">
            Termin ustawia się sam, gdy oznaczysz aplikację jako wysłaną. Gdy firma odpowie, przypomnienie znika.
          </p>
        </EmptyState>
      )}
      {d && d.overdue.length > 0 && (
        <>
          <h3>Do wysłania teraz ({d.overdue.length})</h3>
          <FollowUpRows items={d.overdue} busy={busy} onAct={act} />
        </>
      )}
      {d && d.upcoming.length > 0 && (
        <>
          <h3>
            Nadchodzące w ciągu {daysText(d.horizonDays)} ({d.upcoming.length})
          </h3>
          <FollowUpRows items={d.upcoming} busy={busy} onAct={act} />
        </>
      )}
      <LiveMessage message={message} />
    </Card>
  );
}

function FollowUpRows({
  items, busy, onAct,
}: {
  items: FollowUpItem[];
  busy: number | null;
  onAct: (item: FollowUpItem, action: 'sent' | 'snooze') => void;
}) {
  return (
    <ul className="applications-followups">
      {items.map((f) => (
        <li key={f.applicationId}>
          <div>
            <Link to={`/aplikacje/${f.applicationId}`}>{f.offerTitle}</Link>
            {f.company && <span className="muted"> — {f.company}</span>}
            <div className="small muted">
              Termin {formatDate(f.nextFollowUpAt)} · {APPLICATION_STATUS[f.status]}
              {f.appliedAt && <> · wysłana {formatDate(f.appliedAt)}</>}
              {f.contactName && <> · kontakt: {f.contactName}</>}
            </div>
          </div>
          <div className="actions">
            <Badge tone={f.overdue ? 'warning' : 'neutral'}>{followUpLabel(f.days)}</Badge>
            <button type="button" className="btn btn-small" disabled={busy === f.applicationId} onClick={() => onAct(f, 'sent')}>
              Follow-up wysłany<span className="sr-only">: {f.offerTitle}</span>
            </button>
            <button type="button" className="btn btn-small" disabled={busy === f.applicationId} onClick={() => onAct(f, 'snooze')}>
              Odłóż o 3 dni<span className="sr-only">: {f.offerTitle}</span>
            </button>
          </div>
        </li>
      ))}
    </ul>
  );
}
