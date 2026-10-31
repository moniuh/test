import { useState } from 'react';
import { errorMessage } from '../../api/client';
import { formatDateTime, formatMoney } from '../../api/labels';
import { Badge, Card, EmptyState, ErrorBox, Loading } from '../../components/ui';
import { aggregatorApi } from './api';
import { SourceForm } from './SourceForm';
import {
  RUN_STATUS_LABEL, SOURCE_TYPE_LABEL, countLabel, emptySource, formatRunResult, joinList, runTone,
} from './logic';
import type { AggregatorSettings, SourceView } from './types';

/** Źródła ofert ze stanem, filtrami i akcjami (pomysły #9, #15, #44, #60). */
export function SourcesTab({
  sources, loading, error, reload, settings, announce, onChanged,
}: {
  sources: SourceView[] | undefined;
  loading: boolean;
  error: string | undefined;
  reload: () => void;
  settings: AggregatorSettings | undefined;
  announce: (message: string) => void;
  onChanged: () => void;
}) {
  const [editing, setEditing] = useState<SourceView | 'new' | null>(null);
  const [busy, setBusy] = useState<number | null>(null);
  const [actionError, setActionError] = useState<string>();

  async function run(s: SourceView) {
    setBusy(s.id);
    setActionError(undefined);
    announce(`Pobieram „${s.name}”…`);
    try {
      const r = await aggregatorApi.runSource(s.id);
      announce(formatRunResult(r));
      onChanged();
    } catch (e) {
      setActionError(errorMessage(e));
      announce(`Nie udało się pobrać „${s.name}”: ${errorMessage(e)}`);
    } finally {
      setBusy(null);
    }
  }

  async function toggle(s: SourceView) {
    setBusy(s.id);
    setActionError(undefined);
    try {
      await aggregatorApi.setEnabled(s.id, !s.enabled);
      announce(`Źródło „${s.name}” ${s.enabled ? 'wyłączone' : 'włączone'}.`);
      onChanged();
    } catch (e) {
      setActionError(errorMessage(e));
    } finally {
      setBusy(null);
    }
  }

  async function remove(s: SourceView) {
    if (!window.confirm(`Usunąć źródło „${s.name}”? Pobrane oferty zostaną w aplikacji.`)) return;
    setBusy(s.id);
    setActionError(undefined);
    try {
      await aggregatorApi.deleteSource(s.id);
      announce(`Usunięto źródło „${s.name}”. Pobrane oferty zostają.`);
      onChanged();
      requestAnimationFrame(() => document.getElementById('aggregator-sources-heading')?.focus());
    } catch (e) {
      setActionError(errorMessage(e));
    } finally {
      setBusy(null);
    }
  }

  function saved(s: SourceView) {
    announce(editing === 'new' ? `Dodano źródło „${s.name}”. Użyj „Pobierz teraz”, aby pobrać oferty.` : `Zapisano źródło „${s.name}”.`);
    setEditing(null);
    onChanged();
    requestAnimationFrame(() => document.getElementById(`aggregator-source-${s.id}`)?.focus());
  }

  return (
    <>
      <div className="spread">
        <h2 id="aggregator-sources-heading" tabIndex={-1}>Źródła ofert</h2>
        {editing === null && (
          <button type="button" className="btn btn-primary" onClick={() => setEditing('new')}>Dodaj źródło</button>
        )}
      </div>

      {settings && <Etiquette settings={settings} />}

      {editing !== null && (
        <Card title={editing === 'new' ? 'Nowe źródło' : `Edycja: ${editing.name}`} headingLevel={3}>
          <SourceForm
            key={editing === 'new' ? 'new' : editing.id}
            initial={editing === 'new' ? emptySource() : editing}
            demoFiles={settings?.demoFiles ?? []}
            onSaved={saved}
            onCancel={() => setEditing(null)}
          />
        </Card>
      )}

      {actionError && <ErrorBox message={actionError} />}
      {error && <ErrorBox message={error} onRetry={reload} />}
      {loading && !sources && <Loading label="Ładowanie źródeł…" />}
      {sources && sources.length === 0 && (
        <EmptyState title="Brak źródeł">
          <p>Dodaj kanał RSS/Atom (np. zapisane wyszukiwanie z portalu) albo stronę kariery firmy.</p>
        </EmptyState>
      )}
      <ul className="aggregator-list aggregator-sources">
        {sources?.map((s) => (
          <li key={s.id}>
            <article className="card compact" aria-busy={busy === s.id}>
              <div className="spread">
                <h3 id={`aggregator-source-${s.id}`} tabIndex={-1} style={{ margin: 0 }}>{s.name}</h3>
                <span className="badges">
                  <Badge>{SOURCE_TYPE_LABEL[s.type]}</Badge>
                  {s.enabled ? <Badge tone="success">Włączone</Badge> : <Badge>Wyłączone</Badge>}
                </span>
              </div>
              <p className="aggregator-source-url">{s.url}</p>
              <div className="aggregator-source-state">
                {s.lastRunAt ? (
                  <>
                    <span>Ostatnie pobranie: {formatDateTime(s.lastRunAt)}</span>
                    {s.lastStatus && <Badge tone={runTone(s.lastStatus)}>{RUN_STATUS_LABEL[s.lastStatus]}</Badge>}
                    {s.lastRun && s.lastStatus !== 'ERROR' && s.lastStatus !== 'BLOCKED' && (
                      <span className="muted">
                        {countLabel(s.lastRun.created, 'nowa', 'nowe', 'nowych')}, {countLabel(s.lastRun.duplicates, 'duplikat', 'duplikaty', 'duplikatów')},
                        {' '}{countLabel(s.lastRun.filteredOut, 'odfiltrowana', 'odfiltrowane', 'odfiltrowanych')}
                      </span>
                    )}
                  </>
                ) : (
                  <span className="muted">Jeszcze nie pobierano</span>
                )}
                <span className="muted">
                  Oferty z tego źródła: {s.offersCount} (nieprzejrzane: {s.unseenCount})
                </span>
              </div>
              {s.lastError && (
                <p className="aggregator-source-error">
                  <strong>Problem przy ostatnim pobraniu:</strong> {s.lastError}
                </p>
              )}
              <Filters source={s} />
              {s.usesNetwork && settings && (
                <p className="small muted" style={{ margin: '0.35rem 0 0' }}>
                  Pobieranie z sieci: sprawdzamy robots.txt, odczekujemy {settings.perHostDelayMs} ms między zapytaniami do
                  domeny i przedstawiamy się jako „{settings.userAgent}”. Każda oferta ma link do oryginału z nazwą źródła.
                </p>
              )}
              <div className="aggregator-actions">
                <button type="button" className="btn btn-small btn-primary" onClick={() => run(s)} disabled={busy === s.id}>
                  {busy === s.id ? 'Pobieranie…' : 'Pobierz teraz'}<span className="sr-only"> — {s.name}</span>
                </button>
                <button type="button" className="btn btn-small" onClick={() => toggle(s)} disabled={busy === s.id}>
                  {s.enabled ? 'Wyłącz' : 'Włącz'}<span className="sr-only"> źródło {s.name}</span>
                </button>
                <button type="button" className="btn btn-small" onClick={() => setEditing(s)} disabled={busy === s.id}>
                  Edytuj<span className="sr-only"> źródło {s.name}</span>
                </button>
                <button type="button" className="btn btn-small btn-danger" onClick={() => remove(s)} disabled={busy === s.id}>
                  Usuń<span className="sr-only"> źródło {s.name}</span>
                </button>
              </div>
            </article>
          </li>
        ))}
      </ul>
    </>
  );
}

