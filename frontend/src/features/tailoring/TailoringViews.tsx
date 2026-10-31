import { useId, useRef, useState, type KeyboardEvent, type ReactNode } from 'react';
import { MATCH_LEVEL } from '../../api/labels';
import { Alert, Badge, ScoreRing } from '../../components/ui';
import {
  CHANGE_LABEL, CHANGE_TONE, DIFF_LABEL, DIFF_MARK, VIOLATION_LABEL, bulletMoveLabel, diffSummary, formatDelta,
  guardSummary,
} from './logic';
import type { AiRejection, DiffEntry, GuardReport, RoleChange, ScoreComparison } from './types';
import './tailoring.css';

/** Dostępne zakładki: strzałki/Home/End przenoszą fokus, aria-selected na aktywnej. */
export function Tabs<T extends string>({
  label, tabs, active, onChange, children,
}: {
  label: string;
  tabs: { id: T; label: string }[];
  active: T;
  onChange: (id: T) => void;
  children: ReactNode;
}) {
  const base = useId();
  const refs = useRef<(HTMLButtonElement | null)[]>([]);
  function onKey(e: KeyboardEvent<HTMLButtonElement>, index: number) {
    let next = -1;
    if (e.key === 'ArrowRight') next = (index + 1) % tabs.length;
    else if (e.key === 'ArrowLeft') next = (index - 1 + tabs.length) % tabs.length;
    else if (e.key === 'Home') next = 0;
    else if (e.key === 'End') next = tabs.length - 1;
    if (next >= 0) {
      e.preventDefault();
      onChange(tabs[next].id);
      refs.current[next]?.focus();
    }
  }
  return (
    <div>
      <div className="tabs" role="tablist" aria-label={label}>
        {tabs.map((t, i) => (
          <button
            key={t.id}
            ref={(el) => {
              refs.current[i] = el;
            }}
            type="button"
            role="tab"
            id={`${base}-tab-${t.id}`}
            aria-controls={`${base}-panel`}
            aria-selected={active === t.id}
            tabIndex={active === t.id ? 0 : -1}
            onClick={() => onChange(t.id)}
            onKeyDown={(e) => onKey(e, i)}
          >
            {t.label}
          </button>
        ))}
      </div>
      <div role="tabpanel" id={`${base}-panel`} aria-labelledby={`${base}-tab-${active}`} tabIndex={0}>
        {children}
      </div>
    </div>
  );
}

/** Różnice CV bazowe ↔ dopasowane — każda linia ma znacznik i etykietę tekstową, nie tylko kolor. */
export function DiffView({ entries }: { entries: DiffEntry[] }) {
  const [onlyChanges, setOnlyChanges] = useState(true);
  const shown = onlyChanges ? entries.filter((e) => e.kind !== 'SAME') : entries;
  return (
    <div className="stack">
      <div className="spread">
        <p style={{ margin: 0 }}>
          <strong>{diffSummary(entries)}</strong>
        </p>
        <label className="tailoring-check">
          <input type="checkbox" checked={onlyChanges} onChange={(e) => setOnlyChanges(e.target.checked)} />
          Pokaż tylko zmienione linie
        </label>
      </div>
      <p className="small muted" style={{ margin: 0 }}>
        Legenda: „+” dodane, „↕” przeniesione, „~” zmienione (np. kolejność pozycji), „−” ukryte, „·” bez zmian.
      </p>
      {shown.length === 0 ? (
        <p className="muted">Brak zmian do pokazania.</p>
      ) : (
        <ol className="tailoring-diff" aria-label="Różnice między CV bazowym a dopasowanym">
          {shown.map((e, i) => (
            <li key={`${i}-${e.kind}-${e.newLine ?? e.baseLine}`} className={`kind-${e.kind}`}>
              <span className="tailoring-diff-mark" aria-hidden="true">{DIFF_MARK[e.kind]}</span>
              <span className="tailoring-diff-kind">{DIFF_LABEL[e.kind]}</span>
              <span className="tailoring-diff-text">
                {e.text}
                {e.kind === 'CHANGED' && e.previous && (
                  <span className="tailoring-diff-prev">wcześniej: {e.previous}</span>
                )}
                {e.kind === 'MOVED' && e.baseLine != null && e.newLine != null && (
                  <span className="tailoring-diff-prev">
                    linia {e.baseLine} w CV bazowym → linia {e.newLine}
                  </span>
                )}
              </span>
            </li>
          ))}
        </ol>
      )}
    </div>
  );
}

