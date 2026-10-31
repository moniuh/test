import { useEffect, useId, useState, type ReactNode } from 'react';
import { errorMessage } from '../../api/client';
import type { CvVariant } from '../../api/types';
import { useAsync } from '../../hooks/useAsync';
import { Badge, EmptyState, ErrorBox, LiveMessage, Loading, SourceBadge } from '../../components/ui';
import { cvToolsApi } from './api';
import {
  PRESELECT_MIN_MENTIONS, applySummary, categoryLabel, initialSelection, pluralize, toggleSelection, yearsLabel,
} from './cvTools';
import type { SkillSuggestion } from './types';

/** #2 — umiejętności rozpoznane w CV z propozycją dopisania do profilu. */
export function CvSkillsSection({ cv, onChanged }: { cv: CvVariant; onChanged: () => void }) {
  const skills = useAsync(() => cvToolsApi.skills(cv.id), [cv.id, cv.updatedAt]);
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string>();
  const [error, setError] = useState<string>();
  const legendId = useId();

  useEffect(() => {
    if (skills.data) setSelected(initialSelection(skills.data.suggestions));
  }, [skills.data]);

  if (skills.loading && !skills.data) return <Loading label="Szukam umiejętności w CV…" />;
  if (skills.error) return <ErrorBox message={skills.error} onRetry={skills.reload} />;
  if (!skills.data) return null;

  const all = skills.data.suggestions;
  const fresh = all.filter((s) => !s.alreadyInProfile);
  const known = all.filter((s) => s.alreadyInProfile);
  const chosen = fresh.filter((s) => selected.has(s.name)).map((s) => s.name);

  async function apply() {
    setBusy(true);
    setError(undefined);
    setMessage(undefined);
    try {
      const r = await cvToolsApi.applySkills(cv.id, chosen);
      setMessage(applySummary(r));
      onChanged();
      skills.reload();
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }

  if (all.length === 0) {
    return (
      <EmptyState title="Słownik nie rozpoznał umiejętności w tej wersji CV">
        <p className="small">Wpisz nazwy technologii i narzędzi w standardowej formie, np. „PostgreSQL”, „Docker”, „Scrum”.</p>
      </EmptyState>
    );
  }

  return (
    <div className="stack">
      <p className="small" style={{ margin: 0 }}>
        Rozpoznano {all.length} {pluralize(all.length, 'umiejętność', 'umiejętności', 'umiejętności')}, w tym spoza
        profilu: {fresh.length}. Wstępnie zaznaczyliśmy te wspomniane co najmniej {PRESELECT_MIN_MENTIONS} razy — decyzja
        należy do Ciebie. Lata doświadczenia zapisujemy tylko wtedy, gdy wynikają wprost z CV; zawsze widzisz cytat, na
        którym się opierają. <SourceBadge source="offline" />
      </p>

      {error && <ErrorBox message={error} />}

      {fresh.length > 0 ? (
        <fieldset aria-labelledby={legendId}>
          <legend id={legendId}>Nowe umiejętności do dodania</legend>
          <div className="row" style={{ marginBottom: '0.4rem' }}>
            <button type="button" className="btn btn-small" onClick={() => setSelected(new Set(fresh.map((s) => s.name)))}>
              Zaznacz wszystkie
            </button>
            <button type="button" className="btn btn-small" onClick={() => setSelected(new Set())}>
              Odznacz wszystkie
            </button>
          </div>
          <ul className="list-plain">
            {fresh.map((s) => (
              <SkillItem key={s.name} skill={s}>
                <label>
                  <input
                    type="checkbox"
                    checked={selected.has(s.name)}
                    onChange={() => setSelected((prev) => toggleSelection(prev, s.name))}
                  />
                  {s.name}
                </label>
              </SkillItem>
            ))}
          </ul>
          <div className="form-actions">
            <button type="button" className="btn btn-primary" disabled={busy || chosen.length === 0} onClick={apply}>
              {busy ? 'Dodaję…' : `Dodaj do profilu (${chosen.length})`}
            </button>
          </div>
        </fieldset>
      ) : (
        <p className="small">✓ Wszystkie umiejętności z tego CV są już w Twoim profilu.</p>
      )}
      <LiveMessage message={message} />

      {known.length > 0 && (
        <details>
          <summary className="label">Już w profilu ({known.length})</summary>
          <ul className="list-plain" style={{ marginTop: '0.5rem' }}>
            {known.map((s) => (
              <SkillItem key={s.name} skill={s}>
                <strong>{s.name}</strong>
                <Badge tone="success">✓ w profilu{s.profileYears != null ? ` · ${yearsLabel(s.profileYears)}` : ''}</Badge>
              </SkillItem>
            ))}
          </ul>
        </details>
      )}
    </div>
  );
}

function SkillItem({ skill, children }: { skill: SkillSuggestion; children: ReactNode }) {
  return (
    <li className="profile-skill-item">
      <div className="profile-skill-head">
        {children}
        <Badge>{categoryLabel(skill.category)}</Badge>
        {skill.years != null && (
          <Badge tone="info" title="Lata doświadczenia wynikające z CV">
            {yearsLabel(skill.years)} wg CV
          </Badge>
        )}
        {skill.mentions > 1 && <span className="muted small">wzmianek: {skill.mentions}</span>}
      </div>
      {skill.evidence && (
        <blockquote className="evidence">
          <span className="sr-only">Cytat z CV: </span>
          {skill.evidence}
        </blockquote>
      )}
      {skill.years != null && skill.yearsEvidence && skill.yearsEvidence !== skill.evidence && (
        <p className="muted small" style={{ margin: 0 }}>
          Lata na podstawie: „{skill.yearsEvidence}”
        </p>
      )}
    </li>
  );
}
