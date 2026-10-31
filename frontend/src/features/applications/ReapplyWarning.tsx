import { Link } from 'react-router-dom';
import { formatDate } from '../../api/labels';
import { Alert } from '../../components/ui';
import type { PreviousApplication, ReapplyCheck } from './types';

/** Ostrzeżenie przed ponowną aplikacją do tej samej firmy lub na podobne stanowisko (#47). */
export function ReapplyWarning({ check }: { check: ReapplyCheck }) {
  if (check.sameCompany.length === 0 && check.similarRole.length === 0) return null;
  return (
    <div className="stack" style={{ gap: '0.5rem' }}>
      {check.sameCompany.length > 0 && (
        <Alert tone="warning">
          <div>
            <strong>
              <span aria-hidden="true">⚠ </span>Ponowna aplikacja?
            </strong>{' '}
            {check.warning}
            <ul className="applications-history small">
              {check.sameCompany.map((p) => (
                <PreviousRow key={p.applicationId} p={p} />
              ))}
            </ul>
          </div>
        </Alert>
      )}
      {check.similarRole.length > 0 && (
        <details>
          <summary className="small">Podobne stanowiska w innych firmach ({check.similarRole.length})</summary>
          <ul className="applications-history small">
            {check.similarRole.map((p) => (
              <PreviousRow key={p.applicationId} p={p} showCompany />
            ))}
          </ul>
        </details>
      )}
    </div>
  );
}

function PreviousRow({ p, showCompany = false }: { p: PreviousApplication; showCompany?: boolean }) {
  return (
    <li>
      <Link to={`/aplikacje/${p.applicationId}`}>{p.offerTitle}</Link>
      {showCompany && p.company && <> — {p.company}</>}
      {' · '}
      {p.outcome}
      {p.date && <> · {formatDate(p.date)}</>}
    </li>
  );
}
