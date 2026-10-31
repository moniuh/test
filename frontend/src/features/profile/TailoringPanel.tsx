import { useId, useState } from 'react';
import { Link } from 'react-router-dom';
import { useAsync } from '../../hooks/useAsync';
import { Badge, Card, EmptyState, ErrorBox, LiveMessage, Loading, ScoreRing, SourceBadge } from '../../components/ui';
import { cvToolsApi } from './api';
import { CopyButton } from './components';
import { MISSING_ACTION, scoreDelta, yearsLabel } from './cvTools';
import type { TailoringResult } from './types';
import './profile.css';

/** Gniazdo modułu „profil”: brakujące słowa kluczowe CV pod ofertę (#5). */
export function TailoringPanel({ offerId, cvId }: { offerId: number; cvId: number | null }) {
  const tailoring = useAsync(() => cvToolsApi.tailoring(offerId, cvId), [offerId, cvId]);
  return (
    <Card title="Dopasuj CV do tej oferty" actions={<SourceBadge source="offline" />}>
      {tailoring.loading && !tailoring.data && <Loading label="Porównuję wymagania z CV i profilem…" />}
      {tailoring.error && <ErrorBox message={tailoring.error} onRetry={tailoring.reload} />}
      {tailoring.data && <TailoringBody result={tailoring.data} />}
    </Card>
  );
}

function MustBadge({ mustHave }: { mustHave: boolean }) {
  return mustHave ? <Badge tone="danger">obowiązkowe</Badge> : <Badge>mile widziane</Badge>;
}

