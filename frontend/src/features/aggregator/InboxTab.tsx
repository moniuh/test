import { useState } from 'react';
import { Link } from 'react-router-dom';
import { errorMessage } from '../../api/client';
import { CONTRACT, SENIORITY, WORK_MODE, formatSalary, todayIso } from '../../api/labels';
import { useAsync } from '../../hooks/useAsync';
import { Badge, EmptyState, ErrorBox, Field, Loading, ScoreBadge } from '../../components/ui';
import { aggregatorApi } from './api';
import { SignalsWarning } from './SignalsWarning';
import { comparableNote, countLabel, relativeDay } from './logic';
import type { InboxItem, SourceView } from './types';

type SourceFilter = '' | 'MANUAL' | `${number}`;

/** Skrzynka nowych ofert (pomysł #48): nieprzejrzane, szybkie akcje, sortowanie wg dopasowania i świeżości. */
export function InboxTab({
  sources, version, announce, onCountsChanged,
}: {
  sources: SourceView[];
  version: number;
  announce: (message: string) => void;
  onCountsChanged: () => void;
}) {
  const [onlyUnseen, setOnlyUnseen] = useState(false);
  const [sourceFilter, setSourceFilter] = useState<SourceFilter>('');
  const [minScore, setMinScore] = useState('');
  const [sort, setSort] = useState<'match' | 'fresh'>('match');
  const [busy, setBusy] = useState<number | null>(null);
  const [actionError, setActionError] = useState<string>();

  const sourceId = sourceFilter && sourceFilter !== 'MANUAL' ? Number(sourceFilter) : null;
  const inbox = useAsync(
    () => aggregatorApi.inbox({
      onlyUnseen,
      sourceId,
      origin: sourceFilter === 'MANUAL' ? 'MANUAL' : null,
      minScore: minScore ? Number(minScore) : null,
      sort,
    }),
    [onlyUnseen, sourceFilter, minScore, sort, version],
  );
  const items = inbox.data?.items ?? [];

  function focusAfterRemoval(index: number) {
    const next = items[index + 1] ?? items[index - 1];
    requestAnimationFrame(() => {
      const el = next ? document.getElementById(`aggregator-offer-${next.offerId}`) : document.getElementById('aggregator-inbox-heading');
      el?.focus();
    });
  }

  function removeItem(item: InboxItem, unseen?: number) {
    const index = items.findIndex((i) => i.offerId === item.offerId);
    inbox.setData(inbox.data && {
      ...inbox.data,
      items: items.filter((i) => i.offerId !== item.offerId),
      total: inbox.data.total - 1,
      unseen: unseen ?? inbox.data.unseen - (item.seen || item.origin !== 'SOURCE' ? 0 : 1),
    });
    focusAfterRemoval(index);
  }

  async function decide(item: InboxItem, status: 'SAVED' | 'REJECTED') {
    setBusy(item.offerId);
    setActionError(undefined);
    try {
      await aggregatorApi.setOfferStatus(item.offerId, status);
      if (!item.seen && item.origin === 'SOURCE') await aggregatorApi.markSeen([item.offerId]);
      removeItem(item);
      announce(status === 'SAVED'
        ? `Zapisano ofertę „${item.title}” — znajdziesz ją w zakładce Oferty.`
        : `Odrzucono ofertę „${item.title}”.`);
      onCountsChanged();
    } catch (e) {
      setActionError(errorMessage(e));
    } finally {
      setBusy(null);
    }
  }

  async function markSeen(item: InboxItem) {
    setBusy(item.offerId);
    setActionError(undefined);
    try {
      const r = await aggregatorApi.markSeen([item.offerId]);
      if (onlyUnseen) {
        removeItem(item, r.unseen);
      } else {
        inbox.setData(inbox.data && {
          ...inbox.data,
          unseen: r.unseen,
          items: items.map((i) => (i.offerId === item.offerId ? { ...i, seen: true } : i)),
        });
      }
      announce(`Oznaczono „${item.title}” jako przejrzaną. Nieprzejrzanych: ${r.unseen}.`);
      onCountsChanged();
    } catch (e) {
      setActionError(errorMessage(e));
    } finally {
      setBusy(null);
    }
  }

  async function markAll() {
    setActionError(undefined);
    try {
      const r = await aggregatorApi.markAllSeen(sourceId);
      announce(`Oznaczono ${countLabel(r.marked, 'ofertę', 'oferty', 'ofert')} jako przejrzane.`);
      inbox.reload();
      onCountsChanged();
    } catch (e) {
      setActionError(errorMessage(e));
    }
  }

  const today = todayIso();

  return (
    <>
      <h2 id="aggregator-inbox-heading" tabIndex={-1}>Skrzynka nowych ofert</h2>
      <p className="muted small">
        Oferty w stanie „Nowa” ze wszystkich źródeł i dodane ręcznie, ułożone według dopasowania do aktywnego CV.
        Zapisz, odrzuć albo od razu dopasuj CV.
      </p>
      <div className="aggregator-toolbar">
        <label className="aggregator-check">
          <input type="checkbox" checked={onlyUnseen} onChange={(e) => setOnlyUnseen(e.target.checked)} />
          Tylko nieprzejrzane
        </label>
        <Field label="Źródło">
          {(id) => (
            <select id={id} value={sourceFilter} onChange={(e) => setSourceFilter(e.target.value as SourceFilter)}>
              <option value="">Wszystkie</option>
              <option value="MANUAL">Dodane ręcznie</option>
              {sources.map((s) => (
                <option key={s.id} value={String(s.id)}>{s.name}</option>
              ))}
            </select>
          )}
        </Field>
        <Field label="Minimalne dopasowanie">
          {(id) => (
            <select id={id} value={minScore} onChange={(e) => setMinScore(e.target.value)}>
              <option value="">Dowolne</option>
              <option value="25">od 25%</option>
              <option value="50">od 50%</option>
              <option value="75">od 75%</option>
            </select>
          )}
        </Field>
        <Field label="Sortuj">
          {(id) => (
            <select id={id} value={sort} onChange={(e) => setSort(e.target.value as 'match' | 'fresh')}>
              <option value="match">Najlepiej dopasowane</option>
              <option value="fresh">Najnowsze</option>
            </select>
          )}
        </Field>
        <span className="aggregator-spacer" />
        <button type="button" className="btn" onClick={markAll} disabled={!inbox.data || inbox.data.unseen === 0}>
          Oznacz wszystkie jako przejrzane
        </button>
      </div>

      {actionError && <ErrorBox message={actionError} />}
      {inbox.error && <ErrorBox message={inbox.error} onRetry={inbox.reload} />}
      {inbox.loading && !inbox.data && <Loading label="Ładowanie skrzynki…" />}
      {inbox.data && (
        <p className="small muted" aria-live="polite">
          {countLabel(items.length, 'oferta', 'oferty', 'ofert')} w widoku · nieprzejrzanych ze źródeł: {inbox.data.unseen}
        </p>
      )}
      {inbox.data && items.length === 0 && (
        <EmptyState title={onlyUnseen ? 'Wszystko przejrzane' : 'Brak nowych ofert'}>
          <p>Dodaj źródło w zakładce „Źródła” albo pobierz oferty przyciskiem „Pobierz wszystkie źródła”.</p>
        </EmptyState>
      )}
      <ul className="aggregator-list">
        {items.map((item) => (
          <li key={item.offerId}>
            <InboxCard
              item={item}
              today={today}
              busy={busy === item.offerId}
              onSave={() => decide(item, 'SAVED')}
              onReject={() => decide(item, 'REJECTED')}
              onSeen={() => markSeen(item)}
            />
          </li>
        ))}
      </ul>
    </>
  );
}

