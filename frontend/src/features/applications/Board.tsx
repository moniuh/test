import { useEffect, useRef, useState, type DragEvent, type KeyboardEvent } from 'react';
import { Link } from 'react-router-dom';
import type { ApplicationStatus, ApplicationView } from '../../api/types';
import { APPLICATION_STATUS, APPLICATION_STATUSES, daysSince, formatDate, todayIso } from '../../api/labels';
import { Badge, LiveMessage } from '../../components/ui';
import { CLOSED_STATUSES, dropIndex, groupByStatus, isClosed, moveAnnouncement } from './board';
import { agoLabel, daysUntil, followUpLabel } from './format';
import { readPref, writePref } from './clipboard';

const COLLAPSE_KEY = 'radar.aplikacje.zamkniete';

interface BoardProps {
  apps: ApplicationView[];
  /** Przeniesienie karty; zwraca false, gdy się nie udało (komunikat pokazuje rodzic). */
  onMove: (id: number, status: ApplicationStatus, position: number) => Promise<boolean>;
}

/**
 * Tablica Kanban (#39): kolumny statusów, przeciąganie HTML5 oraz alternatywa klawiaturowa
 * (przycisk „Przenieś…” na każdej karcie). Każde przeniesienie jest ogłaszane przez aria-live.
 */
