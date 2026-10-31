import { useRef, useState, type KeyboardEvent } from 'react';
import { useSearchParams } from 'react-router-dom';
import { errorMessage } from '../../api/client';
import { useAsync } from '../../hooks/useAsync';
import { useDocumentTitle } from '../../hooks/useDocumentTitle';
import { LiveMessage, PageHeader } from '../../components/ui';
import { aggregatorApi } from './api';
import { InboxTab } from './InboxTab';
import { RunsTab } from './RunsTab';
import { SearchTab } from './SearchTab';
import { SourcesTab } from './SourcesTab';
import { formatRunsSummary, plural } from './logic';
import './aggregator.css';

type TabKey = 'skrzynka' | 'zrodla' | 'szukaj' | 'historia';
const TABS: TabKey[] = ['skrzynka', 'zrodla', 'szukaj', 'historia'];

/**
 * Agregator ofert: skrzynka nowych ofert, źródła (RSS/Atom, strony kariery), wyszukiwarka i historia pobrań
 * (pomysły #9, #15, #22, #44, #48, #49, #60, #61, #83). Zakładki wg wzorca WAI-ARIA (strzałki, Home/End).
 */
export function AggregatorPage() {
  useDocumentTitle('Agregator');
  const [params, setParams] = useSearchParams();
  const requested = params.get('widok') as TabKey | null;
  const tab: TabKey = requested && TABS.includes(requested) ? requested : 'skrzynka';
  const [message, setMessage] = useState<string>();
  const [version, setVersion] = useState(0);
  const [runningAll, setRunningAll] = useState(false);
  const tabRefs = useRef<Record<TabKey, HTMLButtonElement | null>>({ skrzynka: null, zrodla: null, szukaj: null, historia: null });

  const summary = useAsync(() => aggregatorApi.summary(), [version]);
  const sources = useAsync(() => aggregatorApi.sources(), [version]);
  const settings = useAsync(() => aggregatorApi.settings(), []);

  const announce = (m: string) => setMessage(m);
  const refresh = () => setVersion((v) => v + 1);

  function select(next: TabKey, focus = false) {
    const p = new URLSearchParams(params);
    if (next === 'skrzynka') p.delete('widok');
    else p.set('widok', next);
    setParams(p, { replace: true });
    if (focus) tabRefs.current[next]?.focus();
  }

  function onTabKey(e: KeyboardEvent<HTMLButtonElement>) {
    const i = TABS.indexOf(tab);
    let next: TabKey | null = null;
    if (e.key === 'ArrowRight') next = TABS[(i + 1) % TABS.length];
    else if (e.key === 'ArrowLeft') next = TABS[(i - 1 + TABS.length) % TABS.length];
    else if (e.key === 'Home') next = TABS[0];
    else if (e.key === 'End') next = TABS[TABS.length - 1];
    if (next) {
      e.preventDefault();
      select(next, true);
    }
  }

  async function runAll() {
    setRunningAll(true);
    announce('Pobieram oferty ze wszystkich włączonych źródeł…');
    try {
      const runs = await aggregatorApi.runAll();
      announce(formatRunsSummary(runs));
      refresh();
    } catch (e) {
      announce(`Nie udało się pobrać ofert: ${errorMessage(e)}`);
    } finally {
      setRunningAll(false);
    }
  }

  const unseen = summary.data?.unseen;
  const labels: Record<TabKey, string> = {
    skrzynka: unseen == null ? 'Skrzynka' : `Skrzynka (${unseen} ${plural(unseen, 'nowa', 'nowe', 'nowych')})`,
    zrodla: summary.data ? `Źródła (${summary.data.sources})` : 'Źródła',
    szukaj: 'Szukaj',
    historia: 'Historia pobrań',
  };

  return (
    <>
      <PageHeader
        title="Agregator ofert"
        subtitle="Oferty z kanałów RSS/Atom i stron kariery firm w jednym miejscu — z analizą, filtrami, dopasowaniem do CV i ostrzeżeniami przed ukrytymi poleceniami dla AI."
        actions={
          <button type="button" className="btn btn-primary" onClick={runAll} disabled={runningAll} aria-busy={runningAll}>
            {runningAll ? 'Pobieranie…' : 'Pobierz wszystkie źródła'}
          </button>
        }
      />
      <LiveMessage message={message} />

      <div className="tabs" role="tablist" aria-label="Widoki agregatora">
        {TABS.map((t) => (
          <button
            key={t}
            ref={(el) => {
              tabRefs.current[t] = el;
            }}
            type="button"
            role="tab"
            id={`aggregator-tab-${t}`}
            aria-selected={tab === t}
            aria-controls={`aggregator-panel-${t}`}
            tabIndex={tab === t ? 0 : -1}
            onClick={() => select(t)}
            onKeyDown={onTabKey}
          >
            {labels[t]}
          </button>
        ))}
      </div>

      <div role="tabpanel" id={`aggregator-panel-${tab}`} aria-labelledby={`aggregator-tab-${tab}`} tabIndex={0}>
        {tab === 'skrzynka' && (
          <InboxTab sources={sources.data ?? []} version={version} announce={announce} onCountsChanged={summary.reload} />
        )}
        {tab === 'zrodla' && (
          <SourcesTab
            sources={sources.data}
            loading={sources.loading}
            error={sources.error}
            reload={sources.reload}
            settings={settings.data}
            announce={announce}
            onChanged={refresh}
          />
        )}
        {tab === 'szukaj' && <SearchTab announce={announce} />}
        {tab === 'historia' && (
          <RunsTab sources={sources.data ?? []} version={version} announce={announce} onChanged={refresh} />
        )}
      </div>
    </>
  );
}
