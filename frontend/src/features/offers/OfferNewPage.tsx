import { useEffect, useRef, useState, type KeyboardEvent, type RefObject } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import type { JobOfferDraft } from '../../api/types';
import { useDocumentTitle } from '../../hooks/useDocumentTitle';
import { Alert, Badge, Card, ErrorBox, Field, LiveMessage, PageHeader, SourceBadge } from '../../components/ui';
import { OfferForm } from '../../components/OfferForm';
import { offersApi } from './api';
import {
  FIELD_STATUS, ORIGIN_LABEL, confidenceSummary, describeImportError, fieldStatus, formatPercent, isHttpUrl,
  likelyDuplicates, nextTabIndex, parseAnnouncement, reviewLabels, toFormInitial, type ImportErrorView,
} from './logic';
import type { DuplicateCandidate, ParsedOffer } from './types';
import './offers.css';

type Tab = 'paste' | 'url' | 'manual';

const TABS: { id: Tab; label: string }[] = [
  { id: 'paste', label: 'Wklej treść' },
  { id: 'url', label: 'Z adresu URL' },
  { id: 'manual', label: 'Ręcznie' },
];

/**
 * Dodawanie oferty: wklejenie treści (#13) lub import z adresu (#14) → rozpoznane pola z oznaczeniem
 * „do sprawdzenia” → formularz → ostrzeżenie o możliwym duplikacie (#16) → zapis.
 */
