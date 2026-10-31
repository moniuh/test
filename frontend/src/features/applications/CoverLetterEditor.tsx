import { useState, type ReactNode } from 'react';
import { Alert, ErrorBox, Field, LiveMessage, SourceBadge } from '../../components/ui';
import { applicationsApi } from './api';
import { CopyButton } from './CopyButton';
import { useAction } from './useAction';
import type { CoverLetterResult, LetterLanguage, LetterTone } from './types';

/**
 * List motywacyjny oparty na dowodach (#42): generowanie (AI albo szablon offline z cytatów CV),
 * edycja, kopiowanie. Zapis należy do rodzica (ApplyPanel — przy tworzeniu aplikacji, szczegóły — osobny przycisk).
 */
export function CoverLetterEditor({
  offerId, cvId, value, onChange, actions,
}: {
  offerId: number;
  cvId: number | null;
  value: string;
  onChange: (text: string) => void;
  actions?: ReactNode;
}) {
  const [tone, setTone] = useState<LetterTone>('formalny');
  const [language, setLanguage] = useState<LetterLanguage>('pl');
  const [result, setResult] = useState<CoverLetterResult>();
  const [message, setMessage] = useState<string>();
  const { busy, error, run } = useAction();

  async function generate() {
    if (value.trim() && !window.confirm('Zastąpić obecną treść listu nowo wygenerowaną?')) return;
    setMessage(undefined);
    const r = await run(() => applicationsApi.coverLetter(offerId, cvId, tone, language));
    if (!r) return;
    setResult(r);
    onChange(r.text);
    setMessage(
      r.source === 'ai'
        ? 'Wygenerowano list z pomocą AI — przeczytaj go i popraw przed wysłaniem.'
        : 'Przygotowano list z szablonu na podstawie cytatów z CV.',
    );
  }

  return (
    <div className="stack applications-letter" style={{ gap: '0.5rem' }}>
      <div className="row" style={{ alignItems: 'flex-end' }}>
        <Field label="Ton">
          {(id) => (
            <select id={id} value={tone} onChange={(e) => setTone(e.target.value as LetterTone)}>
              <option value="formalny">Formalny</option>
              <option value="swobodny">Swobodny</option>
            </select>
          )}
        </Field>
        <Field label="Język">
          {(id) => (
            <select id={id} value={language} onChange={(e) => setLanguage(e.target.value as LetterLanguage)}>
              <option value="pl">Polski</option>
              <option value="en">Angielski</option>
            </select>
          )}
        </Field>
        <div className="field">
          <button type="button" className="btn" onClick={generate} disabled={busy} aria-busy={busy}>
            {busy ? 'Generowanie…' : 'Wygeneruj list'}
          </button>
        </div>
      </div>
      {error && <ErrorBox message={error} />}
      {result && (
        <div className="row small">
          <SourceBadge source={result.source} />
          <span className="muted">
            {result.source === 'ai' ? 'Napisane przez AI na zamaskowanych danych' : 'Złożone z szablonu, bez AI'}
            {result.cvName ? ` · CV „${result.cvName}”` : ''}
          </span>
        </div>
      )}
      {result?.notice && <Alert tone="info">{result.notice}</Alert>}
      {result && result.evidence.length > 0 && (
        <details>
          <summary className="label">Dowody z CV ({result.evidence.length})</summary>
          <ul className="applications-evidence">
            {result.evidence.map((e, i) => (
              <li key={`${i}-${e.requirement}`}>
                <strong>{e.requirement}</strong>
                <span className="muted small">
                  {' · '}
                  {e.mustHave ? 'obowiązkowe' : 'mile widziane'}
                  {e.partial && ' · spełnione częściowo'}
                </span>
                <blockquote className="evidence">
                  <span className="sr-only">Cytat z CV: </span>„{e.quote}”
                </blockquote>
              </li>
            ))}
          </ul>
        </details>
      )}
      {result && result.evidence.length === 0 && (
        <p className="small muted">Nie znaleziono w CV cytatów pasujących do wymagań — list jest ogólny.</p>
      )}
      <Field label="Treść listu motywacyjnego" hint="Tekst możesz dowolnie zmieniać — AI tylko podpowiada, decyzja należy do Ciebie.">
        {(id, describedBy) => (
          <textarea id={id} aria-describedby={describedBy} value={value} rows={12} onChange={(e) => onChange(e.target.value)} />
        )}
      </Field>
      <div className="actions">
        <CopyButton text={value} label="Kopiuj list" onDone={setMessage} />
        {actions}
      </div>
      <LiveMessage message={message} />
    </div>
  );
}
