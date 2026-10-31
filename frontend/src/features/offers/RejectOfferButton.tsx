import { useId, useRef, useState, type FormEvent } from 'react';
import { errorMessage } from '../../api/client';
import type { JobOffer, RejectReason } from '../../api/types';
import { REJECT_REASON } from '../../api/labels';
import { ErrorBox } from '../../components/ui';
import { offersApi } from './api';
import './offers.css';

const REASONS = Object.keys(REJECT_REASON) as RejectReason[];
const NOTE_LIMIT = 1000;

/**
 * Odrzucenie oferty z powodem (#24): przycisk otwiera modalne okno `<dialog>` (fokus w oknie, Esc zamyka,
 * po zamknięciu fokus wraca na przycisk). Dla oferty już odrzuconej przycisk jest ukryty.
 */
export function RejectOfferButton({ offer, onChanged }: { offer: JobOffer; onChanged: (offer: JobOffer) => void }) {
  const dialogRef = useRef<HTMLDialogElement>(null);
  const triggerRef = useRef<HTMLButtonElement>(null);
  const firstRadioRef = useRef<HTMLInputElement>(null);
  const [reason, setReason] = useState<RejectReason | ''>('');
  const [note, setNote] = useState('');
  const [error, setError] = useState<string>();
  const [saving, setSaving] = useState(false);
  const [announce, setAnnounce] = useState<string>();
  const titleId = useId();
  const descId = useId();
  const noteId = useId();
  const noteHintId = useId();
  const rejected = offer.status === 'REJECTED';

  function open() {
    setReason(offer.rejectReason ?? '');
    setNote(offer.rejectNote ?? '');
    setError(undefined);
    setAnnounce(undefined);
    dialogRef.current?.showModal();
    firstRadioRef.current?.focus();
  }

  function close() {
    dialogRef.current?.close();
  }

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (!reason) {
      setError('Wybierz powód odrzucenia');
      firstRadioRef.current?.focus();
      return;
    }
    setSaving(true);
    setError(undefined);
    try {
      const updated = await offersApi.reject(offer.id, reason, note);
      setAnnounce(`Oferta odrzucona. Powód: ${REJECT_REASON[reason]}.`);
      close();
      onChanged(updated);
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setSaving(false);
    }
  }

  return (
    <>
      {/* region „status” jest zawsze zamontowany — po odrzuceniu przycisk znika, a komunikat zostaje ogłoszony */}
      <span className="sr-only" role="status" aria-live="polite">
        {announce ?? ''}
      </span>
      {!rejected && (
        <>
          <button ref={triggerRef} type="button" className="btn btn-danger" aria-haspopup="dialog" onClick={open}>
            Odrzuć
          </button>
          <dialog
            ref={dialogRef}
            className="offers-dialog"
            aria-labelledby={titleId}
            aria-describedby={descId}
            onClose={() => triggerRef.current?.focus()}
          >
            <form onSubmit={submit} noValidate>
              <h2 id={titleId}>Odrzuć ofertę</h2>
              <p id={descId} className="muted small">
                „{offer.title}”{offer.company ? ` — ${offer.company}` : ''}. Powód pomoże lepiej dobierać kolejne oferty.
              </p>
              {error && <ErrorBox message={error} />}
              <fieldset>
                <legend>Powód odrzucenia *</legend>
                <div className="offers-radio-list">
                  {REASONS.map((r, i) => (
                    <label key={r}>
                      <input
                        ref={i === 0 ? firstRadioRef : undefined}
                        type="radio"
                        name={`${titleId}-reason`}
                        value={r}
                        checked={reason === r}
                        onChange={() => setReason(r)}
                      />
                      {REJECT_REASON[r]}
                    </label>
                  ))}
                </div>
              </fieldset>
              <div className="field">
                <label htmlFor={noteId}>Notatka (opcjonalnie)</label>
                <textarea
                  id={noteId}
                  value={note}
                  maxLength={NOTE_LIMIT}
                  aria-describedby={noteHintId}
                  onChange={(e) => setNote(e.target.value)}
                  style={{ minHeight: 80 }}
                />
                <small id={noteHintId} className="muted">
                  Np. „za mało pracy zdalnej”. Pozostało znaków: {NOTE_LIMIT - note.length}.
                </small>
              </div>
              <div className="form-actions">
                <button type="submit" className="btn btn-danger" disabled={saving}>
                  {saving ? 'Zapisywanie…' : 'Odrzuć ofertę'}
                </button>
                <button type="button" className="btn" onClick={close}>
                  Anuluj
                </button>
              </div>
            </form>
          </dialog>
        </>
      )}
    </>
  );
}