export function OfferNewPage() {
  useDocumentTitle('Nowa oferta');
  const navigate = useNavigate();
  const [tab, setTab] = useState<Tab>('paste');
  const [text, setText] = useState('');
  const [pasteUrl, setPasteUrl] = useState('');
  const [importUrl, setImportUrl] = useState('');
  const [busy, setBusy] = useState(false);
  const [pasteError, setPasteError] = useState<string>();
  const [importError, setImportError] = useState<ImportErrorView>();
  const [parsed, setParsed] = useState<ParsedOffer>();
  const [formKey, setFormKey] = useState(0);
  const [announce, setAnnounce] = useState<string>();
  const [duplicates, setDuplicates] = useState<DuplicateCandidate[]>();
  const tabRefs = useRef<(HTMLButtonElement | null)[]>([]);
  const resultTitleRef = useRef<HTMLHeadingElement>(null);
  const dupRef = useRef<HTMLElement>(null);
  const formWrapRef = useRef<HTMLDivElement>(null);
  const skipDuplicateCheck = useRef(false);

  useEffect(() => {
    if (parsed) resultTitleRef.current?.focus();
  }, [parsed]);

  useEffect(() => {
    if (duplicates?.length) dupRef.current?.focus();
  }, [duplicates]);

  function selectTab(t: Tab, focus = false) {
    setTab(t);
    setDuplicates(undefined);
    if (focus) tabRefs.current[TABS.findIndex((x) => x.id === t)]?.focus();
  }

  function onTabKey(e: KeyboardEvent<HTMLButtonElement>, index: number) {
    const next = nextTabIndex(index, e.key, TABS.length);
    if (next == null) return;
    e.preventDefault();
    selectTab(TABS[next].id, true);
  }

  function accept(result: ParsedOffer) {
    setParsed(result);
    setFormKey((k) => k + 1);
    setDuplicates(undefined);
    setAnnounce(parseAnnouncement(result));
  }

  async function runParse() {
    setPasteError(undefined);
    if (!text.trim()) {
      setPasteError('Wklej treść ogłoszenia');
      return;
    }
    if (pasteUrl.trim() && !isHttpUrl(pasteUrl)) {
      setPasteError('Adres ogłoszenia powinien zaczynać się od http:// lub https://');
      return;
    }
    setBusy(true);
    setAnnounce('Rozpoznaję pola…');
    try {
      accept(await offersApi.parse(text, pasteUrl));
    } catch (e) {
      setPasteError(describeImportError(e).message);
      setAnnounce(undefined);
    } finally {
      setBusy(false);
    }
  }

  async function runImport() {
    setImportError(undefined);
    if (!isHttpUrl(importUrl)) {
      setImportError({
        message: 'Podaj pełny adres ogłoszenia zaczynający się od http:// lub https://',
        hint: null,
        suggestPaste: false,
      });
      return;
    }
    setBusy(true);
    setAnnounce('Pobieram ogłoszenie…');
    try {
      accept(await offersApi.importUrl(importUrl));
    } catch (e) {
      setImportError(describeImportError(e));
      setAnnounce(undefined);
    } finally {
      setBusy(false);
    }
  }

  function switchToPaste() {
    setPasteUrl(importUrl.trim());
    setImportError(undefined);
    selectTab('paste', true);
  }

  /** Zapis z formularza: najpierw sprawdzenie duplikatów (ostrzeżenie, nie blokada). */
  async function create(draft: JobOfferDraft) {
    if (!skipDuplicateCheck.current) {
      let likely: DuplicateCandidate[] = [];
      try {
        likely = likelyDuplicates(
          await offersApi.checkDuplicates({
            title: draft.title,
            company: draft.company ?? null,
            description: draft.description ?? null,
            url: draft.url ?? null,
          }),
        );
      } catch {
        // ostrzeżenie jest pomocnicze — gdy sprawdzenie się nie uda, zapisujemy normalnie
      }
      if (likely.length > 0) {
        setDuplicates(likely);
        setAnnounce('Uwaga: podobna oferta jest już zapisana.');
        return;
      }
    }
    skipDuplicateCheck.current = false;
    const saved = await offersApi.create(draft);
    navigate(`/oferty/${saved.id}`);
  }

  /** „Zapisz mimo to” — wysyła formularz z aktualnymi danymi, z pominięciem ostrzeżenia. */
  function saveAnyway() {
    skipDuplicateCheck.current = true;
    setDuplicates(undefined);
    formWrapRef.current?.querySelector('form')?.requestSubmit();
  }

  const form = (initial?: Partial<JobOfferDraft>, key?: string) => (
    <div ref={formWrapRef}>
      <OfferForm key={key} initial={initial} onSubmit={create} submitLabel="Dodaj ofertę" onCancel={() => navigate('/oferty')} />
      {duplicates && duplicates.length > 0 && (
        <section
          ref={dupRef}
          tabIndex={-1}
          className="card offers-dup"
          aria-labelledby="offers-dup-title"
          style={{ marginTop: '1rem' }}
        >
          <h2 id="offers-dup-title">
            <span aria-hidden="true">⚠ </span>Możliwy duplikat
          </h2>
          <p>Podobna oferta jest już zapisana. Sprawdź ją, zanim dodasz to ogłoszenie ponownie:</p>
          <ul className="list-plain">
            {duplicates.map((c) => (
              <li key={c.offerId}>
                <div className="spread">
                  <span>
                    <Link to={`/oferty/${c.offerId}`}>{c.title}</Link>
                    <span className="small muted"> {[c.company, c.source].filter(Boolean).join(' · ')}</span>
                  </span>
                  <Badge tone="warning">podobieństwo {formatPercent(c.score)}</Badge>
                </div>
                {c.reasons.length > 0 && <p className="small muted" style={{ margin: '0.2rem 0 0' }}>{c.reasons.join(', ')}</p>}
              </li>
            ))}
          </ul>
          <div className="form-actions">
            <button type="button" className="btn btn-primary" onClick={saveAnyway}>
              Zapisz mimo to
            </button>
            <button type="button" className="btn" onClick={() => setDuplicates(undefined)}>
              Wróć do edycji
            </button>
          </div>
        </section>
      )}
    </div>
  );

  return (
    <>
      <PageHeader
        title="Nowa oferta"
        subtitle="Wklej treść ogłoszenia lub podaj jego adres — rozpoznamy stanowisko, firmę, widełki, tryb pracy i wymagania."
      />
      <div className="tabs" role="tablist" aria-label="Sposób dodania oferty">
        {TABS.map((t, i) => (
          <button
            key={t.id}
            ref={(el) => {
              tabRefs.current[i] = el;
            }}
            type="button"
            role="tab"
            id={`offers-tab-${t.id}`}
            aria-selected={tab === t.id}
            aria-controls={`offers-panel-${t.id}`}
            tabIndex={tab === t.id ? 0 : -1}
            onClick={() => selectTab(t.id)}
            onKeyDown={(e) => onTabKey(e, i)}
          >
            {t.label}
          </button>
        ))}
      </div>

      {tab === 'paste' && (
        <div role="tabpanel" id="offers-panel-paste" aria-labelledby="offers-tab-paste" className="offers-tabpanel">
          <Card>
            {pasteError && <ErrorBox message={pasteError} />}
            <Field label="Treść ogłoszenia" hint="Skopiuj całe ogłoszenie — z tytułem, firmą, widełkami i wymaganiami.">
              {(id, d) => (
                <textarea id={id} aria-describedby={d} className="tall" value={text} onChange={(e) => setText(e.target.value)} />
              )}
            </Field>
            <Field label="Adres ogłoszenia (opcjonalnie)" hint="Posłuży do zapisania źródła, np. pracuj.pl. Strona nie jest pobierana.">
              {(id, d) => (
                <input id={id} aria-describedby={d} type="url" value={pasteUrl} onChange={(e) => setPasteUrl(e.target.value)} />
              )}
            </Field>
            <button type="button" className="btn btn-primary" onClick={runParse} disabled={busy}>
              {busy ? 'Rozpoznaję…' : 'Rozpoznaj pola'}
            </button>
          </Card>
        </div>
      )}

      {tab === 'url' && (
        <div role="tabpanel" id="offers-panel-url" aria-labelledby="offers-tab-url" className="offers-tabpanel">
          <Card>
            {importError && (
              <div className="alert alert-danger" role="alert">
                <div>
                  <strong>{importError.message}</strong>
                  {importError.hint && <p className="small" style={{ margin: '0.25rem 0 0' }}>{importError.hint}</p>}
                </div>
                {importError.suggestPaste && (
                  <button type="button" className="btn btn-small" onClick={switchToPaste}>
                    Wklej treść ręcznie
                  </button>
                )}
              </div>
            )}
            <form
              onSubmit={(e) => {
                e.preventDefault();
                void runImport();
              }}
              noValidate
            >
              <Field
                label="Adres ogłoszenia (URL)"
                hint="Pobierzemy tylko tę stronę, na Twoje żądanie (http/https). Niektóre portale blokują automatyczne pobieranie — wtedy wklej treść."
              >
                {(id, d) => (
                  <input
                    id={id}
                    aria-describedby={d}
                    type="url"
                    inputMode="url"
                    placeholder="https://"
                    value={importUrl}
                    onChange={(e) => setImportUrl(e.target.value)}
                  />
                )}
              </Field>
              <button type="submit" className="btn btn-primary" disabled={busy}>
                {busy ? 'Pobieram…' : 'Pobierz i rozpoznaj'}
              </button>
            </form>
          </Card>
        </div>
      )}

      {tab === 'manual' && (
        <div role="tabpanel" id="offers-panel-manual" aria-labelledby="offers-tab-manual" className="offers-tabpanel">
          <Card>{form(undefined, 'manual')}</Card>
        </div>
      )}

      <LiveMessage message={announce} />

      {tab !== 'manual' && parsed && (
        <div className="stack">
          <ParsedFields parsed={parsed} titleRef={resultTitleRef} />
          <Card title="Sprawdź i zapisz">
            {reviewLabels(parsed).length > 0 && (
              <Alert tone="warning">
                <span>
                  <strong>Do sprawdzenia:</strong> {reviewLabels(parsed).join(', ')} — rozpoznane z niską pewnością albo
                  nieznalezione.
                </span>
              </Alert>
            )}
            {form(toFormInitial(parsed.draft), `parsed-${formKey}`)}
          </Card>
        </div>
      )}
    </>
  );
}

