import { Link } from 'react-router-dom';
import { api } from '../../api/client';
import type { ApplicationView } from '../../api/types';
import { APPLICATION_STATUS, formatDate } from '../../api/labels';
import { useAsync } from '../../hooks/useAsync';
import { useDocumentTitle } from '../../hooks/useDocumentTitle';
import { Card, EmptyState, ErrorBox, Loading, PageHeader } from '../../components/ui';

/** Lista aplikacji (wersja bazowa; tablicę Kanban dodaje moduł „aplikacje”). */
export function ApplicationsPage() {
  useDocumentTitle('Aplikacje');
  const apps = useAsync(() => api.get<ApplicationView[]>('/api/applications'), []);
  return (
    <>
      <PageHeader title="Aplikacje" subtitle="Wszystkie wysłane i planowane aplikacje." />
      {apps.error && <ErrorBox message={apps.error} onRetry={apps.reload} />}
      {apps.loading && !apps.data && <Loading />}
      {apps.data?.length === 0 && <EmptyState title="Brak aplikacji" />}
      {apps.data && apps.data.length > 0 && (
        <Card>
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th scope="col">Stanowisko</th>
                  <th scope="col">Firma</th>
                  <th scope="col">Status</th>
                  <th scope="col">Wysłana</th>
                </tr>
              </thead>
              <tbody>
                {apps.data.map((a) => (
                  <tr key={a.id}>
                    <td><Link to={`/aplikacje/${a.id}`}>{a.offerTitle}</Link></td>
                    <td>{a.company}</td>
                    <td>{APPLICATION_STATUS[a.status]}</td>
                    <td>{formatDate(a.appliedAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Card>
      )}
    </>
  );
}
