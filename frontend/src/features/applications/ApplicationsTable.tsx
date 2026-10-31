import { Link } from 'react-router-dom';
import type { ApplicationView } from '../../api/types';
import { APPLICATION_STATUS, APPLICATION_STATUSES, formatDate, todayIso } from '../../api/labels';
import { Badge } from '../../components/ui';
import { isClosed } from './board';
import { daysUntil, followUpLabel } from './format';

/** Widok listy aplikacji (alternatywa dla tablicy Kanban). */
export function ApplicationsTable({ apps }: { apps: ApplicationView[] }) {
  const sorted = [...apps].sort(
    (a, b) =>
      APPLICATION_STATUSES.indexOf(a.status) - APPLICATION_STATUSES.indexOf(b.status) ||
      (a.boardPosition ?? 0) - (b.boardPosition ?? 0) ||
      a.id - b.id,
  );
  const today = todayIso();
  return (
    <div className="table-wrap">
      <table>
        <caption className="sr-only">Aplikacje według etapu</caption>
        <thead>
          <tr>
            <th scope="col">Stanowisko</th>
            <th scope="col">Firma</th>
            <th scope="col">Status</th>
            <th scope="col">Wysłana</th>
            <th scope="col">Follow-up</th>
            <th scope="col">Wersja CV</th>
          </tr>
        </thead>
        <tbody>
          {sorted.map((a) => {
            const days = a.nextFollowUpAt && !isClosed(a.status) ? daysUntil(a.nextFollowUpAt, today) : null;
            return (
              <tr key={a.id}>
                <th scope="row" className="applications-row-head">
                  <Link to={`/aplikacje/${a.id}`}>{a.offerTitle}</Link>
                </th>
                <td>{a.company ?? '—'}</td>
                <td>{APPLICATION_STATUS[a.status]}</td>
                <td>{formatDate(a.appliedAt)}</td>
                <td>
                  {days == null ? (
                    '—'
                  ) : (
                    <Badge tone={days <= 0 ? 'warning' : 'neutral'} title={formatDate(a.nextFollowUpAt)}>
                      {followUpLabel(days)}
                    </Badge>
                  )}
                </td>
                <td>{a.cvVariantName ?? '—'}</td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
