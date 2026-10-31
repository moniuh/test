import { useState, type FormEvent } from 'react';
import type { Interview, InterviewKind } from '../../api/types';
import { INTERVIEW_KIND } from '../../api/labels';
import { ErrorBox, Field } from '../../components/ui';
import { toLocalInput } from './format';
import type { InterviewDraft } from './types';

const KINDS = Object.keys(INTERVIEW_KIND) as InterviewKind[];

/**
 * Formularz planowania i edycji rozmowy (#45) z notatkami (#52): osoby prowadzące, notatki, zadane pytania.
 * Debriefu (wrażenia, samoocena) tu nie edytujemy — należy do modułu „Rozmowy”.
 */
export function InterviewForm({
  initial, busy, error, onSubmit, onCancel,
}: {
  initial?: Interview;
  busy: boolean;
  error?: string;
  onSubmit: (draft: InterviewDraft) => void;
  onCancel: () => void;
}) {
  const [draft, setDraft] = useState<InterviewDraft>({
    scheduledAt: toLocalInput(initial?.scheduledAt),
    durationMinutes: initial?.durationMinutes ?? 60,
    kind: initial?.kind ?? 'VIDEO',
    location: initial?.location ?? '',
    interviewers: initial?.interviewers ?? '',
    notes: initial?.notes ?? '',
    questionsAsked: initial?.questionsAsked ?? '',
  });
  const [invalid, setInvalid] = useState<string>();

  const set = <K extends keyof InterviewDraft>(k: K, v: InterviewDraft[K]) => setDraft((d) => ({ ...d, [k]: v }));

  function submit(e: FormEvent) {
    e.preventDefault();
    if (!draft.scheduledAt) {
      setInvalid('Podaj datę i godzinę rozmowy.');
      return;
    }
    setInvalid(undefined);
    onSubmit(draft);
  }

  return (
    <form onSubmit={submit} className="applications-interview" aria-label={initial ? 'Edycja rozmowy' : 'Nowa rozmowa'}>
      <div className="applications-form-grid">
        <Field label="Termin" error={invalid}>
          {(id, describedBy) => (
            <input
              id={id}
              type="datetime-local"
              required
              aria-invalid={invalid ? true : undefined}
              aria-describedby={describedBy}
              value={draft.scheduledAt}
              onChange={(e) => set('scheduledAt', e.target.value)}
            />
          )}
        </Field>
        <Field label="Czas trwania (min)">
          {(id) => (
            <input
              id={id}
              type="number"
              min={5}
              max={600}
              step={5}
              value={draft.durationMinutes ?? ''}
              onChange={(e) => set('durationMinutes', e.target.value ? Number(e.target.value) : null)}
            />
          )}
        </Field>
        <Field label="Rodzaj">
          {(id) => (
            <select id={id} value={draft.kind} onChange={(e) => set('kind', e.target.value as InterviewKind)}>
              {KINDS.map((k) => (
                <option key={k} value={k}>
                  {INTERVIEW_KIND[k]}
                </option>
              ))}
            </select>
          )}
        </Field>
        <Field label="Miejsce lub link do spotkania">
          {(id) => <input id={id} type="text" value={draft.location} onChange={(e) => set('location', e.target.value)} />}
        </Field>
        <div className="applications-span">
          <Field label="Osoby prowadzące" hint="Np. „Katarzyna W. (HR), Tech Lead zespołu płatności”.">
            {(id, describedBy) => (
              <input
                id={id}
                aria-describedby={describedBy}
                type="text"
                value={draft.interviewers}
                onChange={(e) => set('interviewers', e.target.value)}
              />
            )}
          </Field>
        </div>
        <div className="applications-span">
          <Field label="Notatki" hint="Przygotowanie, ustalenia, kolejne kroki.">
            {(id, describedBy) => (
              <textarea id={id} aria-describedby={describedBy} rows={4} value={draft.notes} onChange={(e) => set('notes', e.target.value)} />
            )}
          </Field>
        </div>
        <div className="applications-span">
          <Field label="Zadane pytania" hint="Jedno pytanie w linii — przydadzą się przy kolejnych rozmowach.">
            {(id, describedBy) => (
              <textarea
                id={id}
                aria-describedby={describedBy}
                rows={4}
                value={draft.questionsAsked}
                onChange={(e) => set('questionsAsked', e.target.value)}
              />
            )}
          </Field>
        </div>
      </div>
      {error && <ErrorBox message={error} />}
      <div className="form-actions">
        <button type="submit" className="btn btn-primary btn-small" disabled={busy}>
          {initial ? 'Zapisz rozmowę' : 'Zaplanuj rozmowę'}
        </button>
        <button type="button" className="btn btn-small" onClick={onCancel}>
          Anuluj
        </button>
      </div>
    </form>
  );
}
