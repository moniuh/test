import { useEffect, useState, type FormEvent } from 'react';
import type { ApplicationView } from '../../api/types';
import { formatMoney } from '../../api/labels';
import { Card, ErrorBox, Field } from '../../components/ui';
import { applicationsApi } from './api';
import { useAction, type OnChanged } from './useAction';

interface Draft {
  contactName: string;
  contactEmail: string;
  salaryExpectation: string;
  source: string;
  appliedAt: string;
  notes: string;
}

function draftOf(a: ApplicationView): Draft {
  return {
    contactName: a.contactName ?? '',
    contactEmail: a.contactEmail ?? '',
    salaryExpectation: a.salaryExpectation == null ? '' : String(a.salaryExpectation),
    source: a.source ?? '',
    appliedAt: a.appliedAt ?? '',
    notes: a.notes ?? '',
  };
}

/** Kontakt, oczekiwania finansowe i pozostałe dane aplikacji. */
export function DetailsCard({ app, onChanged }: { app: ApplicationView; onChanged: OnChanged }) {
  const [draft, setDraft] = useState<Draft>(() => draftOf(app));
  const { busy, error, run, setError } = useAction();

  useEffect(() => setDraft(draftOf(app)), [app]);

  const set = (k: keyof Draft) => (e: { target: { value: string } }) => setDraft((d) => ({ ...d, [k]: e.target.value }));

  async function save(e: FormEvent) {
    e.preventDefault();
    const salary = draft.salaryExpectation.trim() ? Number(draft.salaryExpectation) : null;
    if (salary != null && (!Number.isFinite(salary) || salary < 0)) {
      setError('Oczekiwania finansowe muszą być liczbą (zł miesięcznie).');
      return;
    }
    const a = await run(() =>
      applicationsApi.update(app.id, {
        contactName: draft.contactName.trim(),
        contactEmail: draft.contactEmail.trim(),
        salaryExpectation: salary,
        source: draft.source.trim(),
        appliedAt: draft.appliedAt || null,
        notes: draft.notes,
        // PUT zawsze ustawia termin follow-upu — przekazujemy bieżący, żeby go nie skasować
        nextFollowUpAt: app.nextFollowUpAt,
      }),
    );
    if (a) onChanged(a, 'Zapisano dane aplikacji.');
  }

  return (
    <Card title="Kontakt i oczekiwania">
      <form onSubmit={save}>
        <Field label="Osoba kontaktowa">
          {(id) => <input id={id} type="text" value={draft.contactName} onChange={set('contactName')} autoComplete="off" />}
        </Field>
        <Field label="E-mail kontaktowy">
          {(id) => <input id={id} type="email" value={draft.contactEmail} onChange={set('contactEmail')} autoComplete="off" />}
        </Field>
        <Field
          label="Oczekiwania finansowe (zł / mies.)"
          hint={app.salaryExpectation != null ? `Podane firmie: ${formatMoney(app.salaryExpectation)}` : 'Kwota podana firmie w rozmowie lub formularzu.'}
        >
          {(id, describedBy) => (
            <input
              id={id}
              aria-describedby={describedBy}
              type="number"
              min={0}
              step={100}
              inputMode="numeric"
              value={draft.salaryExpectation}
              onChange={set('salaryExpectation')}
            />
          )}
        </Field>
        <Field label="Kanał aplikacji" hint="Np. pracuj.pl, LinkedIn, polecenie, strona firmy.">
          {(id, describedBy) => (
            <input id={id} aria-describedby={describedBy} type="text" value={draft.source} onChange={set('source')} />
          )}
        </Field>
        <Field label="Data wysłania">
          {(id) => <input id={id} type="date" value={draft.appliedAt} onChange={set('appliedAt')} />}
        </Field>
        <Field label="Notatki">
          {(id) => <textarea id={id} rows={4} value={draft.notes} onChange={set('notes')} />}
        </Field>
        {error && <ErrorBox message={error} />}
        <button type="submit" className="btn btn-primary btn-small" disabled={busy}>
          Zapisz
        </button>
        {app.contactEmail && (
          <a className="btn btn-small btn-link" style={{ marginLeft: '0.75rem' }} href={`mailto:${app.contactEmail}`}>
            Napisz e-mail<span className="sr-only"> do {app.contactName ?? app.contactEmail}</span>
          </a>
        )}
      </form>
    </Card>
  );
}