function ParsedFields({ parsed, titleRef }: { parsed: ParsedOffer; titleRef: RefObject<HTMLHeadingElement | null> }) {
  return (
    <section className="card" aria-labelledby="offers-result-title">
      <div className="card-header">
        <h2 id="offers-result-title" ref={titleRef} tabIndex={-1} className="offers-result-title">
          Rozpoznane pola
        </h2>
        <SourceBadge source="offline" />
      </div>
      <p>
        {confidenceSummary(parsed)}. Źródło: {ORIGIN_LABEL[parsed.origin]}.
      </p>
      {parsed.warnings.map((w) => (
        <Alert key={w} tone="info">
          {w}
        </Alert>
      ))}
      <div className="table-wrap">
        <table className="offers-fields">
          <caption className="sr-only">Pola rozpoznane w ogłoszeniu, z pewnością i fragmentem źródła</caption>
          <thead>
            <tr>
              <th scope="col">Pole</th>
              <th scope="col">Wartość</th>
              <th scope="col">Stan</th>
              <th scope="col">Skąd</th>
            </tr>
          </thead>
          <tbody>
            {parsed.fields.map((f) => {
              const status = fieldStatus(f);
              const info = FIELD_STATUS[status];
              return (
                <tr key={f.field} className={status === 'ok' ? undefined : 'offers-row-review'}>
                  <th scope="row">{f.label}</th>
                  <td>{f.value ?? '—'}</td>
                  <td>
                    <Badge tone={info.tone}>
                      <span aria-hidden="true">{info.icon}</span> {info.label}
                    </Badge>
                    {status !== 'missing' && <span className="small muted"> {formatPercent(f.confidence)}</span>}
                  </td>
                  <td className="small">
                    {f.method}
                    {f.evidence && (
                      <blockquote className="offers-quote neutral">
                        <span className="sr-only">Fragment ogłoszenia: </span>„{f.evidence}”
                      </blockquote>
                    )}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </section>
  );
}