function TailoringBody({ result: r }: { result: TailoringResult }) {
  const [copied, setCopied] = useState<string>();
  const ids = useId();

  if (r.cvId == null) {
    return (
      <EmptyState title="Nie masz jeszcze CV">
        <p className="small">
          <Link to="/profil">Dodaj CV w profilu</Link>, aby zobaczyć, co dopisać pod tę ofertę.
        </p>
      </EmptyState>
    );
  }

  const nothingToDo = r.fromProfile.length === 0 && r.related.length === 0 && r.missing.length === 0 && r.notes.length === 0;

  return (
    <div className="stack">
      <div className="profile-tailor-scores">
        <figure>
          <ScoreRing score={r.currentScore} size={64} label="Dopasowanie teraz" />
          <figcaption>teraz</figcaption>
        </figure>
        <span className="profile-tailor-arrow" aria-hidden="true">
          →
        </span>
        <figure>
          <ScoreRing score={r.projectedScore} size={64} label="Dopasowanie po dopisaniu umiejętności z profilu" />
          <figcaption>po dopisaniu ({scoreDelta(r.currentScore, r.projectedScore)})</figcaption>
        </figure>
        <p style={{ margin: 0, flex: '1 1 240px' }}>
          {r.summary}
          {r.cvName && <span className="muted small"> Porównanie z CV: {r.cvName}.</span>}
        </p>
      </div>

      {nothingToDo && <p className="small">✓ Nic do poprawienia — CV pokrywa wszystkie wymagania tej oferty.</p>}

      {r.fromProfile.length > 0 && (
        <section aria-labelledby={`${ids}-profile`}>
          <h3 id={`${ids}-profile`}>Masz to w profilu — dopisz do CV ({r.fromProfile.length})</h3>
          <p className="muted small">
            Fragmenty w [nawiasach kwadratowych] uzupełnij prawdziwymi informacjami albo usuń. Lata pochodzą z Twojego
            profilu.
          </p>
          <ul className="list-plain">
            {r.fromProfile.map((a) => (
              <li key={a.skill} className="profile-proposal">
                <div className="profile-skill-head">
                  <strong>{a.skill}</strong>
                  <MustBadge mustHave={a.mustHave} />
                  {a.profileYears != null && <Badge tone="info">{yearsLabel(a.profileYears)} w profilu</Badge>}
                  {a.scoreGain > 0 && <Badge tone="success">+{a.scoreGain} pkt</Badge>}
                </div>
                <p className="small muted" style={{ margin: 0 }}>
                  Wymaganie: „{a.requirement}”
                </p>
                <div className="profile-proposal-row">
                  <span className="small">Do sekcji „Umiejętności”:</span>
                  <code className="profile-line">{a.cvLine}</code>
                  <CopyButton text={a.cvLine} ariaLabel={`linia do umiejętności: ${a.skill}`} onResult={setCopied} />
                </div>
                <div className="profile-proposal-row">
                  <blockquote className="evidence profile-quote-neutral">{a.sentence}</blockquote>
                  <CopyButton text={a.sentence} label="Kopiuj zdanie" ariaLabel={`zdanie o ${a.skill}`} onResult={setCopied} />
                </div>
              </li>
            ))}
          </ul>
          {r.fromProfile.length > 1 && (
            <div className="row">
              <CopyButton
                text={r.cvAddition}
                label="Kopiuj wszystkie linie do „Umiejętności”"
                ariaLabel="wszystkie linie do sekcji Umiejętności"
                onResult={setCopied}
              />
            </div>
          )}
        </section>
      )}

      {r.related.length > 0 && (
        <section aria-labelledby={`${ids}-related`}>
          <h3 id={`${ids}-related`}>Pokrewne — opisz uczciwie ({r.related.length})</h3>
          <ul className="list-plain">
            {r.related.map((x) => (
              <li key={x.skill} className="profile-proposal">
                <div className="profile-skill-head">
                  <strong>{x.skill}</strong>
                  <span className="muted small">— masz pokrewne: {x.relatedSkill}</span>
                  <MustBadge mustHave={x.mustHave} />
                </div>
                {x.evidence && (
                  <blockquote className="evidence related">
                    <span className="sr-only">Cytat z CV: </span>
                    {x.evidence}
                  </blockquote>
                )}
                <p className="small" style={{ margin: 0 }}>
                  {x.advice}
                </p>
                <div className="profile-proposal-row">
                  <blockquote className="evidence profile-quote-neutral">{x.sentence}</blockquote>
                  <CopyButton text={x.sentence} label="Kopiuj zdanie" ariaLabel={`zdanie o ${x.relatedSkill}`} onResult={setCopied} />
                </div>
              </li>
            ))}
          </ul>
        </section>
      )}

      {r.missing.length > 0 && (
        <section aria-labelledby={`${ids}-missing`}>
          <h3 id={`${ids}-missing`}>Brakuje — do nauki lub pominięcia ({r.missing.length})</h3>
          <ul className="list-plain">
            {r.missing.map((m) => {
              const action = MISSING_ACTION[m.action];
              return (
                <li key={m.skill ?? m.requirement} className="profile-proposal">
                  <div className="profile-skill-head">
                    <strong>{m.skill ?? `„${m.requirement}”`}</strong>
                    <MustBadge mustHave={m.mustHave} />
                    <Badge tone={action.tone}>{action.label}</Badge>
                  </div>
                  {m.skill && m.requirement !== m.skill && (
                    <p className="small muted" style={{ margin: 0 }}>
                      Wymaganie: „{m.requirement}”
                    </p>
                  )}
                  <p className="small" style={{ margin: 0 }}>
                    {m.advice}
                  </p>
                </li>
              );
            })}
          </ul>
        </section>
      )}

      {r.notes.length > 0 && (
        <section aria-labelledby={`${ids}-notes`}>
          <h3 id={`${ids}-notes`}>Do doprecyzowania ({r.notes.length})</h3>
          <ul className="small">
            {r.notes.map((n) => (
              <li key={n.requirement}>
                „{n.requirement}” — {n.note}
              </li>
            ))}
          </ul>
        </section>
      )}

      <p className="small muted profile-honesty" style={{ margin: 0 }}>
        Dopisuj tylko to, co jest prawdą. Propozycje opierają się wyłącznie na Twoim profilu i CV — Radar nie dopisuje
        umiejętności, których nie masz. <Link to="/profil">Edytuj CV w profilu</Link>.
      </p>
      <LiveMessage message={copied} />
    </div>
  );
}
