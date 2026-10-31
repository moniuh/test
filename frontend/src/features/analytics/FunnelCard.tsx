import { useState } from 'react';
import { Card, ErrorBox, Field, Loading } from '../../components/ui';
import { useAsync } from '../../hooks/useAsync';
import { analyticsApi } from './api';
import { barWidth, count, formatPercent, isValidRange } from './logic';

const STAGE_TONE: Record<string, string> = { OFFER: ' success', ACCEPTED: ' success' };

/** Lejek rekrutacyjny (#71) jako wykres słupkowy HTML z wartościami tekstowymi. */
export function FunnelCard() {
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const valid = isValidRange(from, to);
  const funnel = useAsync(
    () => (valid ? analyticsApi.funnel(from || undefined, to || undefined) : Promise.resolve(undefined)),
    [from, to, valid],
  );
  const f = funnel.data;
  const max = f?.stages[0]?.count ?? 0;

  return (
    <Card title="Lejek rekrutacyjny" className="analytics-funnel">
      <div className="analytics-filters">
        <Field label="Wysłane od">
          {(id) => <input id={id} type="date" value={from} onChange={(e) => setFrom(e.target.value)} />}
        </Field>
        <Field label="do" error={valid ? undefined : 'Data „do” jest wcześniejsza niż „od”.'}>
          {(id, describedBy) => (
            <input id={id} type="date" value={to} aria-describedby={describedBy} aria-invalid={!valid}
              onChange={(e) => setTo(e.target.value)} />
          )}
        </Field>
      </div>
      {funnel.loading && !f && <Loading />}
      {funnel.error && <ErrorBox message={funnel.error} onRetry={funnel.reload} />}
      {f && (
        <>
          <div className="bars" role="list" aria-label="Etapy lejka">
            {f.stages.map((s, i) => (
              <div key={s.key} role="listitem">
                <div className="bar-row">
                  <span>{s.label}</span>
                  <span className="bar-track" aria-hidden="true">
                    <span className={`bar-fill${STAGE_TONE[s.key] ?? ''}`} style={{ width: barWidth(s.count, max), display: 'block' }} />
                  </span>
                  <span className="analytics-num"><strong>{s.count}</strong> ({formatPercent(s.conversionFromStart)})</span>
                  {i > 0 && (
                    <span className="conversion">
                      z poprzedniego etapu: {formatPercent(s.conversionFromPrevious)}
                    </span>
                  )}
                </div>
              </div>
            ))}
          </div>
          <p className="small" aria-live="polite">{f.summary}</p>
          {f.plannedExcluded > 0 && (
            <p className="small muted">
              Pominięto {count(f.plannedExcluded, 'aplikację', 'aplikacje', 'aplikacji')} „do wysłania” — liczą się tylko wysłane.
            </p>
          )}
        </>
      )}
    </Card>
  );
}
