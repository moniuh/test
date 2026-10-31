/** Logika tablicy Kanban (#39) — czyste funkcje, testowane w board.test.ts. */
import type { ApplicationStatus, ApplicationView } from '../../api/types';
import { APPLICATION_STATUS, APPLICATION_STATUSES } from '../../api/labels';

/** Statusy zamknięte (kolumny, które można zwinąć) — zgodnie z ApplicationStatus.isClosed() w backendzie. */
export const CLOSED_STATUSES: ApplicationStatus[] = ['ACCEPTED', 'REJECTED', 'WITHDRAWN', 'NO_RESPONSE'];

export function isClosed(status: ApplicationStatus): boolean {
  return CLOSED_STATUSES.includes(status);
}

function byPosition(a: ApplicationView, b: ApplicationView): number {
  return (a.boardPosition ?? 0) - (b.boardPosition ?? 0) || a.id - b.id;
}

/** Karty pogrupowane w kolumny (każda kolumna posortowana wg pozycji, potem id). */
export function groupByStatus(apps: ApplicationView[]): Record<ApplicationStatus, ApplicationView[]> {
  const out = Object.fromEntries(APPLICATION_STATUSES.map((s) => [s, [] as ApplicationView[]])) as Record<
    ApplicationStatus,
    ApplicationView[]
  >;
  for (const a of apps) out[a.status]?.push(a);
  for (const s of APPLICATION_STATUSES) out[s].sort(byPosition);
  return out;
}

/** Indeks karty w jej kolumnie (−1, gdy nie ma). */
export function indexInColumn(apps: ApplicationView[], id: number): number {
  const card = apps.find((a) => a.id === id);
  if (!card) return -1;
  return groupByStatus(apps)[card.status].findIndex((a) => a.id === id);
}

/**
 * Przeniesienie karty (lokalnie, przed odpowiedzią serwera) — ta sama semantyka co POST /move:
 * `position` to indeks w kolumnie docelowej bez przenoszonej karty; null = koniec kolumny.
 */
export function moveCard(
  apps: ApplicationView[], id: number, status: ApplicationStatus, position: number | null,
): ApplicationView[] {
  const card = apps.find((a) => a.id === id);
  if (!card) return apps;
  const from = card.status;
  const target = apps.filter((a) => a.status === status && a.id !== id).sort(byPosition);
  const idx = position == null ? target.length : Math.max(0, Math.min(position, target.length));
  target.splice(idx, 0, { ...card, status });
  const updated = new Map<number, ApplicationView>();
  target.forEach((a, i) => updated.set(a.id, { ...a, boardPosition: i }));
  if (from !== status) {
    apps
      .filter((a) => a.status === from && a.id !== id)
      .sort(byPosition)
      .forEach((a, i) => updated.set(a.id, { ...a, boardPosition: i }));
  }
  return apps.map((a) => updated.get(a.id) ?? a);
}

/** Miejsce wstawienia przy upuszczeniu: liczba kart, których środek jest nad kursorem. */
export function dropIndex(midpoints: number[], y: number): number {
  return midpoints.filter((m) => m < y).length;
}

/** Komunikat dla czytników ekranu po przeniesieniu karty. */
export function moveAnnouncement(title: string, status: ApplicationStatus, index: number, total: number): string {
  return `Przeniesiono „${title}” do kolumny ${APPLICATION_STATUS[status]} — pozycja ${index + 1} z ${total}.`;
}
