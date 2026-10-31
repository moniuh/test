import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import type { ApplicationView } from '../../api/types';
import { APPLICATION_STATUS } from '../../api/labels';
import { useAsync } from '../../hooks/useAsync';
import { useDocumentTitle } from '../../hooks/useDocumentTitle';
import { Badge, ErrorBox, LiveMessage, Loading, PageHeader } from '../../components/ui';
import { applicationsApi } from './api';
import { isClosed } from './board';
import { CoverLetterCard } from './CoverLetterCard';
import { DetailsCard } from './DetailsCard';
import { FollowUpCard } from './FollowUpCard';
import { InterviewsCard } from './InterviewsCard';
import { MessagesCard } from './MessagesCard';
import { SnapshotCard } from './SnapshotCard';
import { StatusCard } from './StatusCard';
import { TimelineCard } from './TimelineCard';
import { useAction } from './useAction';
import './applications.css';

/**
 * Szczegóły aplikacji: status, follow-up (#41), kontakt i oczekiwania, oś czasu (#40), rozmowy z .ics (#45, #52),
 * szablony wiadomości (#43), list motywacyjny (#42) i „Co wysłano” (#46).
 */
export function ApplicationDetailPage() {
  const id = Number(useParams().id);
  const navigate = useNavigate();
  const app = useAsync(() => applicationsApi.get(id), [id]);
  const [version, setVersion] = useState(0);
  const [message, setMessage] = useState<string>();
  const remover = useAction();
  useDocumentTitle(app.data ? `Aplikacja: ${app.data.offerTitle}` : 'Aplikacja');

  if (app.loading && !app.data) return <Loading />;
  if (app.error && !app.data) return <ErrorBox message={app.error} onRetry={app.reload} />;
  if (!app.data) return null;
  const a = app.data;

  function changed(updated?: ApplicationView, msg?: string) {
    if (updated) app.setData(updated);
    setVersion((v) => v + 1);
    if (msg) setMessage(msg);
  }

  async function remove() {
    if (!window.confirm('Usunąć aplikację wraz z historią i rozmowami? Tej operacji nie można cofnąć.')) return;
    const ok = await remover.run(async () => {
      await applicationsApi.remove(id);
      return true;
    });
    if (ok) navigate('/aplikacje');
  }

  return (
    <>
      <p className="small no-print">
        <Link to="/aplikacje">← Aplikacje</Link>
      </p>
      <PageHeader
        title={a.offerTitle}
        subtitle={
          <>
            <Link to={`/oferty/${a.offerId}`}>{[a.company, a.location].filter(Boolean).join(' · ') || 'Oferta'}</Link>
            {' · '}
            <Badge tone={a.status === 'REJECTED' ? 'danger' : isClosed(a.status) ? 'neutral' : 'info'}>
              {APPLICATION_STATUS[a.status]}
            </Badge>
          </>
        }
        actions={
          <button type="button" className="btn btn-danger" onClick={remove} disabled={remover.busy}>
            Usuń aplikację
          </button>
        }
      />
      {remover.error && <ErrorBox message={remover.error} />}
      <LiveMessage message={message} />
      <div className="layout-sidebar">
        <div className="stack">
          <TimelineCard appId={a.id} version={version} onChanged={changed} />
          <InterviewsCard appId={a.id} version={version} onChanged={changed} />
          <MessagesCard app={a} onChanged={changed} />
          <CoverLetterCard app={a} onChanged={changed} />
          <SnapshotCard app={a} version={version} onChanged={changed} />
        </div>
        <div className="stack">
          <StatusCard app={a} onChanged={changed} />
          <FollowUpCard app={a} onChanged={changed} />
          <DetailsCard app={a} onChanged={changed} />
        </div>
      </div>
    </>
  );
}
