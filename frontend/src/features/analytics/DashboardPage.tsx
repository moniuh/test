import { useState } from 'react';
import { Link } from 'react-router-dom';
import { formatDate, formatDateTime } from '../../api/labels';
import { Badge, Card, EmptyState, ErrorBox, Loading, PageHeader, ScoreBadge } from '../../components/ui';
import { useAsync } from '../../hooks/useAsync';
import { useDocumentTitle } from '../../hooks/useDocumentTitle';
import { analyticsApi } from './api';
import { barWidth, count, firstName, goalText, progressWidth, streakText } from './logic';
import { MoodQuickEntry } from './MoodQuickEntry';
import { NextActionsCard } from './NextActionsCard';
import type { Dashboard } from './types';
import './analytics.css';

/** Pulpit tygodniowy (#73) z „Co dalej?” (#97) i szybkim wpisem nastroju (#76). */
export function DashboardPage() {
  useDocumentTitle('Pulpit');
  const dashboard = useAsync(() => analyticsApi.dashboard(), []);
  const [actionsKey, setActionsKey] = useState(0);
  const d = dashboard.data;
  const name = firstName(d?.candidateName);

  return (
    <>
      <PageHeader
        title="Pulpit"
        subtitle={d
          ? `${name ? `Cześć, ${name}! ` : ''}Twój tydzień: ${formatDate(d.week.start)} – ${formatDate(d.week.end)}.`
          : 'Twoje poszukiwania pracy w jednym miejscu.'}
        actions={<Link className="btn" to="/analityka">Analityka</Link>}
      />
      {dashboard.loading && !d && <Loading />}
      {dashboard.error && <ErrorBox message={dashboard.error} onRetry={dashboard.reload} />}
      {d && (
        <div className="stack">
          <Stats d={d} />
          <div className="layout-sidebar">
            <div className="stack">
              <WeekGoal d={d} />
              <NextActionsCard refreshKey={actionsKey} />
              <TopOffers d={d} />
            </div>
            <div className="stack">
              <MoodQuickEntry onSaved={() => setActionsKey((k) => k + 1)} />
              <Upcoming d={d} />
              <FollowUps d={d} />
            </div>
          </div>
        </div>
      )}
    </>
  );
}

function Stats({ d }: { d: Dashboard }) {
  return (
    <section aria-labelledby="pulpit-liczniki">
      <h2 id="pulpit-liczniki" className="sr-only">Liczniki</h2>
      <div className="grid-4 analytics-stats">
        <div className="card stat">
          <span className="stat-value">{d.week.applications} <small>/ {d.week.goal}</small></span>
          <span className="stat-label">aplikacje wysłane w tym tygodniu / cel</span>
        </div>
        <div className="card stat">
          <span className="stat-value">{d.counters.activeOffers}</span>
          <span className="stat-label"><Link to="/oferty">aktywne oferty</Link> (nowe i zapisane)</span>
        </div>
        <div className="card stat">
          <span className="stat-value">{d.counters.openApplications}</span>
          <span className="stat-label"><Link to="/aplikacje">otwarte aplikacje</Link></span>
        </div>
        <div className="card stat">
          <span className="stat-value">{d.counters.upcomingInterviews}</span>
          <span className="stat-label"><Link to="/rozmowy">nadchodzące rozmowy</Link></span>
        </div>
      </div>
    </section>
  );
}

