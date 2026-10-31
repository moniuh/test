import { Alert, Badge, Card, EmptyState, ErrorBox, Loading } from '../../components/ui';
import { useAsync } from '../../hooks/useAsync';
import { analyticsApi } from './api';
import { formatDays, formatPercent } from './logic';
import type { EffectivenessGroup, Insight } from './types';

function GroupTable({ caption, firstColumn, groups, minSample }: {
  caption: string;
  firstColumn: string;
  groups: EffectivenessGroup[];
  minSample: number;
}) {
  return (
    <div className="table-wrap">
      <table className="analytics-table">
        <caption className="sr-only">{caption}</caption>
        <thead>
          <tr>
            <th scope="col">{firstColumn}</th>
            <th scope="col" className="analytics-num">Aplikacje</th>
            <th scope="col" className="analytics-num">Odpowiedzi</th>
            <th scope="col" className="analytics-num">Rozmowy</th>
            <th scope="col" className="analytics-num">Mediana dni do odpowiedzi</th>
          </tr>
        </thead>
        <tbody>
          {groups.map((g) => (
            <tr key={g.name}>
              <th scope="row">
                {g.name}{' '}
                {g.smallSample && (
                  <Badge tone="warning" title={`Mniej niż ${minSample} aplikacji — wyniki mogą być przypadkowe`}>
                    mała próba
                  </Badge>
                )}
              </th>
              <td className="analytics-num">{g.applications}</td>
              <td className="analytics-num">{formatPercent(g.responseRate)} <span className="muted">({g.responses})</span></td>
              <td className="analytics-num">{formatPercent(g.interviewRate)} <span className="muted">({g.interviews})</span></td>
              <td className="analytics-num">{formatDays(g.medianDaysToResponse)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function InsightBox({ insight }: { insight: Insight }) {
  return (
    <Alert tone={insight.conclusive ? 'success' : 'neutral'}>
      <span><strong>{insight.conclusive ? 'Wniosek: ' : 'Bez wniosku: '}</strong>{insight.text}</span>
    </Alert>
  );
}

/** Skuteczność wg źródła i wersji CV (#72, test A/B CV). */
export function EffectivenessCard() {
  const data = useAsync(() => analyticsApi.effectiveness(), []);
  const e = data.data;
  return (
    <Card title="Skuteczność wg źródła i wersji CV">
      {data.loading && !e && <Loading />}
      {data.error && <ErrorBox message={data.error} onRetry={data.reload} />}
      {e && e.applications === 0 && (
        <EmptyState title="Brak wysłanych aplikacji">
          <p>Statystyki pojawią się po wysłaniu pierwszych aplikacji.</p>
        </EmptyState>
      )}
      {e && e.applications > 0 && (
        <div className="stack">
          <p className="small muted">
            Odsetek odpowiedzi i rozmów liczony z {e.applications} wysłanych aplikacji. Wniosek pokazujemy tylko wtedy,
            gdy różnica jest duża, a obie grupy mają co najmniej {e.minSample} aplikacji — inaczej to może być przypadek.
          </p>
          <div>
            <h3>Źródło ogłoszenia</h3>
            <GroupTable caption="Skuteczność wg źródła" firstColumn="Źródło" groups={e.bySource} minSample={e.minSample} />
            <InsightBox insight={e.sourceInsight} />
          </div>
          <div>
            <h3>Wersja CV (test A/B)</h3>
            <GroupTable caption="Skuteczność wg wersji CV" firstColumn="Wersja CV" groups={e.byCv} minSample={e.minSample} />
            <InsightBox insight={e.cvInsight} />
          </div>
        </div>
      )}
    </Card>
  );
}
