import { useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../../api/client';
import type { CvVariant } from '../../api/types';
import { Badge, Card, EmptyState, ErrorBox, Field, Loading } from '../../components/ui';
import { useAsync } from '../../hooks/useAsync';
import { analyticsApi } from './api';
import { GAP_TYPE_LABEL } from './logic';

/** Ile luk pokazujemy domyślnie. */
const VISIBLE = 12;

/** Luki kompetencyjne na tle rynku (#30) — tabela posortowana wg liczby „odblokowanych” ofert. */
export function SkillGapsCard() {
  const cvs = useAsync(() => api.get<CvVariant[]>('/api/cv'), []);
  const [cvId, setCvId] = useState<number | null>(null);
  const report = useAsync(() => analyticsApi.skillGaps(cvId), [cvId]);
  const [all, setAll] = useState(false);
  const r = report.data;

  return (
    <Card title="Luki kompetencyjne na tle rynku">
      {cvs.data && cvs.data.length > 1 && (
        <div className="analytics-filters">
          <Field label="Wersja CV">
            {(id) => (
              <select id={id} value={cvId ?? ''} onChange={(e) => setCvId(e.target.value ? Number(e.target.value) : null)}>
                <option value="">Aktywna wersja CV</option>
                {cvs.data!.map((cv) => (
                  <option key={cv.id} value={cv.id}>{cv.name}</option>
                ))}
              </select>
            )}
          </Field>
        </div>
      )}
      {report.loading && <Loading label="Liczę symulacje dopasowania…" />}
      {report.error && <ErrorBox message={report.error} onRetry={report.reload} />}
      {r && !report.loading && (
        <>
          <p className="small muted">
            {r.cvName ? <>CV: <strong>{r.cvName}</strong> · przeanalizowane oferty (nowe i zapisane): {r.offersAnalyzed}. </> : null}
            {r.note}
          </p>
          {r.gaps.length === 0 ? (
            <EmptyState title="Brak luk w aktywnych ofertach">
              <p>Twoje CV pokrywa wymagania zebranych ofert albo nie ma jeszcze ofert do analizy.</p>
            </EmptyState>
          ) : (
            <>
              <div className="table-wrap">
                <table className="analytics-table">
                  <caption className="sr-only">Brakujące umiejętności, od największej liczby odblokowanych ofert</caption>
                  <thead>
                    <tr>
                      <th scope="col">Umiejętność</th>
                      <th scope="col" className="analytics-num">Odblokowane oferty (≥{r.threshold}%)</th>
                      <th scope="col" className="analytics-num">Brakuje w ofertach</th>
                      <th scope="col" className="analytics-num">w tym obowiązkowa</th>
                      <th scope="col" className="analytics-num">Śr. strata pkt</th>
                    </tr>
                  </thead>
                  <tbody>
                    {(all ? r.gaps : r.gaps.slice(0, VISIBLE)).map((g) => (
                      <tr key={g.skill}>
                        <th scope="row">
                          {g.skill}{' '}
                          {g.type !== 'MISSING' && <Badge tone="info">{GAP_TYPE_LABEL[g.type]}</Badge>}
                          {g.hint && <div className="small muted">{g.hint}</div>}
                          {g.unlocked.length > 0 && (
                            <ul className="small">
                              {g.unlocked.map((u) => (
                                <li key={u.offerId}>
                                  <Link to={`/oferty/${u.offerId}`}>{u.title}{u.company ? ` — ${u.company}` : ''}</Link>:{' '}
                                  {u.scoreBefore}% → {u.scoreAfter}%
                                </li>
                              ))}
                            </ul>
                          )}
                        </th>
                        <td className="analytics-num"><strong>{g.unlockedOffers}</strong></td>
                        <td className="analytics-num">{g.offersMissing}</td>
                        <td className="analytics-num">{g.mustHaveMissing}</td>
                        <td className="analytics-num">{g.avgPointsLost.toLocaleString('pl-PL')}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              {r.gaps.length > VISIBLE && (
                <button type="button" className="btn btn-link" aria-expanded={all} onClick={() => setAll((v) => !v)}>
                  {all ? 'Pokaż mniej' : `Pokaż wszystkie (${r.gaps.length})`}
                </button>
              )}
              <p className="small muted">
                „Śr. strata pkt” — o ile punktów średnio wzrósłby wynik dopasowania w ofertach, w których brakuje tej
                umiejętności, gdyby była w CV. Wszystko liczy kod, bez AI.
              </p>
            </>
          )}
        </>
      )}
    </Card>
  );
}