function WeekGoal({ d }: { d: Dashboard }) {
  const w = d.week;
  const max = Math.max(w.goal, ...d.recentWeeks.map((b) => b.applications));
  return (
    <Card title="Cel tygodniowy">
      <p id="cel-tygodniowy-opis">{goalText(w)}</p>
      <div
        className={`progress analytics-progress${w.achieved ? ' achieved' : ''}`}
        role="progressbar"
        aria-labelledby="cel-tygodniowy-opis"
        aria-valuemin={0}
        aria-valuemax={w.goal}
        aria-valuenow={Math.min(w.applications, w.goal)}
      >
        <span style={{ width: progressWidth(w.percent) }} />
      </div>
      <p className="small muted">
        {w.achieved
          ? 'Świetna robota! Kolejne aplikacje to bonus — nie musisz przyspieszać.'
          : `Do niedzieli: ${count(w.daysLeft, 'dzień', 'dni', 'dni')}. Lepiej mniej, a lepiej dopasowanych aplikacji.`}{' '}
        {streakText(d.streak)}
      </p>
      <h3 className="small">Ostatnie {d.recentWeeks.length} tygodni</h3>
      <ol className="analytics-weeks">
        {d.recentWeeks.map((b, i) => {
          const current = i === d.recentWeeks.length - 1;
          return (
            <li key={b.start} className={current ? 'current' : undefined}>
              <span className="value" aria-hidden="true">{b.applications}</span>
              <span className="col" aria-hidden="true">
                <span className={b.achieved ? 'achieved' : undefined} style={{ height: barWidth(b.applications, max) }} />
              </span>
              <span>
                <span className="sr-only">
                  {`Tydzień od ${formatDate(b.start)}${current ? ' (bieżący)' : ''}: ${count(b.applications, 'aplikacja', 'aplikacje', 'aplikacji')}, cel ${b.achieved ? 'osiągnięty' : 'nieosiągnięty'}`}
                </span>
                <span aria-hidden="true">{b.start.slice(8, 10)}.{b.start.slice(5, 7)}{b.achieved ? ' ✓' : ''}</span>
              </span>
            </li>
          );
        })}
      </ol>
      <p className="small muted">Pod kolumną: poniedziałek tygodnia; ✓ = cel osiągnięty.</p>
    </Card>
  );
}

function TopOffers({ d }: { d: Dashboard }) {
  return (
    <Card title="Najlepiej dopasowane oferty bez aplikacji" actions={<Link to="/oferty">Wszystkie oferty</Link>}>
      {d.topOffers.length === 0 ? (
        <EmptyState title="Brak nowych ofert do rozważenia">
          <p><Link to="/oferty/nowa">Dodaj ofertę</Link>, aby zobaczyć dopasowanie.</p>
        </EmptyState>
      ) : (
        <ul className="list-plain">
          {d.topOffers.map((o) => (
            <li key={o.offerId}>
              <div className="spread">
                <Link to={`/oferty/${o.offerId}`}><strong>{o.title}</strong></Link>
                <ScoreBadge score={o.score} />
              </div>
              <div className="small muted">{o.company ?? 'Firma nieznana'} · {o.summary}</div>
              {o.missingSkills.length > 0 && (
                <div className="small">Braki: {o.missingSkills.join(', ')}</div>
              )}
              {o.sameCompanyApplicationId != null && (
                <div className="small">
                  <Badge tone="warning">Uwaga</Badge>{' '}
                  Masz już <Link to={`/aplikacje/${o.sameCompanyApplicationId}`}>aplikację do tej firmy</Link> — sprawdź,
                  czy to nie to samo ogłoszenie.
                </div>
              )}
            </li>
          ))}
        </ul>
      )}
    </Card>
  );
}

function Upcoming({ d }: { d: Dashboard }) {
  return (
    <Card title="Rozmowy w ciągu 7 dni">
      {d.upcomingInterviews.length === 0 ? (
        <p className="muted">Brak zaplanowanych rozmów w najbliższym tygodniu.</p>
      ) : (
        <ul className="list-plain">
          {d.upcomingInterviews.map((i) => (
            <li key={i.interviewId}>
              <strong>{formatDateTime(i.scheduledAt)}</strong> · {i.kindLabel}
              <div>
                <Link to={`/oferty/${i.offerId}`}>{i.company} — {i.position}</Link>
              </div>
              <div className="small"><Link to={`/aplikacje/${i.applicationId}`}>Szczegóły aplikacji</Link></div>
            </li>
          ))}
        </ul>
      )}
    </Card>
  );
}

function FollowUps({ d }: { d: Dashboard }) {
  return (
    <Card title="Zaległe follow-upy">
      {d.overdueFollowUps.length === 0 ? (
        <p className="muted">Wszystkie przypomnienia są na bieżąco.</p>
      ) : (
        <ul className="list-plain">
          {d.overdueFollowUps.map((f) => (
            <li key={f.applicationId}>
              <Link to={`/aplikacje/${f.applicationId}`}>{f.company} — {f.position}</Link>
              <div className="small">
                <Badge tone={f.daysOverdue > 0 ? 'danger' : 'warning'}>
                  {f.daysOverdue > 0 ? `${count(f.daysOverdue, 'dzień', 'dni', 'dni')} po terminie` : 'termin dziś'}
                </Badge>{' '}
                <span className="muted">termin: {formatDate(f.dueDate)} · status: {f.statusLabel}</span>
              </div>
            </li>
          ))}
        </ul>
      )}
    </Card>
  );
}
