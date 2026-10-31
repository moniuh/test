import { copyText } from './clipboard';

/** Przycisk kopiowania do schowka; wynik przekazuje do `onDone` (do ogłoszenia przez aria-live). */
export function CopyButton({
  text, label = 'Kopiuj', onDone,
}: {
  text: string;
  label?: string;
  onDone: (message: string) => void;
}) {
  async function copy() {
    const ok = await copyText(text);
    onDone(ok ? 'Skopiowano do schowka.' : 'Nie udało się skopiować — zaznacz tekst i użyj Ctrl+C.');
  }
  return (
    <button type="button" className="btn btn-small" onClick={copy} disabled={!text.trim()}>
      {label}
    </button>
  );
}
