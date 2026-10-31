import { useState } from 'react';
import { Link } from 'react-router-dom';
import { Badge, Card, EmptyState, ErrorBox, Loading } from '../../components/ui';
import { useAsync } from '../../hooks/useAsync';
import { analyticsApi } from './api';
import { LEVEL_LABEL, levelTone } from './logic';

/** Ile działań pokazujemy, zanim użytkownik rozwinie listę. */
const VISIBLE = 6;

/** „Co dalej?” (#97) — działania na dziś wyliczone regułami z danych. */
export function NextActionsCard({ refreshKey = 0 }: { refreshKey?: number }) {
  const actions = useAsync(() => analyticsApi.nextActions(), [refreshKey]);
  const [all, setAll] = useState(false);

  return (
    <Card title="Co dalej?">
      {actions.loading && !actions.data && <Loading label="Układam listę działań…" />}
      {actions.error && <ErrorBox message={actions.error} onRetry={actions.reload} />}
      {actions.data && actions.data.length === 0 && (
        <EmptyState title="Na dziś wszystko zrobione">
          <p>Nie ma zaległych follow-upów ani pilnych spraw. Możesz przejrzeć <Link to="/oferty">nowe oferty</Link> albo odpocząć.</p>
        </EmptyState>
      )}
      {actions.data && actions.data.length > 0 && (
        <>
          <ol className="analytics-actions" aria-label="Działania od najważniejszego">
            {(all ? actions.data : actions.data.slice(0, VISIBLE)).map((a, i) => (
              <li key={`${a.kind}-${a.link}-${i}`}>
                <Badge tone={levelTone(a.level)}>{LEVEL_LABEL[a.level]}</Badge>
                <div>
                  <Link to={a.link}>{a.title}</Link>
                  <p className="small muted">{a.reason}</p>
                </div>
              </li>
            ))}
          </ol>
          {actions.data.length > VISIBLE && (
            <button type="button" className="btn btn-link" aria-expanded={all} onClick={() => setAll((v) => !v)}>
              {all ? 'Pokaż mniej' : `Pokaż wszystkie (${actions.data.length})`}
            </button>
          )}
          <p className="small muted">
            Lista powstaje z jawnych reguł (terminy, progi dopasowania, cel tygodniowy) — Ty decydujesz, co zrobić.
          </p>
        </>
      )}
    </Card>
  );
}
