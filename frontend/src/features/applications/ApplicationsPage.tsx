import { useState, type KeyboardEvent } from 'react';
import { Link } from 'react-router-dom';
import { errorMessage } from '../../api/client';
import type { ApplicationStatus } from '../../api/types';
import { useAsync } from '../../hooks/useAsync';
import { useDocumentTitle } from '../../hooks/useDocumentTitle';
import { Card, EmptyState, ErrorBox, Loading, PageHeader } from '../../components/ui';
import { CALENDAR_ICS_URL, applicationsApi } from './api';
import { moveCard } from './board';
import { Board } from './Board';
import { ApplicationsTable } from './ApplicationsTable';
import { FollowUpsSection } from './FollowUpsSection';
import { readPref, writePref } from './clipboard';
import './applications.css';

const VIEWS = [
  { id: 'tablica', label: 'Tablica' },
  { id: 'lista', label: 'Lista' },
] as const;
type View = (typeof VIEWS)[number]['id'];
const VIEW_KEY = 'radar.aplikacje.widok';

/** Aplikacje: follow-upy (#41) oraz tablica Kanban (#39) lub lista. */
export function ApplicationsPage() {
  useDocumentTitle('Aplikacje');
  const apps = useAsync(() => applicationsApi.list(), []);
  const followUps = useAsync(() => applicationsApi.followUps(), []);
  const [view, setView] = useState<View>(() => readPref(VIEW_KEY, ['tablica', 'lista'] as const, 'tablica'));
  const [moveError, setMoveError] = useState<string>();

  function choose(v: View, focus = false) {
    setView(v);
    writePref(VIEW_KEY, v);
    if (focus) document.getElementById(`aplikacje-zakladka-${v}`)?.focus();
  }

  function onTabKey(e: KeyboardEvent<HTMLButtonElement>) {
    const idx = VIEWS.findIndex((v) => v.id === view);
    let next = -1;
    if (e.key === 'ArrowRight') next = (idx + 1) % VIEWS.length;
    else if (e.key === 'ArrowLeft') next = (idx - 1 + VIEWS.length) % VIEWS.length;
    else if (e.key === 'Home') next = 0;
    else if (e.key === 'End') next = VIEWS.length - 1;
    if (next >= 0) {
      e.preventDefault();
      choose(VIEWS[next].id, true);
    }
  }

  async function handleMove(id: number, status: ApplicationStatus, position: number): Promise<boolean> {
    const before = apps.data;
    if (!before) return false;
    const statusChanged = before.find((a) => a.id === id)?.status !== status;
    apps.setData(moveCard(before, id, status, position));
    setMoveError(undefined);
    try {
      await applicationsApi.move(id, status, position);
      apps.reload();
      if (statusChanged) followUps.reload();
      return true;
    } catch (e) {
      apps.setData(before);
      setMoveError(`Nie udało się przenieść karty: ${errorMessage(e)}`);
      return false;
    }
  }

  return (
    <>
      <PageHeader
        title="Aplikacje"
        subtitle="Wysłane i planowane aplikacje — od wysłania CV do decyzji."
        actions={
          <>
            <Link className="btn" to="/oferty">
              Wybierz ofertę
            </Link>
            <a className="btn" href={CALENDAR_ICS_URL} download>
              Rozmowy do kalendarza (.ics)
            </a>
          </>
        }
      />
      <div className="stack">
        <FollowUpsSection
          state={followUps}
          onChanged={() => {
            followUps.reload();
            apps.reload();
          }}
        />
        <Card>
          <div className="applications-toolbar">
            <div className="tabs" role="tablist" aria-label="Widok aplikacji">
              {VIEWS.map((v) => (
                <button
                  key={v.id}
                  type="button"
                  role="tab"
                  id={`aplikacje-zakladka-${v.id}`}
                  aria-selected={view === v.id}
                  aria-controls={`aplikacje-panel-${v.id}`}
                  tabIndex={view === v.id ? 0 : -1}
                  onClick={() => choose(v.id)}
                  onKeyDown={onTabKey}
                >
                  {v.label}
                </button>
              ))}
            </div>
            {apps.data && <span className="small muted">Aplikacji: {apps.data.length}</span>}
          </div>
          <div role="tabpanel" id={`aplikacje-panel-${view}`} aria-labelledby={`aplikacje-zakladka-${view}`}>
            {apps.error && <ErrorBox message={apps.error} onRetry={apps.reload} />}
            {moveError && <ErrorBox message={moveError} />}
            {apps.loading && !apps.data && <Loading />}
            {apps.data?.length === 0 && (
              <EmptyState title="Brak aplikacji">
                <p className="small">
                  Otwórz ofertę i użyj panelu „Aplikacja”, aby zapisać wysłaną lub planowaną aplikację.
                </p>
              </EmptyState>
            )}
            {apps.data && apps.data.length > 0 &&
              (view === 'tablica' ? <Board apps={apps.data} onMove={handleMove} /> : <ApplicationsTable apps={apps.data} />)}
          </div>
        </Card>
      </div>
    </>
  );
}
