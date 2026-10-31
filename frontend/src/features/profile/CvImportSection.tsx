import { useRef, useState, type FormEvent } from 'react';
import { errorMessage } from '../../api/client';
import { Alert, ErrorBox, Field, LiveMessage } from '../../components/ui';
import { cvToolsApi } from './api';
import { ACCEPT_ATTRIBUTE, MAX_CV_BYTES, formatBytes, importSummary, validateCvFile } from './cvTools';
import type { CvImportResult } from './types';

const PREVIEW_CHARS = 1200;

/** #1 — import CV z PDF lub DOCX jako nowej wersji CV. */
export function CvImportSection({ onImported }: { onImported: () => void }) {
  const [file, setFile] = useState<File | null>(null);
  const [name, setName] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string>();
  const [message, setMessage] = useState<string>();
  const [result, setResult] = useState<CvImportResult>();
  const inputRef = useRef<HTMLInputElement>(null);
  const fileError = file ? validateCvFile(file.name, file.size) ?? undefined : undefined;

  async function submit(e: FormEvent) {
    e.preventDefault();
    setError(undefined);
    setMessage(undefined);
    if (!file) {
      setError('Wybierz plik CV (PDF lub DOCX).');
      inputRef.current?.focus();
      return;
    }
    if (fileError) {
      inputRef.current?.focus();
      return;
    }
    setBusy(true);
    setResult(undefined);
    try {
      const r = await cvToolsApi.importCv(file, name);
      setResult(r);
      setMessage(importSummary(r));
      setFile(null);
      setName('');
      if (inputRef.current) inputRef.current.value = '';
      onImported();
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  }

  return (
    <form onSubmit={submit} className="stack" aria-busy={busy}>
      <p className="muted small" style={{ margin: 0 }}>
        Wgraj CV w formacie PDF lub DOCX (do {formatBytes(MAX_CV_BYTES)}). Tekst zostanie odczytany na serwerze aplikacji
        i zapisany jako <strong>nowa wersja CV</strong> — obecne wersje się nie zmienią. Skany bez warstwy tekstowej (bez
        OCR) nie są obsługiwane.
      </p>
      <div className="grid-2">
        <div className="profile-file">
          <Field
            label="Plik CV (PDF lub DOCX)"
            hint={file ? `Wybrano: ${file.name} (${formatBytes(file.size)})` : 'Nie wybrano pliku'}
            error={fileError}
          >
            {(id, d) => (
              <input
                id={id}
                ref={inputRef}
                type="file"
                accept={ACCEPT_ATTRIBUTE}
                aria-describedby={d}
                aria-invalid={fileError ? true : undefined}
                onChange={(e) => {
                  setFile(e.target.files?.[0] ?? null);
                  setError(undefined);
                }}
              />
            )}
          </Field>
        </div>
        <Field label="Nazwa nowej wersji (opcjonalnie)" hint="Domyślnie: nazwa pliku">
          {(id, d) => (
            <input id={id} aria-describedby={d} value={name} maxLength={80} onChange={(e) => setName(e.target.value)} />
          )}
        </Field>
      </div>
      <div className="form-actions" style={{ marginTop: 0 }}>
        <button type="submit" className="btn btn-primary" disabled={busy}>
          {busy ? 'Importuję…' : 'Importuj CV'}
        </button>
      </div>
      {error && <ErrorBox message={error} />}
      <LiveMessage message={message} />
      {result && (
        <div className="stack">
          <Alert tone="success">
            <span>
              Nowa wersja „{result.cv.name}” jest na liście wersji CV powyżej — wybierz ją, aby przejrzeć i poprawić tekst.
              Układ wielokolumnowy i tabele mogą zmienić kolejność linii.
            </span>
          </Alert>
          {result.warnings.length > 0 && (
            <div className="alert alert-warning" role="note">
              <ul style={{ margin: 0 }}>
                {result.warnings.map((w) => (
                  <li key={w}>{w}</li>
                ))}
              </ul>
            </div>
          )}
          <details>
            <summary className="label">Podgląd odczytanego tekstu</summary>
            <pre className="profile-preview">
              {result.cv.content.length > PREVIEW_CHARS ? `${result.cv.content.slice(0, PREVIEW_CHARS)}…` : result.cv.content}
            </pre>
          </details>
        </div>
      )}
    </form>
  );
}
