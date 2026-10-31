import { Link, useParams } from 'react-router-dom';
import { api } from '../../api/client';
import type { ApplicationEvent, ApplicationStatus, ApplicationView } from '../../api/types';
import { APPLICATION_STATUS, APPLICATION_STATUSES, EVENT_TYPE, formatDate, formatDateTime } from '../../api/labels';
import { useAsync } from '../../hooks/useAsync';
import { useDocumentTitle } from '../../hooks/useDocumentTitle';
import { Card, ErrorBox, Field, Loading, PageHeader } from '../../components/ui';

/** Szczegóły aplikacji (wersja bazowa; rozbudowuje moduł „aplikacje”). */
export function ApplicationDetailPage() {
  const id = Number(useParams().id);
  const app = useAsync(() => api.get<ApplicationView>(`/api/applications/${id}`), [id]);
  const events = useAsync(() => api.get<ApplicationEvent[]>(`/api/applications/${id}/events`), [id, app.data?.status]);
  useDocumentTitle(app.data ? `Aplikacja: ${app.data.offerTitle}` : 'Aplikacja');

  if (app.loading && !app.data) return <Loading />;
  if (app.error) return <ErrorBox message={app.error} onRetry={app.reload} />;
  const a = app.data!;

  async function changeStatus(status: ApplicationStatus) {
    app.setData(await api.post<ApplicationView>(`/api/applications/${id}/status`, { status }));
  }

  return (
    <>
      <p className="small"><Link to="/aplikacje">← Aplikacje</Link></p>
      <PageHeader title={a.offerTitle} subtitle={<Link to={`/oferty/${a.offerId}`}>{a.company ?? 'Oferta'}</Link>} />
      <div className="grid-2">
        <Card title="Status">
          <Field label="Etap">
            {(fid) => (
              <select id={fid} value={a.status} onChange={(e) => changeStatus(e.target.value as ApplicationStatus)}>
                {APPLICATION_STATUSES.map((s) => (
                  <option key={s} value={s}>{APPLICATION_STATUS[s]}</option>
                ))}
              </select>
            )}
          </Field>
          <p className="small muted">Wysłana: {formatDate(a.appliedAt)} · CV: {a.cvVariantName ?? '—'}</p>
        </Card>
        <Card title="Historia">
          <ul className="timeline">
            {events.data?.map((e) => (
              <li key={e.id}>
                <strong>{EVENT_TYPE[e.type]}</strong>
                {e.toStatus && <> → {APPLICATION_STATUS[e.toStatus]}</>}
                <div className="small muted">{formatDateTime(e.occurredAt)}</div>
                {e.note && <div className="small">{e.note}</div>}
              </li>
            ))}
          </ul>
        </Card>
      </div>
    </>
  );
}
