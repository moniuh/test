import { useEffect, useId, useState, type FormEvent } from 'react';
import { errorMessage } from '../../api/client';
import { Card, ErrorBox, Field, LiveMessage, Loading } from '../../components/ui';
import { useAsync } from '../../hooks/useAsync';
import { analyticsApi } from './api';
import { ENERGY_LABELS, MOOD_ICONS, MOOD_LABELS } from './logic';
import { MoodWarningAlert } from './MoodWarningAlert';
import type { MoodWarning } from './types';

/** Grupa radio 1–5 z etykietami słownymi (emoji tylko dekoracyjnie). */
function Scale({ legend, name, labels, icons, value, onChange }: {
  legend: string;
  name: string;
  labels: readonly string[];
  icons?: readonly string[];
  value: number | null;
  onChange: (v: number) => void;
}) {
  return (
    <fieldset>
      <legend>{legend}</legend>
      <div className="analytics-scale">
        {labels.map((label, i) => (
          <label key={label}>
            <input type="radio" name={name} value={i + 1} checked={value === i + 1} onChange={() => onChange(i + 1)} />
            {icons && <span aria-hidden="true">{icons[i]}</span>}
            {label}
          </label>
        ))}
      </div>
    </fieldset>
  );
}

/** Szybki wpis nastroju na pulpicie (#76): jeden wpis na dzień, ponowny zapis nadpisuje. */
export function MoodQuickEntry({ onSaved }: { onSaved?: () => void }) {
  const overview = useAsync(() => analyticsApi.mood(7), []);
  const name = useId();
  const [mood, setMood] = useState<number | null>(null);
  const [energy, setEnergy] = useState<number | null>(null);
  const [note, setNote] = useState('');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string>();
  const [message, setMessage] = useState<string>();
  const [warning, setWarning] = useState<MoodWarning | null>(null);

  // wypełnij formularz dzisiejszym wpisem, jeśli już istnieje
  useEffect(() => {
    const today = overview.data?.today;
    if (today) {
      setMood(today.mood);
      setEnergy(today.energy);
      setNote(today.note ?? '');
    }
    setWarning(overview.data?.warning ?? null);
  }, [overview.data]);

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (mood == null || energy == null) {
      setError('Wybierz nastrój i poziom energii.');
      return;
    }
    setSaving(true);
    setError(undefined);
    setMessage(undefined);
    try {
      const saved = await analyticsApi.saveMood({ mood, energy, note: note.trim() || null });
      setWarning(saved.warning);
      setMessage('Zapisano dzisiejszy wpis.');
      onSaved?.();
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setSaving(false);
    }
  }

  return (
    <Card title="Jak się dziś czujesz?" className="analytics-mood">
      {overview.loading && !overview.data && <Loading />}
      {overview.error && <ErrorBox message={overview.error} onRetry={overview.reload} />}
      {overview.data && (
        <form onSubmit={submit}>
          <p className="small muted">
            {overview.data.today
              ? 'Masz już dzisiejszy wpis — możesz go zmienić.'
              : 'Krótki wpis pomaga zauważyć spadek sił, zanim zmęczenie weźmie górę. Widzisz go tylko Ty.'}
          </p>
          <Scale legend="Nastrój" name={`${name}-mood`} labels={MOOD_LABELS} icons={MOOD_ICONS} value={mood} onChange={setMood} />
          <Scale legend="Energia" name={`${name}-energy`} labels={ENERGY_LABELS} value={energy} onChange={setEnergy} />
          <Field label="Notatka (opcjonalnie)">
            {(id) => (
              <textarea id={id} value={note} maxLength={1000} onChange={(e) => setNote(e.target.value)}
                placeholder="Np. dobra rozmowa z rekruterką, mało snu…" />
            )}
          </Field>
          {error && <ErrorBox message={error} />}
          <div className="form-actions">
            <button type="submit" className="btn btn-primary" disabled={saving}>
              {saving ? 'Zapisywanie…' : 'Zapisz wpis'}
            </button>
          </div>
          <LiveMessage message={message} />
        </form>
      )}
      {warning && <MoodWarningAlert warning={warning} />}
    </Card>
  );
}
