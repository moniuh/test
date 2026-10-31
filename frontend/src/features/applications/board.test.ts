import { describe, expect, it } from 'vitest';
import type { ApplicationStatus, ApplicationView } from '../../api/types';
import { dropIndex, groupByStatus, indexInColumn, isClosed, moveAnnouncement, moveCard } from './board';

function app(id: number, status: ApplicationStatus, boardPosition: number): ApplicationView {
  return {
    id, offerId: id, offerTitle: `Oferta ${id}`, company: null, location: null, offerSource: null, status,
    appliedAt: null, source: null, cvVariantId: null, cvVariantName: null, cvSnapshot: null, coverLetter: null,
    nextFollowUpAt: null, lastContactAt: null, firstResponseAt: null, boardPosition, contactName: null,
    contactEmail: null, salaryExpectation: null, notes: null, createdAt: '', updatedAt: '',
  };
}

const APPS = [app(1, 'PLANNED', 0), app(2, 'APPLIED', 1), app(3, 'APPLIED', 0), app(4, 'APPLIED', 2), app(5, 'REJECTED', 0)];

const ids = (list: ApplicationView[]) => list.map((a) => a.id);

describe('groupByStatus', () => {
  it('sorts each column by board position and keeps empty columns', () => {
    const g = groupByStatus(APPS);
    expect(ids(g.APPLIED)).toEqual([3, 2, 4]);
    expect(g.OFFER).toEqual([]);
    expect(indexInColumn(APPS, 4)).toBe(2);
    expect(indexInColumn(APPS, 99)).toBe(-1);
  });
});

describe('moveCard', () => {
  it('moves a card to another column at the given position and renumbers both columns', () => {
    const moved = moveCard(APPS, 1, 'APPLIED', 1);
    const g = groupByStatus(moved);
    expect(ids(g.APPLIED)).toEqual([3, 1, 2, 4]);
    expect(g.APPLIED.map((a) => a.boardPosition)).toEqual([0, 1, 2, 3]);
    expect(g.PLANNED).toEqual([]);
    expect(moved.find((a) => a.id === 1)?.status).toBe('APPLIED');
  });

  it('reorders within the same column', () => {
    expect(ids(groupByStatus(moveCard(APPS, 3, 'APPLIED', 2)).APPLIED)).toEqual([2, 4, 3]);
    expect(ids(groupByStatus(moveCard(APPS, 4, 'APPLIED', 0)).APPLIED)).toEqual([4, 3, 2]);
  });

  it('clamps the position and appends on null', () => {
    expect(ids(groupByStatus(moveCard(APPS, 5, 'APPLIED', 99)).APPLIED)).toEqual([3, 2, 4, 5]);
    expect(ids(groupByStatus(moveCard(APPS, 5, 'APPLIED', null)).APPLIED)).toEqual([3, 2, 4, 5]);
    expect(ids(groupByStatus(moveCard(APPS, 5, 'APPLIED', -3)).APPLIED)).toEqual([5, 3, 2, 4]);
  });

  it('does not mutate input and ignores unknown cards', () => {
    const copy = JSON.stringify(APPS);
    moveCard(APPS, 1, 'OFFER', 0);
    expect(JSON.stringify(APPS)).toBe(copy);
    expect(moveCard(APPS, 42, 'OFFER', 0)).toBe(APPS);
  });
});

describe('helpers', () => {
  it('computes the drop index from card midpoints', () => {
    expect(dropIndex([100, 200, 300], 50)).toBe(0);
    expect(dropIndex([100, 200, 300], 250)).toBe(2);
    expect(dropIndex([100, 200, 300], 999)).toBe(3);
    expect(dropIndex([], 10)).toBe(0);
  });

  it('announces moves and knows closed statuses', () => {
    expect(moveAnnouncement('Java Developer', 'INTERVIEW', 0, 3)).toBe(
      'Przeniesiono „Java Developer” do kolumny Rozmowy — pozycja 1 z 3.',
    );
    expect(isClosed('REJECTED')).toBe(true);
    expect(isClosed('OFFER')).toBe(false);
  });
});
