import { useRef, useState, type FormEvent } from 'react';
import { errorMessage } from '../../api/client';
import { CONTRACT, WORK_MODE, formatSalary } from '../../api/labels';
import { Alert, Badge, ErrorBox, Field, Loading } from '../../components/ui';
import { aggregatorApi } from './api';
import { SignalsWarning } from './SignalsWarning';
import {
  SOURCE_TYPE_HINT, SOURCE_TYPE_LABEL, countLabel, joinList, parseList, validateSource, type SourceFormErrors,
} from './logic';
import type { Preview, SourceRequest, SourceType, SourceView } from './types';

const TYPES: SourceType[] = ['RSS', 'ATOM', 'JSONLD_PAGE', 'DEMO'];

/** Formularz źródła z filtrami (pomysł #60) i testem podglądu bez zapisu (pomysł #15). */
export function SourceForm({
  initial, demoFiles, onSaved, onCancel,
}: {
  initial: SourceView | SourceRequest;
  demoFiles: string[];
  onSaved: (saved: SourceView) => void;
  onCancel: () => void;
}) {
  const editingId = 'id' in initial ? initial.id : null;
  const [name, setName] = useState(initial.name);
  const [type, setType] = useState<SourceType>(initial.type);
  const [url, setUrl] = useState(initial.url);
  const [enabled, setEnabled] = useState(initial.enabled);
  const [include, setInclude] = useState(joinList(initial.includeKeywords));
  const [exclude, setExclude] = useState(joinList(initial.excludeKeywords));
  const [locations, setLocations] = useState(joinList(initial.locations));
  const [minSalary, setMinSalary] = useState(initial.minSalary == null ? '' : String(initial.minSalary));
  const [errors, setErrors] = useState<SourceFormErrors>({});
  const [serverError, setServerError] = useState<string>();
  const [saving, setSaving] = useState(false);
  const [testing, setTesting] = useState(false);
  const [preview, setPreview] = useState<Preview>();
  const nameRef = useRef<HTMLInputElement>(null);
  const urlRef = useRef<HTMLInputElement>(null);
  const minSalaryRef = useRef<HTMLInputElement>(null);

  function request(): SourceRequest {
    return {
      name: name.trim(),
      type,
      url: url.trim(),
      enabled,
      includeKeywords: parseList(include),
      excludeKeywords: parseList(exclude),
      locations: parseList(locations),
      minSalary: minSalary.trim() === '' ? null : Number(minSalary),
    };
  }

  async function submit(e: FormEvent) {
    e.preventDefault();
    const req = request();
    const errs = validateSource(req);
    setErrors(errs);
    setServerError(undefined);
    if (Object.keys(errs).length) {
      (errs.name ? nameRef : errs.url ? urlRef : minSalaryRef).current?.focus();
      return;
    }
    setSaving(true);
    try {
      const saved = editingId != null
        ? await aggregatorApi.updateSource(editingId, req)
        : await aggregatorApi.createSource(req);
      onSaved(saved);
    } catch (err) {
      setServerError(errorMessage(err));
    } finally {
      setSaving(false);
    }
  }

  async function test() {
    const req = request();
    const errs = validateSource({ ...req, name: req.name || 'podgląd' });
    setErrors(errs);
    setServerError(undefined);
    setPreview(undefined);
    if (Object.keys(errs).length) return;
    setTesting(true);
    try {
      setPreview(await aggregatorApi.testSource(req));
    } catch (err) {
      setServerError(errorMessage(err));
    } finally {
      setTesting(false);
    }
  }

  const isDemo = type === 'DEMO';

  return (
    <form className="aggregator-form" onSubmit={submit} noValidate aria-describedby="aggregator-form-note">
      <p id="aggregator-form-note" className="small muted">
        Oferty z nowego źródła trafią do skrzynki po pierwszym pobraniu („Pobierz teraz” albo harmonogram).
      </p>
      <div className="grid-2">
        <Field label="Nazwa źródła" error={errors.name}>
          {(id, describedBy) => (
            <input
              id={id}
              ref={nameRef}
              aria-describedby={describedBy}
              aria-invalid={!!errors.name}
              value={name}
              onChange={(e) => setName(e.target.value)}
              autoComplete="off"
            />
          )}
        </Field>
        <Field label="Typ źródła" hint={SOURCE_TYPE_HINT[type]}>
          {(id, describedBy) => (
            <select id={id} aria-describedby={describedBy} value={type} onChange={(e) => setType(e.target.value as SourceType)}>
              {TYPES.map((t) => (
                <option key={t} value={t}>{SOURCE_TYPE_LABEL[t]}</option>
              ))}
            </select>
          )}
        </Field>
      </div>
      <Field
        label={isDemo ? 'Plik demonstracyjny' : 'Adres URL'}
        hint={isDemo ? `Dostępne pliki: ${demoFiles.join(', ')}` : 'Tylko publiczne adresy http(s) — adresy lokalne i prywatne są blokowane.'}
        error={errors.url}
      >
        {(id, describedBy) => (
          <input
            id={id}
            ref={urlRef}
            type={isDemo ? 'text' : 'url'}
            inputMode={isDemo ? 'text' : 'url'}
            list={isDemo ? 'aggregator-demo-files' : undefined}
            aria-describedby={describedBy}
            aria-invalid={!!errors.url}
            value={url}
            onChange={(e) => setUrl(e.target.value)}
            placeholder={isDemo ? 'demo-feed.xml' : 'https://…'}
            autoComplete="off"
          />
        )}
      </Field>
      {isDemo && (
        <datalist id="aggregator-demo-files">
          {demoFiles.map((f) => <option key={f} value={f} />)}
        </datalist>
      )}
      <label className="aggregator-check" style={{ display: 'inline-flex', gap: '0.4rem', marginBottom: '0.75rem' }}>
        <input type="checkbox" checked={enabled} onChange={(e) => setEnabled(e.target.checked)} />
        Źródło włączone (pobierane przez harmonogram i „Pobierz wszystkie”)
      </label>

      <fieldset>
        <legend>Filtry stosowane przed zapisem oferty</legend>
        <p className="small muted" style={{ marginTop: 0 }}>
          Słowa porównujemy bez wielkości liter i polskich znaków, jako całe słowa lub frazy („java*” = prefiks).
          Brak danych (np. widełek) nie odrzuca oferty.
        </p>
        <div className="grid-2">
          <Field label="Musi zawierać jedno z (słowa kluczowe)" hint="Rozdziel przecinkami, np. java, kotlin, spring boot">
            {(id, describedBy) => (
              <input id={id} aria-describedby={describedBy} value={include} onChange={(e) => setInclude(e.target.value)} />
            )}
          </Field>
          <Field label="Wyklucz, jeśli zawiera" hint="Np. senior, praca zmianowa">
            {(id, describedBy) => (
              <input id={id} aria-describedby={describedBy} value={exclude} onChange={(e) => setExclude(e.target.value)} />
            )}
          </Field>
          <Field label="Lokalizacje" hint="Np. Warszawa, zdalnie — „zdalnie” przepuszcza oferty zdalne">
            {(id, describedBy) => (
              <input id={id} aria-describedby={describedBy} value={locations} onChange={(e) => setLocations(e.target.value)} />
            )}
          </Field>
          <Field
            label="Minimalne wynagrodzenie (zł / mies., równoważnik UoP brutto)"
            hint="Porównujemy z górną granicą widełek po normalizacji (B2B, stawki godzinowe, waluty)."
            error={errors.minSalary}
          >
            {(id, describedBy) => (
              <input
                id={id}
                ref={minSalaryRef}
                type="number"
                min={0}
                step={500}
                inputMode="numeric"
                aria-describedby={describedBy}
                aria-invalid={!!errors.minSalary}
                value={minSalary}
                onChange={(e) => setMinSalary(e.target.value)}
              />
            )}
          </Field>
        </div>
      </fieldset>

      {serverError && <ErrorBox message={serverError} />}
      <div className="form-actions">
        <button type="submit" className="btn btn-primary" disabled={saving}>
          {saving ? 'Zapisywanie…' : editingId != null ? 'Zapisz zmiany' : 'Dodaj źródło'}
        </button>
        <button type="button" className="btn" onClick={test} disabled={testing}>
          {testing ? 'Testowanie…' : 'Testuj (podgląd 5 pozycji)'}
        </button>
        <button type="button" className="btn btn-link" onClick={onCancel}>Anuluj</button>
      </div>

      <div aria-live="polite" className="aggregator-preview">
        {testing && <Loading label="Pobieram podgląd źródła…" />}
        {preview && <PreviewList preview={preview} />}
      </div>
    </form>
  );
}