function InboxCard({
  item, today, busy, onSave, onReject, onSeen,
}: {
  item: InboxItem;
  today: string;
  busy: boolean;
  onSave: () => void;
  onReject: () => void;
  onSeen: () => void;
}) {
  const meta = [
    item.company,
    item.location,
    item.workMode && WORK_MODE[item.workMode],
    item.seniority && SENIORITY[item.seniority],
    item.contractTypes.length ? item.contractTypes.map((c) => CONTRACT[c]).join(' / ') : null,
  ].filter(Boolean).join(' · ');
  const fetched = item.origin === 'SOURCE' ? `pobrano ${relativeDay(item.fetchedAt, today)}` : `dodano ${relativeDay(item.fetchedAt, today)}`;
  return (
    <article className={`card compact aggregator-offer${item.seen ? '' : ' is-unseen'}`} aria-busy={busy}>
      <div className="aggregator-head">
        <div>
          <h3>
            <Link id={`aggregator-offer-${item.offerId}`} to={`/oferty/${item.offerId}`}>{item.title}</Link>
          </h3>
          <p className="aggregator-meta">{meta || 'Brak szczegółów'}</p>
          <p className="aggregator-meta">
            {formatSalary(item)}
            {comparableNote(item) && ` (${comparableNote(item)})`}
          </p>
        </div>
        <div className="row">
          {!item.seen && <Badge tone="info">Nowa</Badge>}
          {item.origin === 'MANUAL' && <Badge>dodana ręcznie</Badge>}
          <span className="aggregator-score">
            Dopasowanie <ScoreBadge score={item.score ?? undefined} />
          </span>
        </div>
      </div>
      <p className="aggregator-meta">
        {item.url ? (
          <a href={item.url} target="_blank" rel="noopener noreferrer">
            Źródło: {item.sourceName ?? 'oryginał'}
            <span className="sr-only"> (oryginalne ogłoszenie, otwiera się w nowej karcie)</span>
          </a>
        ) : (
          <>Źródło: {item.sourceName ?? '—'}</>
        )}
        {' · '}
        {fetched}
        {item.expiresAt && ` · ważna do ${item.expiresAt}`}
        {item.mustHaveTotal ? ` · wymagania obowiązkowe: ${item.mustHaveMet}/${item.mustHaveTotal}` : ''}
        {item.missingSkills.length > 0 && ` · brakuje: ${item.missingSkills.join(', ')}`}
      </p>
      {item.snippet && <p className="aggregator-snippet">{item.snippet}</p>}
      <SignalsWarning signals={item.signals} />
      <div className="aggregator-actions">
        <button type="button" className="btn btn-small btn-success" onClick={onSave} disabled={busy}>
          Zapisz<span className="sr-only"> ofertę {item.title}</span>
        </button>
        <button type="button" className="btn btn-small btn-danger" onClick={onReject} disabled={busy}>
          Odrzuć<span className="sr-only"> ofertę {item.title}</span>
        </button>
        <Link className="btn btn-small btn-primary" to={`/dopasuj-cv/${item.offerId}`}>
          Dopasuj CV<span className="sr-only"> do oferty {item.title}</span>
        </Link>
        <Link className="btn btn-small" to={`/oferty/${item.offerId}`}>
          Szczegóły<span className="sr-only"> oferty {item.title}</span>
        </Link>
        {!item.seen && (
          <button type="button" className="btn btn-small btn-link" onClick={onSeen} disabled={busy}>
            Oznacz jako przejrzaną<span className="sr-only"> — {item.title}</span>
          </button>
        )}
      </div>
    </article>
  );
}
