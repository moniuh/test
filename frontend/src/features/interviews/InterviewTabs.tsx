import { useRef, type KeyboardEvent } from 'react';

/**
 * Dostępne zakładki (WAI-ARIA tabs): `aria-selected`, `aria-controls`, strzałki/Home/End przełączają zakładkę.
 * Panel renderuje rodzic z `id={panelId(prefix, tab)}` i `aria-labelledby={tabId(prefix, tab)}`.
 */
export function InterviewTabs<T extends string>({
  tabs, labels, active, onChange, prefix, label,
}: {
  tabs: readonly T[];
  labels: Record<T, string>;
  active: T;
  onChange: (tab: T) => void;
  prefix: string;
  label: string;
}) {
  const refs = useRef<Record<string, HTMLButtonElement | null>>({});

  function onKeyDown(e: KeyboardEvent<HTMLButtonElement>, index: number) {
    let next = -1;
    if (e.key === 'ArrowRight') next = (index + 1) % tabs.length;
    else if (e.key === 'ArrowLeft') next = (index - 1 + tabs.length) % tabs.length;
    else if (e.key === 'Home') next = 0;
    else if (e.key === 'End') next = tabs.length - 1;
    if (next < 0) return;
    e.preventDefault();
    onChange(tabs[next]);
    refs.current[tabs[next]]?.focus();
  }

  return (
    <div className="tabs no-print" role="tablist" aria-label={label}>
      {tabs.map((t, i) => (
        <button
          key={t}
          ref={(el) => {
            refs.current[t] = el;
          }}
          type="button"
          role="tab"
          id={tabId(prefix, t)}
          aria-selected={t === active}
          aria-controls={panelId(prefix, t)}
          tabIndex={t === active ? 0 : -1}
          onClick={() => onChange(t)}
          onKeyDown={(e) => onKeyDown(e, i)}
        >
          {labels[t]}
        </button>
      ))}
    </div>
  );
}

export function tabId(prefix: string, tab: string): string {
  return `${prefix}-tab-${tab}`;
}

export function panelId(prefix: string, tab: string): string {
  return `${prefix}-panel-${tab}`;
}
