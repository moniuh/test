import { useState, type FormEvent } from 'react';
import { errorMessage } from '../../api/client';
import type { CvVariant } from '../../api/types';
import { OFFER_STATUS, WORK_MODE } from '../../api/labels';
import { Card, Field } from '../../components/ui';
import {
  ALL_STATUSES, ALL_WORK_MODES, DEFAULT_FILTERS, MIN_SCORE_OPTIONS, SORT_LABEL, describeFilters, hasActiveFilters, toggle,
} from './ranking';
import type { RankingFilters, RankingSort } from './types';

/** Filtry rankingu (status, fraza, min. dopasowanie, tryb pracy, min. wynagrodzenie) i zapis wyszukiwania (#33). */
export function RankingFiltersForm({
  filters, onChange, cvs, excludedCount, onSave,
}: {
  filters: RankingFilters;
  onChange: (f: RankingFilters) => void;
  cvs: CvVariant[] | undefined;
  excludedCount: number;
  onSave: (name: string) => Promise<void>;
}) {
  const [saving, setSaving] = useState(false);
  const [name, setName] = useState('');
  const [saveError, setSaveError] = useState<string>();
  const set = (patch: Partial<RankingFilters>) => onChange({ ...filters, ...patch });

  function openSave() {
    setName(describeFilters(filters).slice(0, 120));
    setSaveError(undefined);
    setSaving(true);
  }

  async function submitSave(e: FormEvent) {
    e.preventDefault();
    if (!name.trim()) {
      setSaveError('Podaj nazwę wyszukiwania.');
      return;
    }
    try {
      await onSave(name);
      setSaving(false);
    } catch (err) {
      setSaveError(errorMessage(err));
    }
  }

  return (
    <Card title="Filtry" className="matching-filters">
      <form role="search" aria-label="Filtry ofert" onSubmit={(e) => e.preventDefault()}>
        <div className="grid-3">
          <Field label="Szukaj w ofertach" hint="Stanowisko, firma, technologia, treść ogłoszenia">
            {(id, describedBy) => (
              <input
                id={id}
                type="search"
                value={filters.q}
                aria-describedby={describedBy}
                onChange={(e) => set({ q: e.target.value })}
                placeholder="np. kafka zdalnie"
              />
            )}
          </Field>
          <Field label="Sortowanie">
            {(id) => (
              <select id={id} value={filters.sort} onChange={(e) => set({ sort: e.target.value as RankingSort })}>
                {(Object.keys(SORT_LABEL) as RankingSort[]).map((s) => (
                  <option key={s} value={s}>{SORT_LABEL[s]}</option>
                ))}
              </select>
            )}
          </Field>
          {cvs && cvs.length > 1 ? (
            <Field label="Wersja CV do dopasowania">
              {(id) => (
                <select
                  id={id}
                  value={filters.cvId ?? ''}
                  onChange={(e) => set({ cvId: e.target.value ? Number(e.target.value) : null })}
                >
                  <option value="">Aktywne CV</option>
                  {cvs.map((c) => (
                    <option key={c.id} value={c.id}>{c.name}</option>
                  ))}
                </select>
              )}
            </Field>
          ) : (
            <div />
          )}
        </div>

        <div className="grid-3">
          <fieldset>
            <legend>Status</legend>
            <div className="checks">
              {ALL_STATUSES.map((s) => (
                <label key={s}>
                  <input
                    type="checkbox"
                    checked={filters.statuses.includes(s)}
                    onChange={() => set({ statuses: toggle(filters.statuses, s, ALL_STATUSES) })}
                  />
                  {OFFER_STATUS[s]}
                </label>
              ))}
            </div>
            <small className="muted">Bez zaznaczenia — wszystkie.</small>
          </fieldset>
          <fieldset>
            <legend>Tryb pracy</legend>
            <div className="checks">
              {ALL_WORK_MODES.map((m) => (
                <label key={m}>
                  <input
                    type="checkbox"
                    checked={filters.workModes.includes(m)}
                    onChange={() => set({ workModes: toggle(filters.workModes, m, ALL_WORK_MODES) })}
                  />
                  {WORK_MODE[m]}
                </label>
              ))}
            </div>
            <small className="muted">Bez zaznaczenia — dowolny.</small>
          </fieldset>
          <div>
            <Field label="Min. dopasowanie do CV">
              {(id) => (
                <select
                  id={id}
                  value={filters.minScore ?? ''}
                  onChange={(e) => set({ minScore: e.target.value ? Number(e.target.value) : null })}
                >
                  <option value="">Dowolne</option>
                  {MIN_SCORE_OPTIONS.map((v) => (
                    <option key={v} value={v}>co najmniej {v}%</option>
                  ))}
                </select>
              )}
            </Field>
          </div>
        </div>

        <div className="grid-3">
          <Field
            label="Min. wynagrodzenie (zł/mies.)"
            hint="Porównujemy z górną granicą widełek jako równoważnikiem UoP brutto (B2B przeliczone). Oferty bez widełek są wtedy ukrywane."
          >
            {(id, describedBy) => (
              <input
                id={id}
                type="number"
                inputMode="numeric"
                min={0}
                step={500}
                value={filters.minSalary ?? ''}
                aria-describedby={describedBy}
                onChange={(e) => {
                  const v = e.target.value === '' ? null : Math.max(0, Number(e.target.value));
                  set({ minSalary: v != null && Number.isFinite(v) ? v : null });
                }}
              />
            )}
          </Field>
          <div>
            <label className="field-inline">
              <input
                type="checkbox"
                checked={filters.showExcluded}
                onChange={(e) => set({ showExcluded: e.target.checked })}
              />
              Pokaż wykluczone ({excludedCount})
            </label>
            <small className="muted">Oferty z Twoimi deal-breakerami (profil → preferencje).</small>
          </div>
          <div className="actions" style={{ alignSelf: 'end', marginBottom: '0.85rem' }}>
            <button
              type="button"
              className="btn"
              disabled={!hasActiveFilters(filters)}
              onClick={() => onChange({ ...DEFAULT_FILTERS, sort: filters.sort, cvId: filters.cvId })}
            >
              Wyczyść filtry
            </button>
            {!saving && (
              <button type="button" className="btn btn-primary" onClick={openSave}>
                Zapisz wyszukiwanie
              </button>
            )}
          </div>
        </div>
      </form>

      {saving && (
        <form className="matching-save-form" onSubmit={submitSave} aria-label="Zapis wyszukiwania">
          <Field label="Nazwa wyszukiwania" error={saveError}>
            {(id, describedBy) => (
              <input
                id={id}
                type="text"
                value={name}
                maxLength={120}
                autoFocus
                aria-describedby={describedBy}
                aria-invalid={saveError ? true : undefined}
                onChange={(e) => setName(e.target.value)}
              />
            )}
          </Field>
          <button type="submit" className="btn btn-primary">Zapisz</button>
          <button type="button" className="btn" onClick={() => setSaving(false)}>Anuluj</button>
        </form>
      )}
    </Card>
  );
}
