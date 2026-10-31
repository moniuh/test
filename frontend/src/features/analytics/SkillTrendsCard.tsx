import { useState } from 'react';
import { formatDate } from '../../api/labels';
import { Badge, Card, EmptyState, ErrorBox, Field, Loading } from '../../components/ui';
import { useAsync } from '../../hooks/useAsync';
import { analyticsApi } from './api';
import { barWidth, formatPercent, trendText } from './logic';

const WINDOWS = [14, 30, 60, 90];

/** Trendy umiejętności w ofertach (#75): udział ofert, pokrycie przez kandydata i zmiana względem poprzedniego okna. */
export function SkillTrendsCard() {
  const [days, setDays] = useState(30);
  const trends = useAsync(() => analyticsApi.skillTrends(days), [days]);
  const t = trends.data;

  return (
    <Card title="Trendy umiejętności w ofertach" className="analytics-trends">
      <div className="analytics-filters">
        <Field label="Okres">
          {(id) => (
            <select id={id} value={days} onChange={(e) => setDays(Number(e.target.value))}>
              {WINDOWS.map((w) => <option key={w} value={w}>ostatnie {w} dni</option>)}
            </select>
          )}
        </Field>
      </div>
      {trends.loading && !t && <Loading />}
      {trends.error && <ErrorBox message={trends.error} onRetry={trends.reload} />}
      {t && (
        <>
          <p className="small" aria-live="polite">{t.summary}</p>
          {t.skills.length === 0 ? (
            <EmptyState title="Brak umiejętności do pokazania" />
          ) : (
            <div className="bars" role="list" aria-label="Najczęściej wymagane umiejętności">
              {t.skills.map((s) => (
                <div key={s.skill} role="listitem">
                  <div className="bar-row">
                    <span><strong>{s.skill}</strong></span>
                    <span className="bar-track" aria-hidden="true">
                      <span className={`bar-fill${s.candidateHas ? ' success' : ' warning'}`}
                        style={{ width: barWidth(s.share, 100), display: 'block' }} />
                    </span>
                    <span className="analytics-num">
                      {formatPercent(s.share)} ofert ({s.offers})
                    </span>
                    <span className="meta">
                      {s.candidateHas
                        ? <Badge tone="success">✓ masz ({s.candidateSource})</Badge>
                        : <Badge tone="warning">brak w CV i profilu</Badge>}
                      <span>obowiązkowa: {s.mustHave}, mile widziana: {s.niceToHave}</span>
                      <span>{trendText(s.trend, s.deltaPp)}{s.trend !== 'UNKNOWN' && ` (wcześniej ${formatPercent(s.previousShare)})`}</span>
                    </span>
                  </div>
                </div>
              ))}
            </div>
          )}
          <p className="small muted">
            Okno: {formatDate(t.from)} – {formatDate(t.to)} ({t.offersInWindow} ofert); porównanie z {formatDate(t.previousFrom)} –{' '}
            {formatDate(t.previousTo)} ({t.offersInPreviousWindow} ofert). Data oferty to data publikacji (lub dodania).
            Zielony słupek = masz tę umiejętność, żółty = brak.
          </p>
        </>
      )}
    </Card>
  );
}
