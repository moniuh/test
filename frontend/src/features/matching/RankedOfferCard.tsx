import { useId } from 'react';
import { Link } from 'react-router-dom';
import { OFFER_STATUS, SENIORITY, WORK_MODE, formatSalary } from '../../api/labels';
import { Badge, ScoreBadge, ScoreRing } from '../../components/ui';
import { formatPoints } from './ranking';
import type { RankComponent, RankedOffer } from './types';

const EXCLUSION_LABEL: Record<RankedOffer['exclusions'][number]['type'], string> = {
  DEAL_BREAKER: 'Fraza wykluczająca',
  WORK_MODE: 'Tryb pracy',
  LOCATION: 'Lokalizacja',
  CONTRACT: 'Forma umowy',
  SALARY: 'Wynagrodzenie',
};

/** Ocena składnika jako tekst + pasek (pasek tylko wizualnie, informację niesie tekst). */
function ComponentValue({ c }: { c: RankComponent }) {
  if (c.key === 'learned') {
    const pct = Math.min(100, Math.abs(c.value) * 100);
    return (
      <>
        <span className={`matching-bar ${c.value < 0 ? 'negative' : ''}`} aria-hidden="true">
          <span style={{ width: `${pct}%` }} />
        </span>
        {c.neutral ? 'nieaktywne' : formatPoints(c.points)}
      </>
    );
  }
  const pct = Math.round(c.value * 100);
  return (
    <>
      <span className="matching-bar" aria-hidden="true">
        <span style={{ width: `${pct}%` }} />
      </span>
      {pct}%{c.neutral ? ' (neutralnie)' : ''}
    </>
  );
}

function weightLabel(c: RankComponent): string {
  return c.key === 'learned' ? `±${Math.round(c.weight * 100)} pkt` : `${Math.round(c.weight * 100)}%`;
}

/** Karta oferty w rankingu: wynik, dopasowanie, wykluczenia (#32) i rozbicie na składniki (#31). */
export function RankedOfferCard({ item, isNew }: { item: RankedOffer; isNew: boolean }) {
  const titleId = useId();
  const o = item.offer;
  const sum = item.components.reduce((acc, c) => acc + c.points, 0);
  const clipped = sum > 100.5 || sum < -0.5;
  const mode = o.workMode ? WORK_MODE[o.workMode] : null;
  // lokalizacja „zdalnie” i tryb „Zdalnie” — bez powtórzenia
  const sameAsMode = mode != null && o.location?.trim().toLowerCase() === mode.toLowerCase();
  const meta = [o.company, sameAsMode ? null : o.location, mode, o.seniority && SENIORITY[o.seniority], formatSalary(o)]
    .filter(Boolean)
    .join(' · ');

  return (
    <article
      className={`matching-offer${item.excluded ? ' is-excluded' : ''}${isNew ? ' is-new' : ''}`}
      aria-labelledby={titleId}
    >
      <div className="matching-offer-head">
        <ScoreRing score={item.rankScore} size={58} label="Ranking" />
        <div className="matching-offer-main">
          <h3 id={titleId}>
            <Link to={`/oferty/${o.id}`}>{o.title}</Link>
          </h3>
          <p className="muted small meta">{meta}</p>
          <div className="badges">
            <span className="matching-metric">
              <span className="small muted">Dopasowanie CV:</span>
              <ScoreBadge score={item.matchScore} />
            </span>
            {o.status !== 'NEW' && (
              <Badge tone={o.status === 'SAVED' ? 'success' : o.status === 'REJECTED' ? 'danger' : 'neutral'}>
                {OFFER_STATUS[o.status]}
              </Badge>
            )}
            {isNew && <Badge tone="info">Nowa od ostatniego sprawdzenia</Badge>}
            {item.excluded && <Badge tone="danger">Wykluczona</Badge>}
          </div>
          {item.missingSkills.length > 0 && (
            <p className="small muted" style={{ margin: '0.35rem 0 0' }}>
              Brakuje w CV: {item.missingSkills.slice(0, 5).join(', ')}
              {item.missingSkills.length > 5 ? ` i ${item.missingSkills.length - 5} więcej` : ''}
            </p>
          )}
        </div>
      </div>

      {item.excluded && (
        <div className="matching-exclusions">
          <strong>Wykluczona przez Twoje deal-breakery:</strong>
          <ul>
            {item.exclusions.map((e, i) => (
              <li key={`${e.type}-${i}`}>
                <span className="sr-only">{EXCLUSION_LABEL[e.type]}: </span>
                {e.reason}
                {e.evidence && (
                  <blockquote>
                    <span className="sr-only">Cytat z ogłoszenia: </span>„{e.evidence}”
                  </blockquote>
                )}
              </li>
            ))}
          </ul>
        </div>
      )}

      <details>
        <summary>Dlaczego {item.rankScore} pkt? Rozbicie rankingu</summary>
        <div className="table-wrap">
          <table className="matching-components">
            <caption className="sr-only">Składniki rankingu oferty {o.title}</caption>
            <thead>
              <tr>
                <th scope="col">Składnik</th>
                <th scope="col">Ocena</th>
                <th scope="col" className="num">Waga</th>
                <th scope="col" className="num">Punkty</th>
                <th scope="col">Wyjaśnienie</th>
              </tr>
            </thead>
            <tbody>
              {item.components.map((c) => (
                <tr key={c.key}>
                  <th scope="row">{c.name}</th>
                  <td className="nowrap"><ComponentValue c={c} /></td>
                  <td className="num">{weightLabel(c)}</td>
                  <td className="num">{formatPoints(c.points)}</td>
                  <td>{c.explanation}</td>
                </tr>
              ))}
            </tbody>
            <tfoot>
              <tr>
                <th scope="row">Razem</th>
                <td colSpan={2} />
                <td className="num">{item.rankScore}</td>
                <td className="small muted">{clipped ? 'Suma obcięta do zakresu 0–100.' : 'Suma punktów składników (0–100).'}</td>
              </tr>
            </tfoot>
          </table>
        </div>
      </details>
    </article>
  );
}
