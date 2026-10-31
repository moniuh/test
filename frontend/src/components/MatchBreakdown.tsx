import type { MatchResult, RequirementMatch } from '../api/types';
import { MATCH_LEVEL } from '../api/labels';
import { Badge, ScoreRing, type Tone } from './ui';

const LEVEL_TONE: Record<RequirementMatch['level'], Tone> = {
  FULL: 'success', PARTIAL: 'warning', RELATED: 'warning', PROFILE_ONLY: 'info', MISSING: 'danger',
};

/** Rozbicie dopasowania: wynik, wyjaśnienie (#34) i każde wymaganie z cytatem z CV jako dowodem. */
export function MatchBreakdown({ match }: { match: MatchResult }) {
  return (
    <div className="stack">
      <div className="row">
        <ScoreRing score={match.score} size={84} />
        <div>
          <p style={{ margin: 0 }}>{match.summary}</p>
          {match.cvName && <p className="muted small" style={{ margin: 0 }}>Porównanie z CV: {match.cvName}</p>}
        </div>
      </div>

      {match.explanation.length > 0 && (
        <details open>
          <summary className="label">Dlaczego {match.score}%?</summary>
          <ul className="small" style={{ marginTop: '0.5rem' }}>
            {match.explanation.map((e) => (
              <li key={e}>{e}</li>
            ))}
          </ul>
        </details>
      )}

      {match.requirements.length > 0 && (
        <ul className="req-list" aria-label="Wymagania oferty">
          {match.requirements.map((r, i) => (
            <li key={`${i}-${r.requirement}`} className="req">
              <div className="req-head">
                <div>
                  <strong>{r.requirement}</strong>{' '}
                  <span className="muted small">{r.mustHave ? '· obowiązkowe' : '· mile widziane'}</span>
                </div>
                <div className="row" style={{ gap: '0.4rem', flexWrap: 'nowrap' }}>
                  <Badge tone={LEVEL_TONE[r.level]}>{MATCH_LEVEL[r.level]}</Badge>
                  <span className="req-points" title="Punkty zdobyte / możliwe">
                    {Math.round(r.points)}/{Math.round(r.maxPoints)} pkt
                  </span>
                </div>
              </div>
              {r.evidence && (
                <blockquote className={`evidence ${r.level === 'RELATED' ? 'related' : ''}`}>
                  <span className="sr-only">Cytat z CV: </span>„{r.evidence}”
                </blockquote>
              )}
              {r.note && <p className="small muted" style={{ margin: '0.35rem 0 0' }}>{r.note}</p>}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
