import { useState, type FormEvent } from 'react';
import { api, errorMessage } from '../../api/client';
import type { JobOffer } from '../../api/types';
import { useAsync } from '../../hooks/useAsync';
import { Alert, Badge, Card, EmptyState, ErrorBox, Field, LiveMessage, Loading } from '../../components/ui';
import { interviewApi } from './api';
import type { StarStory, StarStoryRequest } from './types';
import { splitSkills } from './interviewUtils';

interface StoryForm {
  title: string;
  situation: string;
  task: string;
  action: string;
  result: string;
  skills: string;
}

const EMPTY: StoryForm = { title: '', situation: '', task: '', action: '', result: '', skills: '' };

const PARTS: { key: keyof Omit<StoryForm, 'title' | 'skills'>; label: string; hint: string }[] = [
  { key: 'situation', label: 'Sytuacja', hint: 'Gdzie i kiedy, jaki był problem lub kontekst' },
  { key: 'task', label: 'Zadanie', hint: 'Za co odpowiadałaś/odpowiadałeś, jaki był cel' },
  { key: 'action', label: 'Działanie', hint: 'Co konkretnie zrobiono — w pierwszej osobie, krok po kroku' },
  { key: 'result', label: 'Rezultat', hint: 'Efekt, najlepiej z liczbami, i wniosek' },
];

