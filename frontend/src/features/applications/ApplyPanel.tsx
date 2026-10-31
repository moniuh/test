import { useState } from 'react';
import { Link } from 'react-router-dom';
import { api, errorMessage } from '../../api/client';
import type { ApplicationView, JobOffer } from '../../api/types';
import { APPLICATION_STATUS, formatDate } from '../../api/labels';
import { useAsync } from '../../hooks/useAsync';
import { Card, ErrorBox } from '../../components/ui';

/** Aplikowanie na ofertę z poziomu jej szczegółów (rozszerzane przez moduł „aplikacje”). */
export function ApplyPanel({ offer, cvId }: { offer: JobOffer; cvId: number | null }) {
  const apps = useAsync(() => api.get<ApplicationView[]>(`/api/applications?offerId=${offer.id}`), [offer.id]);
  const [error, setError] = useState<string>();
  const [busy, setBusy] = useState(false);

  async function apply() {
    setBusy(true);
    setError(undefined);
    try {
      await api.post('/api/applications', { offerId: offer.id, cvVariantId: cvId, status: 'APPLIED' });
      apps.reload();
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }

  return (
    <Card title="Aplikacja" headingLevel={2}>
      {error && <ErrorBox message={error} />}
      {apps.data && apps.data.length > 0 ? (
        <ul className="list-plain">
          {apps.data.map((a) => (
            <li key={a.id}>
              <Link to={`/aplikacje/${a.id}`}>{APPLICATION_STATUS[a.status]}</Link>
              <span className="muted small"> · wysłana {formatDate(a.appliedAt)}</span>
            </li>
          ))}
        </ul>
      ) : (
        <button type="button" className="btn btn-primary" onClick={apply} disabled={busy}>
          Oznacz jako wysłaną aplikację
        </button>
      )}
    </Card>
  );
}
