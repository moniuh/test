import { useState, type FormEvent } from 'react';
import { api, errorMessage } from '../api/client';
import type { ContractType, JobOfferDraft, Requirement, SalaryPeriod, Seniority, WorkMode } from '../api/types';
import { CONTRACT, SALARY_PERIOD, SENIORITY, WORK_MODE } from '../api/labels';
import { ErrorBox, Field } from './ui';

const lines = (s: string) => s.split('\n').map((x) => x.trim()).filter(Boolean);
const csv = (s: string) => s.split(',').map((x) => x.trim()).filter(Boolean);

/**
 * Formularz oferty. Aby wypełnić go nowymi danymi (np. z parsera), zmień `key` komponentu —
 * stan formularza jest inicjowany z `initial` tylko przy montowaniu.
 */
export function OfferForm({
  initial, onSubmit, submitLabel = 'Zapisz', onCancel,
}: {
  initial?: Partial<JobOfferDraft>;
  onSubmit: (draft: JobOfferDraft) => Promise<void>;
  submitLabel?: string;
  onCancel?: () => void;
}) {
  const [title, setTitle] = useState(initial?.title ?? '');
  const [company, setCompany] = useState(initial?.company ?? '');
  const [location, setLocation] = useState(initial?.location ?? '');
  const [url, setUrl] = useState(initial?.url ?? '');
  const [source, setSource] = useState(initial?.source ?? '');
  const [workMode, setWorkMode] = useState<WorkMode | ''>(initial?.workMode ?? '');
  const [contracts, setContracts] = useState<ContractType[]>(initial?.contractTypes ?? []);
  const [seniority, setSeniority] = useState<Seniority | ''>(initial?.seniority ?? '');
  const [salaryMin, setSalaryMin] = useState(initial?.salaryMin?.toString() ?? '');
  const [salaryMax, setSalaryMax] = useState(initial?.salaryMax?.toString() ?? '');
  const [currency, setCurrency] = useState(initial?.salaryCurrency ?? 'PLN');
  const [period, setPeriod] = useState<SalaryPeriod | ''>(initial?.salaryPeriod ?? 'MONTH');
  const [gross, setGross] = useState(initial?.salaryGross == null ? '' : String(initial.salaryGross));
  const [description, setDescription] = useState(initial?.description ?? '');
  const [requirements, setRequirements] = useState<Requirement[]>(initial?.requirements ?? []);
  const [benefits, setBenefits] = useState((initial?.benefits ?? []).join('\n'));
  const [tags, setTags] = useState((initial?.tags ?? []).join(', '));
  const [notes, setNotes] = useState(initial?.notes ?? '');
  const [expiresAt, setExpiresAt] = useState(initial?.expiresAt ?? '');
  const [error, setError] = useState<string>();
  const [saving, setSaving] = useState(false);
  const [extracting, setExtracting] = useState(false);

  const toggleContract = (c: ContractType) =>
    setContracts((cs) => (cs.includes(c) ? cs.filter((x) => x !== c) : [...cs, c]));

  const updateReq = (i: number, patch: Partial<Requirement>) =>
    setRequirements((rs) => rs.map((r, idx) => (idx === i ? { ...r, ...patch } : r)));

  async function extract() {
    setExtracting(true);
    setError(undefined);
    try {
      const res = await api.post<{ requirements: Requirement[] }>('/api/offers/requirements/extract', { text: description });
      setRequirements(res.requirements);
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setExtracting(false);
    }
  }

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (!title.trim()) {
      setError('Podaj tytuł stanowiska');
      return;
    }
    setSaving(true);
    setError(undefined);
    try {
      await onSubmit({
        ...initial,
        title: title.trim(),
        company: company || null,
        location: location || null,
        url: url || null,
        source: source || null,
        workMode: workMode || null,
        contractTypes: contracts,
        seniority: seniority || null,
        salaryMin: salaryMin ? Number(salaryMin) : null,
        salaryMax: salaryMax ? Number(salaryMax) : null,
        salaryCurrency: currency,
        salaryPeriod: period || null,
        salaryGross: gross === '' ? null : gross === 'true',
        description: description || null,
        requirements: requirements.filter((r) => r.text.trim()),
        benefits: lines(benefits),
        tags: csv(tags),
        notes: notes || null,
        expiresAt: expiresAt || null,
      });
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setSaving(false);
    }
  }

  return (
    <form onSubmit={submit} noValidate>
      {error && <ErrorBox message={error} />}
      <div className="grid-2">
        <Field label="Stanowisko *">
          {(id) => <input id={id} value={title} onChange={(e) => setTitle(e.target.value)} required aria-required="true" />}
        </Field>
        <Field label="Firma">{(id) => <input id={id} value={company} onChange={(e) => setCompany(e.target.value)} />}</Field>
        <Field label="Lokalizacja">{(id) => <input id={id} value={location} onChange={(e) => setLocation(e.target.value)} />}</Field>
        <Field label="Źródło" hint="np. pracuj.pl, justjoin.it, LinkedIn, polecenie">
          {(id, d) => <input id={id} aria-describedby={d} value={source} onChange={(e) => setSource(e.target.value)} />}
        </Field>
        <Field label="Adres ogłoszenia (URL)">
          {(id) => <input id={id} type="url" value={url} onChange={(e) => setUrl(e.target.value)} />}
        </Field>
        <Field label="Ważne do">
          {(id) => <input id={id} type="date" value={expiresAt} onChange={(e) => setExpiresAt(e.target.value)} />}
        </Field>
        <Field label="Tryb pracy">
          {(id) => (
            <select id={id} value={workMode} onChange={(e) => setWorkMode(e.target.value as WorkMode | '')}>
              <option value="">— nie podano —</option>
              {Object.entries(WORK_MODE).map(([k, v]) => (
                <option key={k} value={k}>{v}</option>
              ))}
            </select>
          )}
        </Field>
        <Field label="Poziom">
          {(id) => (
            <select id={id} value={seniority} onChange={(e) => setSeniority(e.target.value as Seniority | '')}>
              <option value="">— nie podano —</option>
              {Object.entries(SENIORITY).map(([k, v]) => (
                <option key={k} value={k}>{v}</option>
              ))}
            </select>
          )}
        </Field>
      </div>

      <fieldset>
        <legend>Forma zatrudnienia</legend>
        <div className="checks">
          {(Object.keys(CONTRACT) as ContractType[]).map((c) => (
            <label key={c}>
              <input type="checkbox" checked={contracts.includes(c)} onChange={() => toggleContract(c)} /> {CONTRACT[c]}
            </label>
          ))}
        </div>
      </fieldset>

      <fieldset>
        <legend>Wynagrodzenie</legend>
        <div className="grid-4">
          <Field label="Od">{(id) => <input id={id} type="number" min={0} value={salaryMin} onChange={(e) => setSalaryMin(e.target.value)} />}</Field>
          <Field label="Do">{(id) => <input id={id} type="number" min={0} value={salaryMax} onChange={(e) => setSalaryMax(e.target.value)} />}</Field>
          <Field label="Waluta">
            {(id) => (
              <select id={id} value={currency} onChange={(e) => setCurrency(e.target.value)}>
                {['PLN', 'EUR', 'USD', 'GBP', 'CHF'].map((c) => (
                  <option key={c}>{c}</option>
                ))}
              </select>
            )}
          </Field>
          <Field label="Okres">
            {(id) => (
              <select id={id} value={period} onChange={(e) => setPeriod(e.target.value as SalaryPeriod)}>
                {(Object.keys(SALARY_PERIOD) as SalaryPeriod[]).map((p) => (
                  <option key={p} value={p}>
                    {{ HOUR: 'za godzinę', DAY: 'za dzień', MONTH: 'miesięcznie', YEAR: 'rocznie' }[p]}
                  </option>
                ))}
              </select>
            )}
          </Field>
        </div>
        <Field label="Kwota" hint="Dla B2B kwota netto + VAT z faktury traktowana jest jak „przed podatkami”">
          {(id, d) => (
            <select id={id} aria-describedby={d} value={gross} onChange={(e) => setGross(e.target.value)}>
              <option value="">nie podano</option>
              <option value="true">brutto / netto + VAT (B2B)</option>
              <option value="false">netto „na rękę”</option>
            </select>
          )}
        </Field>
      </fieldset>

      <Field label="Treść ogłoszenia">
        {(id) => <textarea id={id} className="tall" value={description} onChange={(e) => setDescription(e.target.value)} />}
      </Field>

      <fieldset>
        <legend>Wymagania</legend>
        <div className="row" style={{ marginBottom: '0.5rem' }}>
          <button type="button" className="btn btn-small" onClick={extract} disabled={!description.trim() || extracting}>
            {extracting ? 'Wyodrębniam…' : 'Wyodrębnij z treści'}
          </button>
          <button
            type="button"
            className="btn btn-small"
            onClick={() => setRequirements((rs) => [...rs, { text: '', mustHave: true, minYears: null }])}
          >
            Dodaj wymaganie
          </button>
        </div>
        {requirements.length === 0 && <p className="muted small">Brak wymagań — zostaną wyodrębnione z treści przy zapisie.</p>}
        <ul className="list-plain">
          {requirements.map((r, i) => (
            <li key={i} className="row">
              <label className="sr-only" htmlFor={`req-${i}`}>Wymaganie {i + 1}</label>
              <input id={`req-${i}`} style={{ flex: '1 1 260px', width: 'auto' }} value={r.text} onChange={(e) => updateReq(i, { text: e.target.value })} />
              <label className="row" style={{ gap: '0.3rem' }}>
                <input type="checkbox" checked={r.mustHave} onChange={(e) => updateReq(i, { mustHave: e.target.checked })} />
                obowiązkowe
              </label>
              <label className="row" style={{ gap: '0.3rem' }}>
                lata
                <input
                  type="number"
                  min={0}
                  style={{ width: '5rem' }}
                  value={r.minYears ?? ''}
                  onChange={(e) => updateReq(i, { minYears: e.target.value ? Number(e.target.value) : null })}
                />
              </label>
              <button
                type="button"
                className="btn btn-small btn-danger"
                onClick={() => setRequirements((rs) => rs.filter((_, idx) => idx !== i))}
                aria-label={`Usuń wymaganie: ${r.text || i + 1}`}
              >
                Usuń
              </button>
            </li>
          ))}
        </ul>
      </fieldset>

      <div className="grid-2">
        <Field label="Benefity" hint="Jeden w linii">
          {(id, d) => <textarea id={id} aria-describedby={d} value={benefits} onChange={(e) => setBenefits(e.target.value)} style={{ minHeight: 90 }} />}
        </Field>
        <div>
          <Field label="Tagi" hint="Oddzielone przecinkami">
            {(id, d) => <input id={id} aria-describedby={d} value={tags} onChange={(e) => setTags(e.target.value)} />}
          </Field>
          <Field label="Notatki">
            {(id) => <textarea id={id} value={notes} onChange={(e) => setNotes(e.target.value)} style={{ minHeight: 60 }} />}
          </Field>
        </div>
      </div>

      <div className="form-actions">
        <button type="submit" className="btn btn-primary" disabled={saving}>
          {saving ? 'Zapisywanie…' : submitLabel}
        </button>
        {onCancel && (
          <button type="button" className="btn" onClick={onCancel}>
            Anuluj
          </button>
        )}
      </div>
    </form>
  );
}