/** Kolejność punktów doświadczenia w stanowiskach (#70). */
export function RoleChangesView({ roles }: { roles: RoleChange[] }) {
  if (roles.length === 0) return <p className="muted">W CV nie rozpoznano sekcji doświadczenia z punktami.</p>;
  return (
    <ul className="tailoring-roles" aria-label="Kolejność punktów w stanowiskach">
      {roles.map((r, i) => {
        const ordered = [...r.bullets].sort(
          (a, b) => (a.newPosition ?? 1000 + a.originalPosition) - (b.newPosition ?? 1000 + b.originalPosition),
        );
        return (
          <li key={`${i}-${r.header}`}>
            <strong>{r.header ?? 'Stanowisko bez nagłówka'}</strong>
            <ol>
              {ordered.map((b) => (
                <li key={b.originalPosition} className={b.hidden ? 'is-hidden' : undefined}>
                  {b.text}{' '}
                  <span className="small muted">
                    — {bulletMoveLabel(b)}
                    {b.matchedSkills.length > 0 && `; dotyczy: ${b.matchedSkills.join(', ')}`}
                    {!b.hidden && b.relevance > 0 && ` (trafność ${b.relevance})`}
                  </span>
                </li>
              ))}
            </ol>
          </li>
        );
      })}
    </ul>
  );
}

