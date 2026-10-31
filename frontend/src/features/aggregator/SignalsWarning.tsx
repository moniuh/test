import type { InjectionSignal } from './types';
import { SEVERITY_LABEL, SIGNAL_KIND_LABEL, countLabel, maxSeverity } from './logic';

/**
 * Ostrzeżenie o możliwej próbie prompt injection (pomysł #83): rodzaj, waga, wyjaśnienie i dosłowny cytat.
 * Rozwijane natywnym <details> (obsługa klawiaturą i czytnikami ekranu bez dodatkowego kodu).
 */
export function SignalsWarning({ signals, open = false }: { signals: InjectionSignal[]; open?: boolean }) {
  const top = maxSeverity(signals);
  if (!top) return null;
  const cls = top === 'HIGH' ? '' : top === 'MEDIUM' ? ' is-medium' : ' is-low';
  return (
    <div className={`aggregator-signals${cls}`}>
      <details open={open}>
        <summary>
          <span aria-hidden="true">⚠ </span>
          <strong>Ostrzeżenie:</strong> treść może zawierać ukryte polecenia dla AI (
          {countLabel(signals.length, 'sygnał', 'sygnały', 'sygnałów')}, najwyższe ryzyko: {SEVERITY_LABEL[top]})
        </summary>
        <p className="small" style={{ fontWeight: 400, marginTop: '0.4rem' }}>
          Narzędzia AI w Radarze Pracy traktują treść ogłoszeń jako dane, nie polecenia — ale zachowaj ostrożność,
          zwłaszcza przy wklejaniu tej oferty do innych asystentów.
        </p>
        <ul>
          {signals.map((s, i) => (
            <li key={`${s.kind}-${i}`}>
              {SIGNAL_KIND_LABEL[s.kind]} (ryzyko: {SEVERITY_LABEL[s.severity]}) — {s.detail}
              <blockquote aria-label="Cytat ze źródła">{s.quote}</blockquote>
            </li>
          ))}
        </ul>
      </details>
    </div>
  );
}