function PreviewList({ preview }: { preview: Preview }) {
  return (
    <section aria-labelledby="aggregator-preview-heading">
      <h4 id="aggregator-preview-heading">
        Podgląd: {countLabel(preview.total, 'pozycja', 'pozycje', 'pozycji')} w źródle
        {preview.title ? ` „${preview.title}”` : ''} ({preview.format})
      </h4>
      {preview.warnings.map((w) => <Alert key={w} tone="warning">{w}</Alert>)}
      {preview.items.length === 0 && <p className="muted">Źródło nie zwróciło żadnych ofert.</p>}
      <ul className="list-plain">
        {preview.items.map((it) => (
          <li key={it.externalId}>
            <div className="spread">
              <strong>{it.title}</strong>
              <span className="badges">
                {it.accepted ? <Badge tone="success">przejdzie filtry</Badge> : <Badge tone="warning">odfiltrowana</Badge>}
                {it.duplicateOf && <Badge>już w bazie</Badge>}
                <Badge>{countLabel(it.requirementsCount, 'wymaganie', 'wymagania', 'wymagań')}</Badge>
              </span>
            </div>
            <p className="small muted" style={{ margin: 0 }}>
              {[it.company, it.location, it.workMode && WORK_MODE[it.workMode],
                it.contractTypes.map((c) => CONTRACT[c]).join(' / ') || null,
                formatSalary(it)].filter(Boolean).join(' · ')}
            </p>
            {!it.accepted && it.filterReason && <p className="small" style={{ margin: 0 }}>Powód: {it.filterReason}</p>}
            {it.duplicateOf && <p className="small" style={{ margin: 0 }}>Duplikat: {it.duplicateOf}</p>}
            {it.analysisNotes.length > 0 && (
              <p className="small muted" style={{ margin: 0 }}>Analiza: {it.analysisNotes.join('; ')}</p>
            )}
            <SignalsWarning signals={it.signals} />
          </li>
        ))}
      </ul>
    </section>
  );
}