export function Board({ apps, onMove }: BoardProps) {
  const columns = groupByStatus(apps);
  const [collapsed, setCollapsed] = useState(
    () => readPref(COLLAPSE_KEY, ['zwiniete', 'rozwiniete'] as const, 'rozwiniete') === 'zwiniete',
  );
  const [dragId, setDragId] = useState<number | null>(null);
  const [drop, setDrop] = useState<{ status: ApplicationStatus; index: number } | null>(null);
  const [menuFor, setMenuFor] = useState<number | null>(null);
  const [message, setMessage] = useState<string>();
  const [focusId, setFocusId] = useState<number | null>(null);
  const lists = useRef<Partial<Record<ApplicationStatus, HTMLUListElement | null>>>({});

  // po przeniesieniu klawiaturą fokus wraca na przycisk „Przenieś…” karty (już w nowej kolumnie)
  useEffect(() => {
    if (focusId == null) return;
    document.querySelector<HTMLElement>(`[data-move-toggle="${focusId}"]`)?.focus();
    setFocusId(null);
  }, [focusId, apps]);

  const closedCount = CLOSED_STATUSES.reduce((n, s) => n + columns[s].length, 0);
  const visible = APPLICATION_STATUSES.filter((s) => !collapsed || !isClosed(s));

  function toggleCollapsed() {
    const next = !collapsed;
    setCollapsed(next);
    writePref(COLLAPSE_KEY, next ? 'zwiniete' : 'rozwiniete');
  }

  async function move(card: ApplicationView, status: ApplicationStatus, position: number, byKeyboard: boolean) {
    setMenuFor(null);
    const total = columns[status].filter((a) => a.id !== card.id).length + 1;
    const index = Math.max(0, Math.min(position, total - 1));
    const ok = await onMove(card.id, status, index);
    if (ok) {
      setMessage(moveAnnouncement(card.offerTitle, status, index, total));
      if (byKeyboard) setFocusId(card.id);
    } else if (byKeyboard) {
      setFocusId(card.id);
    }
  }

  function onDragStart(e: DragEvent<HTMLLIElement>, card: ApplicationView) {
    e.dataTransfer.setData('text/plain', String(card.id));
    e.dataTransfer.effectAllowed = 'move';
    setDragId(card.id);
    setMenuFor(null);
  }

  function onDragEnd() {
    setDragId(null);
    setDrop(null);
  }

  function onDragOver(e: DragEvent<HTMLElement>, status: ApplicationStatus) {
    if (dragId == null) return;
    e.preventDefault();
    e.dataTransfer.dropEffect = 'move';
    const list = lists.current[status];
    const mids = list
      ? Array.from(list.querySelectorAll<HTMLElement>('[data-card-id]'))
          .filter((el) => Number(el.dataset.cardId) !== dragId)
          .map((el) => {
            const r = el.getBoundingClientRect();
            return r.top + r.height / 2;
          })
      : [];
    const index = dropIndex(mids, e.clientY);
    if (drop?.status !== status || drop.index !== index) setDrop({ status, index });
  }

  function onDragLeave(e: DragEvent<HTMLElement>, status: ApplicationStatus) {
    if (e.currentTarget.contains(e.relatedTarget as Node | null)) return;
    setDrop((d) => (d?.status === status ? null : d));
  }

  async function onDrop(e: DragEvent<HTMLElement>, status: ApplicationStatus) {
    e.preventDefault();
    const id = dragId ?? Number(e.dataTransfer.getData('text/plain'));
    const index = drop?.status === status ? drop.index : columns[status].filter((a) => a.id !== id).length;
    setDragId(null);
    setDrop(null);
    const card = apps.find((a) => a.id === id);
    if (!card) return;
    if (card.status === status && columns[status].findIndex((a) => a.id === id) === index) return;
    await move(card, status, index, false);
  }

  return (
    <div className="stack" style={{ gap: '0.5rem' }}>
      <div className="row">
        <button type="button" className="btn btn-small" aria-pressed={collapsed} onClick={toggleCollapsed}>
          Zwiń zamknięte kolumny
        </button>
        <span className="small muted">
          Przeciągnij kartę do innej kolumny albo użyj przycisku „Przenieś…” na karcie.
        </span>
      </div>
      <LiveMessage message={message} />
      <div className="board applications-board">
        {visible.map((status) => {
          const cards = columns[status];
          const others = cards.filter((c) => c.id !== dragId);
          const target = drop?.status === status ? drop.index : null;
          const headingId = `aplikacje-kolumna-${status}`;
          return (
            <section
              key={status}
              className={`board-col${target != null ? ' drop-target' : ''}${isClosed(status) ? ' applications-col-closed' : ''}`}
              aria-labelledby={headingId}
              onDragOver={(e) => onDragOver(e, status)}
              onDragLeave={(e) => onDragLeave(e, status)}
              onDrop={(e) => onDrop(e, status)}
            >
              <h2 id={headingId}>
                <span>{APPLICATION_STATUS[status]}</span>
                <span className="applications-count">
                  <span className="sr-only">, liczba aplikacji: </span>
                  {cards.length}
                </span>
              </h2>
              <ul
                className="applications-cards"
                ref={(el) => {
                  lists.current[status] = el;
                }}
              >
                {cards.map((card, i) => (
                  <BoardCard
                    key={card.id}
                    card={card}
                    index={i}
                    total={cards.length}
                    dragging={card.id === dragId}
                    dropBefore={target != null && card.id !== dragId && others.indexOf(card) === target}
                    menuOpen={menuFor === card.id}
                    onToggleMenu={(open) => setMenuFor(open ? card.id : null)}
                    onDragStart={(e) => onDragStart(e, card)}
                    onDragEnd={onDragEnd}
                    onMove={(s, pos) => move(card, s, pos, true)}
                  />
                ))}
                {target != null && target >= others.length && <li aria-hidden="true" className="applications-drop-end" />}
              </ul>
              {cards.length === 0 && <p className="small muted">Pusto — upuść tu kartę albo użyj „Przenieś…”.</p>}
            </section>
          );
        })}
        {collapsed && (
          <section className="board-col applications-col-summary applications-col-closed" aria-labelledby="aplikacje-kolumna-zamkniete">
            <h2 id="aplikacje-kolumna-zamkniete">
              <span>Zamknięte</span>
              <span className="applications-count">
                <span className="sr-only">, liczba aplikacji: </span>
                {closedCount}
              </span>
            </h2>
            <ul>
              {CLOSED_STATUSES.map((s) => (
                <li key={s}>
                  {APPLICATION_STATUS[s]}: {columns[s].length}
                </li>
              ))}
            </ul>
            <button type="button" className="btn btn-small" onClick={toggleCollapsed}>
              Pokaż kolumny
            </button>
          </section>
        )}
      </div>
    </div>
  );
}

