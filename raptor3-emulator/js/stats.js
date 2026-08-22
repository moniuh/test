// Trwałe statystyki stanowiska testowego (localStorage).

const KEY = 'raptor3.stats.v1';

const EMPTY = { tests: 0, burnTime: 0, prop: 0, aborts: 0 };

export function loadStats() {
  try {
    const raw = localStorage.getItem(KEY);
    if (raw) return { ...EMPTY, ...JSON.parse(raw) };
  } catch { /* prywatny tryb / brak zgody — statystyki tylko na sesję */ }
  return { ...EMPTY };
}

export function saveStats(stats) {
  try {
    localStorage.setItem(KEY, JSON.stringify(stats));
  } catch { /* jak wyżej */ }
}

// wywoływane po zakończeniu testu (powrót do GOTOWY)
export function recordTest(stats, sim) {
  stats.tests += 1;
  stats.burnTime += sim.burnTime;
  stats.prop += sim.propUsed;
  if (sim.alarm) stats.aborts += 1;
  saveStats(stats);
}
