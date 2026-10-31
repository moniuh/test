import { Link } from 'react-router-dom';
import { formatDate } from '../../api/labels';
import { Card, EmptyState, ErrorBox, Loading } from '../../components/ui';
import { useAsync } from '../../hooks/useAsync';
import { analyticsApi } from './api';
import { barWidth, energyLabel, moodLabel, scaleTone } from './logic';
import { MoodWarningAlert } from './MoodWarningAlert';

function ScaleCell({ value, label }: { value: number; label: string }) {
  return (
    <span className="analytics-scale-cell">
      <span className="bar-track" aria-hidden="true">
        <span className={`bar-fill ${scaleTone(value)}`} style={{ width: barWidth(value, 5), display: 'block' }} />
      </span>
      <span>{value}/5 — {label}</span>
    </span>
  );
}

/** Historia dziennika samopoczucia (#76) z ostatnich 30 dni. */
export function MoodHistoryCard() {
  const mood = useAsync(() => analyticsApi.mood(30), []);
  const m = mood.data;
  return (
    <Card title="Dziennik samopoczucia">
      {mood.loading && !m && <Loading />}
      {mood.error && <ErrorBox message={mood.error} onRetry={mood.reload} />}
      {m?.warning && <MoodWarningAlert warning={m.warning} />}
      {m && m.entries.length === 0 && (
        <EmptyState title="Brak wpisów z ostatnich 30 dni">
          <p>Dodaj krótki wpis na <Link to="/">pulpicie</Link> — zajmuje kilka sekund.</p>
        </EmptyState>
      )}
      {m && m.entries.length > 0 && (
        <div className="table-wrap">
          <table className="analytics-table">
            <caption className="sr-only">Wpisy z ostatnich {m.days} dni, od najnowszego</caption>
            <thead>
              <tr>
                <th scope="col">Dzień</th>
                <th scope="col">Nastrój</th>
                <th scope="col">Energia</th>
                <th scope="col">Notatka</th>
              </tr>
            </thead>
            <tbody>
              {m.entries.map((e) => (
                <tr key={e.id}>
                  <th scope="row" className="nowrap">{formatDate(e.date)}</th>
                  <td><ScaleCell value={e.mood} label={moodLabel(e.mood)} /></td>
                  <td><ScaleCell value={e.energy} label={energyLabel(e.energy)} /></td>
                  <td>{e.note ?? <span className="muted">—</span>}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      <p className="small muted">Wpisy zostają na Twoim serwerze. Wspierający komunikat pojawia się, gdy średni nastrój lub energia z 3 ostatnich wpisów wynosi 2 lub mniej albo nastrój spadnie o 2 punkty w ciągu tygodnia.</p>
    </Card>
  );
}
