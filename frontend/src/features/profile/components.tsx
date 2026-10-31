import { useRef, type KeyboardEvent, type ReactNode } from 'react';

/** Kopiowanie do schowka z zapasową ścieżką dla przeglądarek bez Clipboard API (np. http bez TLS). */
export async function copyText(text: string): Promise<boolean> {
  try {
    if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(text);
      return true;
    }
  } catch {
    // przejdź do ścieżki zapasowej
  }
  try {
    const area = document.createElement('textarea');
    area.value = text;
    area.setAttribute('readonly', '');
    area.style.position = 'fixed';
    area.style.opacity = '0';
    document.body.appendChild(area);
    area.select();
    const ok = document.execCommand('copy');
    document.body.removeChild(area);
    return ok;
  } catch {
    return false;
  }
}

/** Przycisk „Kopiuj” — wynik ogłaszany przez `onResult` (np. w LiveMessage rodzica). */
export function CopyButton({
  text, label = 'Kopiuj', ariaLabel, onResult,
}: {
  text: string;
  label?: string;
  ariaLabel?: string;
  onResult: (message: string) => void;
}) {
  return (
    <button
      type="button"
      className="btn btn-small"
      aria-label={ariaLabel}
      onClick={async () => {
        const ok = await copyText(text);
        onResult(ok ? `Skopiowano: ${ariaLabel ?? label}` : 'Nie udało się skopiować — zaznacz tekst i skopiuj go ręcznie.');
      }}
    >
      {label}
    </button>
  );
}

export interface TabDef<T extends string> {
  id: T;
  label: ReactNode;
}

/**
 * Zakładki zgodne ze wzorcem ARIA: strzałki/Home/End przełączają, aktywna zakładka ma tabIndex 0,
 * a panel jest powiązany przez aria-controls/aria-labelledby.
 */
export function Tabs<T extends string>({
  idBase, label, tabs, active, onChange, renderPanel,
}: {
  idBase: string;
  label: string;
  tabs: TabDef<T>[];
  active: T;
  onChange: (id: T) => void;
  renderPanel: (id: T) => ReactNode;
}) {
  const refs = useRef<Record<string, HTMLButtonElement | null>>({});
  const tabId = (id: T) => `${idBase}-tab-${id}`;
  const panelId = (id: T) => `${idBase}-panel-${id}`;

  function onKeyDown(e: KeyboardEvent<HTMLDivElement>) {
    const index = tabs.findIndex((t) => t.id === active);
    let next = -1;
    if (e.key === 'ArrowRight') next = (index + 1) % tabs.length;
    else if (e.key === 'ArrowLeft') next = (index - 1 + tabs.length) % tabs.length;
    else if (e.key === 'Home') next = 0;
    else if (e.key === 'End') next = tabs.length - 1;
    if (next < 0) return;
    e.preventDefault();
    const id = tabs[next].id;
    onChange(id);
    refs.current[id]?.focus();
  }

  return (
    <>
      <div className="tabs" role="tablist" aria-label={label} onKeyDown={onKeyDown}>
        {tabs.map((t) => (
          <button
            key={t.id}
            ref={(el) => {
              refs.current[t.id] = el;
            }}
            type="button"
            role="tab"
            id={tabId(t.id)}
            aria-selected={t.id === active}
            aria-controls={panelId(t.id)}
            tabIndex={t.id === active ? 0 : -1}
            onClick={() => onChange(t.id)}
          >
            {t.label}
          </button>
        ))}
      </div>
      {tabs.map((t) => (
        <div
          key={t.id}
          role="tabpanel"
          id={panelId(t.id)}
          aria-labelledby={tabId(t.id)}
          hidden={t.id !== active}
          tabIndex={0}
          className="profile-tabpanel"
        >
          {t.id === active ? renderPanel(t.id) : null}
        </div>
      ))}
    </>
  );
}