/** Raport strażnika prawdomówności (#68). */
export function GuardReportView({ report, rejected = [] }: { report: GuardReport; rejected?: AiRejection[] }) {
  return (
    <div className="stack">
      {report.passed ? (
        <Alert tone="success">
          <span>
            <strong>{guardSummary(report)}.</strong> Każda linia pochodzi z CV bazowego albo z faktów z profilu.
          </span>
        </Alert>
      ) : (
        <Alert tone="warning">
          <span>
            <strong>{guardSummary(report)}.</strong> Popraw tekst albo świadomie potwierdź zapis — tailoring ma
            pokazywać prawdę, nie ją dopisywać.
          </span>
        </Alert>
      )}
      <p className="small muted" style={{ margin: 0 }}>
        Sprawdzono linii: {report.checkedLines} · dosłownie z CV: {report.verbatimLines} · przestawione lub
        przeredagowane: {report.rewrittenLines} · zbudowane z faktów: {report.generatedLines}
      </p>
      {report.profileOnlySkills.length > 0 && (
        <p className="small" style={{ margin: 0 }}>
          Umiejętności z profilu, których nie było w CV bazowym: <strong>{report.profileOnlySkills.join(', ')}</strong>.
          To Twoje dane — upewnij się, że potrafisz je potwierdzić na rozmowie.
        </p>
      )}
      {report.violations.length > 0 && (
        <div className="table-wrap">
          <table>
            <caption className="sr-only">Naruszenia zgłoszone przez strażnika</caption>
            <thead>
              <tr>
                <th scope="col">Linia</th>
                <th scope="col">Rodzaj</th>
                <th scope="col">Wyjaśnienie</th>
                <th scope="col">Treść</th>
              </tr>
            </thead>
            <tbody>
              {report.violations.map((v, i) => (
                <tr key={`${i}-${v.line}-${v.kind}`}>
                  <td>{v.line > 0 ? v.line : '—'}</td>
                  <td>
                    <Badge tone="warning">{VIOLATION_LABEL[v.kind]}</Badge>
                  </td>
                  <td>{v.detail}</td>
                  <td className="small">{v.text}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {rejected.length > 0 && (
        <details>
          <summary className="label">Zdania AI odrzucone przez strażnika ({rejected.length}) — wróciły do wersji offline</summary>
          <ul className="small" style={{ marginTop: '0.5rem' }}>
            {rejected.map((r, i) => (
              <li key={i}>
                <span className="sr-only">Propozycja AI: </span>„{r.proposed}” — {r.reason}
              </li>
            ))}
          </ul>
        </details>
      )}
    </div>
  );
}

/** Wynik przed i po tailoringu wymaganie po wymaganiu (#78). */
export function ScoreComparisonView({ comparison }: { comparison: ScoreComparison }) {
  const order: Record<string, number> = { WORSE: 0, GAINED_EVIDENCE: 1, IMPROVED: 2, STILL_MISSING: 3, UNCHANGED: 4 };
  const reqs = [...comparison.requirements].sort((a, b) => order[a.change] - order[b.change]);
  const changed = reqs.filter((r) => r.change !== 'UNCHANGED');
  const unchanged = reqs.filter((r) => r.change === 'UNCHANGED');
  return (
    <div className="stack">
      <div className="tailoring-scores">
        <div className="tailoring-score">
          <ScoreRing score={comparison.before} size={72} label="Wynik CV bazowego" />
          <span className="small muted">przed</span>
        </div>
        <span className="tailoring-arrow" aria-hidden="true">→</span>
        <div className="tailoring-score">
          <ScoreRing score={comparison.after} size={72} label="Wynik wersji dopasowanej" />
          <span className="small muted">po</span>
        </div>
        <Badge tone={comparison.delta > 0 ? 'success' : comparison.delta < 0 ? 'danger' : 'neutral'}>
          {formatDelta(comparison.delta)}
        </Badge>
      </div>
      <p style={{ margin: 0 }}>{comparison.summary}</p>
      {comparison.stillMissing.length > 0 && (
        <p className="small" style={{ margin: 0 }}>
          Nadal brakuje: <strong>{comparison.stillMissing.join(', ')}</strong>. Jeśli to znasz, dopisz najpierw do profilu
          (z latami doświadczenia) i wygeneruj wersję ponownie.
        </p>
      )}
      <ul className="req-list" aria-label="Wymagania oferty przed i po">
        {changed.map((r, i) => (
          <RequirementRow key={`c-${i}`} r={r} />
        ))}
      </ul>
      {unchanged.length > 0 && (
        <details>
          <summary className="label">Wymagania bez zmian ({unchanged.length})</summary>
          <ul className="req-list">
            {unchanged.map((r, i) => (
              <RequirementRow key={`u-${i}`} r={r} />
            ))}
          </ul>
        </details>
      )}
    </div>
  );
}

function RequirementRow({ r }: { r: ScoreComparison['requirements'][number] }) {
  return (
    <li className="req">
      <div className="req-head">
        <div>
          <strong>{r.requirement}</strong>{' '}
          <span className="muted small">{r.mustHave ? '· obowiązkowe' : '· mile widziane'}</span>
          <div className="tailoring-req-levels">
            {MATCH_LEVEL[r.levelBefore]} → {MATCH_LEVEL[r.levelAfter]} · {Math.round(r.pointsBefore)} →{' '}
            {Math.round(r.pointsAfter)} z {Math.round(r.maxPoints)} pkt
          </div>
        </div>
        <Badge tone={CHANGE_TONE[r.change]}>{CHANGE_LABEL[r.change]}</Badge>
      </div>
      {r.evidenceAfter && r.change !== 'STILL_MISSING' && (
        <blockquote className="evidence">
          <span className="sr-only">Cytat z dopasowanego CV: </span>„{r.evidenceAfter}”
        </blockquote>
      )}
      {r.note && <p className="small muted" style={{ margin: '0.35rem 0 0' }}>{r.note}</p>}
    </li>
  );
}