interface CardProps {
  card: ApplicationView;
  index: number;
  total: number;
  dragging: boolean;
  dropBefore: boolean;
  menuOpen: boolean;
  onToggleMenu: (open: boolean) => void;
  onDragStart: (e: DragEvent<HTMLLIElement>) => void;
  onDragEnd: () => void;
  onMove: (status: ApplicationStatus, position: number) => void;
}

function BoardCard({ card, index, total, dragging, dropBefore, menuOpen, onToggleMenu, onDragStart, onDragEnd, onMove }: CardProps) {
  const followDays = card.nextFollowUpAt && !isClosed(card.status) ? daysUntil(card.nextFollowUpAt, todayIso()) : null;
  const sent = agoLabel(daysSince(card.appliedAt));
  return (
    <li
      data-card-id={card.id}
      className={`board-card${dragging ? ' dragging' : ''}${dropBefore ? ' applications-drop-before' : ''}`}
      draggable
      onDragStart={onDragStart}
      onDragEnd={onDragEnd}
    >
      <Link className="applications-card-title" to={`/aplikacje/${card.id}`} draggable={false}>
        {card.offerTitle}
      </Link>
      <div className="small muted">{[card.company, card.location].filter(Boolean).join(' · ') || '—'}</div>
      <div className="applications-card-meta">
        {sent && card.status !== 'PLANNED' && <Badge title={formatDate(card.appliedAt)}>Wysłana {sent}</Badge>}
        {followDays != null && (
          <Badge tone={followDays <= 0 ? 'warning' : 'neutral'} title={`Termin follow-upu: ${formatDate(card.nextFollowUpAt)}`}>
            Follow-up: {followUpLabel(followDays)}
          </Badge>
        )}
      </div>
      <MoveMenu card={card} index={index} total={total} open={menuOpen} onToggle={onToggleMenu} onMove={onMove} />
    </li>
  );
}

function MoveMenu({
  card, index, total, open, onToggle, onMove,
}: {
  card: ApplicationView;
  index: number;
  total: number;
  open: boolean;
  onToggle: (open: boolean) => void;
  onMove: (status: ApplicationStatus, position: number) => void;
}) {
  const panelId = `aplikacje-przenies-${card.id}`;

  function onKeyDown(e: KeyboardEvent<HTMLDivElement>) {
    if (e.key === 'Escape' && open) {
      e.stopPropagation();
      onToggle(false);
      document.querySelector<HTMLElement>(`[data-move-toggle="${card.id}"]`)?.focus();
    }
  }

  return (
    <div className="applications-move applications-card-actions" onKeyDown={onKeyDown}>
      <button
        type="button"
        className="btn btn-small"
        aria-expanded={open}
        aria-controls={panelId}
        data-move-toggle={card.id}
        onClick={() => onToggle(!open)}
      >
        Przenieś…<span className="sr-only"> „{card.offerTitle}”</span>
      </button>
      {open && (
        <div id={panelId} className="applications-move-panel" role="group" aria-label={`Przenieś „${card.offerTitle}”`}>
          <p className="applications-move-label">W tej kolumnie</p>
          <ul>
            <li>
              <button type="button" className="btn btn-small" disabled={index <= 0} onClick={() => onMove(card.status, index - 1)}>
                <span aria-hidden="true">↑ </span>Wyżej
              </button>
            </li>
            <li>
              <button
                type="button"
                className="btn btn-small"
                disabled={index >= total - 1}
                onClick={() => onMove(card.status, index + 1)}
              >
                <span aria-hidden="true">↓ </span>Niżej
              </button>
            </li>
          </ul>
          <p className="applications-move-label">Do kolumny</p>
          <ul>
            {APPLICATION_STATUSES.filter((s) => s !== card.status).map((s) => (
              <li key={s}>
                <button type="button" className="btn btn-small" onClick={() => onMove(s, 0)}>
                  {APPLICATION_STATUS[s]}
                </button>
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}