function Filters({ source: s }: { source: SourceView }) {
  const parts: string[] = [];
  if (s.includeKeywords.length) parts.push(`musi zawierać: ${joinList(s.includeKeywords)}`);
  if (s.excludeKeywords.length) parts.push(`wyklucz: ${joinList(s.excludeKeywords)}`);
  if (s.locations.length) parts.push(`lokalizacje: ${joinList(s.locations)}`);
  if (s.minSalary != null) parts.push(`min. ${formatMoney(s.minSalary)} / mies. (UoP brutto)`);
  return (
    <p className="small" style={{ margin: '0.35rem 0 0' }}>
      <strong>Filtry:</strong> {parts.length ? parts.join(' · ') : 'brak — wszystkie oferty trafiają do skrzynki'}
    </p>
  );
}

function Etiquette({ settings }: { settings: AggregatorSettings }) {
  return (
    <details className="card compact aggregator-etiquette" style={{ marginBottom: '1rem' }}>
      <summary><strong>Etykieta pobierania</strong> — jak agregator korzysta z cudzych serwisów</summary>
      <ul>
        <li>Przed pobraniem sprawdzamy plik robots.txt serwisu i nie pobieramy zablokowanych ścieżek.</li>
        <li>Między zapytaniami do tej samej domeny odczekujemy co najmniej {settings.perHostDelayMs} ms.</li>
        <li>Przedstawiamy się nagłówkiem User-Agent „{settings.userAgent}”.</li>
        <li>
          Limit czasu zapytania: {settings.requestTimeoutSeconds} s, rozmiar odpowiedzi do {settings.maxResponseKb} KB,
          najwyżej {settings.maxItemsPerFetch} pozycji z jednego pobrania.
        </li>
        <li>Pobieramy tylko publiczne adresy http(s) — adresy lokalne i sieci prywatne są blokowane.</li>
        <li>W skrzynce każda oferta ma nazwę źródła i link do oryginalnego ogłoszenia (atrybucja).</li>
        <li>
          Harmonogram: {settings.schedulerEnabled ? `co ${settings.intervalMinutes} min` : 'wyłączony'}; oferty bez daty
          ważności archiwizujemy po {settings.archiveAfterDays} dniach od pobrania.
        </li>
      </ul>
    </details>
  );
}