/** #56 Bank historii STAR: zapisane raz osiągnięcia, podpowiadane do wymagań ofert. */
export function StoriesTab({ onPractice }: { onPractice: (story: StarStory) => void }) {
  const stories = useAsync(() => interviewApi.stories(), []);
  const offers = useAsync(() => api.get<JobOffer[]>('/api/offers'), []);
  const [form, setForm] = useState<StoryForm>(EMPTY);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [error, setError] = useState<string>();
  const [live, setLive] = useState<string>();
  const [busy, setBusy] = useState(false);
  const [offerId, setOfferId] = useState<number | null>(null);
  const suggestions = useAsync(
    () => (offerId ? interviewApi.suggestStories(offerId) : Promise.resolve(undefined)),
    [offerId, stories.data],
  );

  function edit(s: StarStory) {
    setEditingId(s.id);
    setForm({
      title: s.title, situation: s.situation ?? '', task: s.task ?? '', action: s.action ?? '', result: s.result ?? '',
      skills: s.skills.join(', '),
    });
    setLive(`Edytujesz historię „${s.title}”.`);
    document.getElementById('iv-story-title')?.focus();
  }

  function cancel() {
    setEditingId(null);
    setForm(EMPTY);
  }

  async function save(e: FormEvent) {
    e.preventDefault();
    if (!form.title.trim()) {
      setError('Podaj tytuł historii.');
      return;
    }
    const req: StarStoryRequest = { ...form, skills: splitSkills(form.skills) };
    setBusy(true);
    setError(undefined);
    try {
      if (editingId) await interviewApi.updateStory(editingId, req);
      else await interviewApi.createStory(req);
      setLive(editingId ? 'Zapisano zmiany w historii.' : 'Dodano historię do banku.');
      cancel();
      stories.reload();
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  }

  async function remove(s: StarStory) {
    if (!window.confirm(`Usunąć historię „${s.title}”?`)) return;
    try {
      await interviewApi.deleteStory(s.id);
      setLive('Usunięto historię.');
      if (editingId === s.id) cancel();
      stories.reload();
    } catch (err) {
      setError(errorMessage(err));
    }
  }

  return (
    <div className="stack">
      <div className="grid-2">
        <Card title={editingId ? 'Edytuj historię' : 'Nowa historia STAR'}>
          <form onSubmit={save} noValidate>
            <div className="field">
              <label htmlFor="iv-story-title">Tytuł</label>
              <input id="iv-story-title" value={form.title} placeholder="np. Migracja płatności na Kafkę"
                onChange={(e) => setForm({ ...form, title: e.target.value })} />
            </div>
            {PARTS.map((p) => (
              <Field key={p.key} label={p.label} hint={p.hint}>
                {(id, describedBy) => (
                  <textarea id={id} aria-describedby={describedBy} rows={3} value={form[p.key]}
                    onChange={(e) => setForm({ ...form, [p.key]: e.target.value })} />
                )}
              </Field>
            ))}
            <Field label="Umiejętności" hint="Oddziel przecinkami, np. Kafka, Spring Boot, mentoring — resztę rozpoznamy w treści">
              {(id, describedBy) => (
                <input id={id} aria-describedby={describedBy} value={form.skills}
                  onChange={(e) => setForm({ ...form, skills: e.target.value })} />
              )}
            </Field>
            {error && <ErrorBox message={error} />}
            <div className="form-actions">
              <button type="submit" className="btn btn-primary" disabled={busy}>{editingId ? 'Zapisz zmiany' : 'Dodaj historię'}</button>
              {editingId && <button type="button" className="btn" onClick={cancel}>Anuluj</button>}
            </div>
            <LiveMessage message={live} />
          </form>
        </Card>

        <Card title="Historie pasujące do oferty">
          <Field label="Oferta">
            {(id) => (
              <select id={id} value={offerId ?? ''} onChange={(e) => setOfferId(e.target.value ? Number(e.target.value) : null)}>
                <option value="">— wybierz ofertę —</option>
                {offers.data?.map((o) => <option key={o.id} value={o.id}>{o.title}{o.company ? ` · ${o.company}` : ''}</option>)}
              </select>
            )}
          </Field>
          {offerId && suggestions.loading && !suggestions.data && <Loading />}
          {suggestions.error && <ErrorBox message={suggestions.error} onRetry={suggestions.reload} />}
          {offerId && suggestions.data && (
            <div role="status" aria-live="polite">
              {suggestions.data.suggestions.length === 0 ? (
                <p className="muted small">Żadna historia nie pokrywa wymagań tej oferty.</p>
              ) : (
                <ol className="small">
                  {suggestions.data.suggestions.map((s) => (
                    <li key={s.story.id}>
                      <strong>{s.story.title}</strong> <Badge tone="success">{s.score} pkt</Badge>
                      <div>Wspólne umiejętności: {s.matchedSkills.join(', ')}</div>
                      <div className="muted">Wymagania: {s.requirements.map((r) => `„${r}”`).join('; ')}</div>
                    </li>
                  ))}
                </ol>
              )}
              {suggestions.data.uncoveredSkills.length > 0 && (
                <Alert tone="warning">
                  <span><span aria-hidden="true">⚠ </span>Brakuje historii dla wymagań obowiązkowych: {suggestions.data.uncoveredSkills.join(', ')}.</span>
                </Alert>
              )}
              <p className="small muted">Punkty: wymaganie obowiązkowe 3, mile widziane 1 (wspólne umiejętności ze słownika).</p>
            </div>
          )}
        </Card>
      </div>

      <Card title={`Twoje historie${stories.data ? ` (${stories.data.length})` : ''}`}>
        {stories.loading && !stories.data && <Loading />}
        {stories.error && <ErrorBox message={stories.error} onRetry={stories.reload} />}
        {stories.data && stories.data.length === 0 && (
          <EmptyState title="Bank historii jest pusty">
            Zapisz 3–5 historii o sukcesach, konfliktach, błędach i nauce — przydadzą się do wielu pytań.
          </EmptyState>
        )}
        {stories.data?.map((s) => (
          <article key={s.id} className="iv-story" aria-labelledby={`iv-story-${s.id}`}>
            <div className="spread">
              <h3 id={`iv-story-${s.id}`} style={{ margin: 0 }}>{s.title}</h3>
              <div className="actions">
                <button type="button" className="btn btn-small" onClick={() => onPractice(s)}>
                  Ćwicz z tą historią<span className="sr-only">: {s.title}</span>
                </button>
                <button type="button" className="btn btn-small" onClick={() => edit(s)}>
                  Edytuj<span className="sr-only"> historię {s.title}</span>
                </button>
                <button type="button" className="btn btn-small btn-danger" onClick={() => remove(s)}>
                  Usuń<span className="sr-only"> historię {s.title}</span>
                </button>
              </div>
            </div>
            <dl className="kv small">
              {PARTS.map((p) => (s[p.key] ? (
                <div key={p.key} style={{ display: 'contents' }}>
                  <dt>{p.label}</dt>
                  <dd>{s[p.key]}</dd>
                </div>
              ) : null))}
            </dl>
            <div className="iv-chips">
              {s.skills.map((k) => <span key={k} className="chip">{k}</span>)}
              {s.detectedSkills.map((k) => <span key={k} className="chip muted" title="rozpoznane w treści">{k} <span className="sr-only">(rozpoznane w treści)</span></span>)}
            </div>
          </article>
        ))}
      </Card>
    </div>
  );
}
