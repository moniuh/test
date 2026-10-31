import { useRef, useState, type FormEvent, type RefObject } from 'react';
import { api, errorMessage } from '../../api/client';
import type { JobOffer } from '../../api/types';
import { formatDateTime } from '../../api/labels';
import { useAsync } from '../../hooks/useAsync';
import {
  Badge, Card, EmptyState, ErrorBox, Field, LiveMessage, ScoreRing, SourceBadge, scoreTone,
} from '../../components/ui';
import { interviewApi } from './api';
import type { PracticeAnswer, StarAssessment } from './types';
import { LENGTH_LABEL, PERSON_LABEL, composeStoryAnswer, starParts, wordCount } from './interviewUtils';

/** #54 Trening odpowiedzi: ocena metodą STAR (heurystyki), opcjonalny komentarz AI, historia prób. */
export function PracticeTab({ initialQuestion, initialOfferId, initialAnswer }: {
  initialQuestion: string;
  initialOfferId: number | null;
  initialAnswer: string;
}) {
  const offers = useAsync(() => api.get<JobOffer[]>('/api/offers'), []);
  const stories = useAsync(() => interviewApi.stories(), []);
  const history = useAsync(() => interviewApi.history(), []);
  const [question, setQuestion] = useState(initialQuestion);
  const [offerId, setOfferId] = useState<number | null>(initialOfferId);
  const [answer, setAnswer] = useState(initialAnswer);
  const [storyId, setStoryId] = useState('');
  const [result, setResult] = useState<StarAssessment>();
  const [error, setError] = useState<string>();
  const [busy, setBusy] = useState(false);
  const [live, setLive] = useState<string>();
  const resultRef = useRef<HTMLHeadingElement>(null);

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (!question.trim() || !answer.trim()) {
      setError('Wpisz pytanie i swoją odpowiedź.');
      return;
    }
    setBusy(true);
    setError(undefined);
    try {
      const r = await interviewApi.assess({ question, answer, offerId });
      setResult(r);
      setLive(`Ocena odpowiedzi: ${r.score} na 100.`);
      history.reload();
      requestAnimationFrame(() => resultRef.current?.focus());
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  }

  function insertStory() {
    const story = stories.data?.find((s) => String(s.id) === storyId);
    if (!story) return;
    const draft = composeStoryAnswer(story);
    setAnswer((a) => (a.trim() ? `${a.trim()}\n\n${draft}` : draft));
    setLive(`Wstawiono historię „${story.title}”. Dopasuj ją do pytania.`);
  }

  function load(p: PracticeAnswer) {
    setQuestion(p.question);
    setAnswer(p.answer);
    setOfferId(p.offerId);
    setLive('Wczytano próbę do formularza.');
  }

  async function remove(p: PracticeAnswer) {
    if (!window.confirm('Usunąć tę próbę z historii?')) return;
    try {
      await interviewApi.deleteAttempt(p.id);
      history.reload();
      setLive('Usunięto próbę.');
    } catch (err) {
      setError(errorMessage(err));
    }
  }

  const words = wordCount(answer);

  return (
    <div className="stack">
      <Card title="Trening odpowiedzi">
        <p className="small muted">
          Odpowiedz tak, jak na rozmowie: sytuacja, zadanie, Twoje działania i mierzalny rezultat (STAR).
          Wynik liczą reguły w aplikacji; komentarz AI pojawia się tylko, gdy AI jest włączone.
        </p>
        <form onSubmit={submit} noValidate>
          <Field label="Pytanie">
            {(id) => <input id={id} value={question} onChange={(e) => setQuestion(e.target.value)} />}
          </Field>
          <div className="grid-2">
            <Field label="Oferta (opcjonalnie)" hint="Sprawdzimy, czy odpowiedź nawiązuje do wymagań oferty">
              {(id, describedBy) => (
                <select id={id} aria-describedby={describedBy} value={offerId ?? ''}
                  onChange={(e) => setOfferId(e.target.value ? Number(e.target.value) : null)}>
                  <option value="">— bez oferty —</option>
                  {offers.data?.map((o) => (
                    <option key={o.id} value={o.id}>{o.title}{o.company ? ` · ${o.company}` : ''}</option>
                  ))}
                </select>
              )}
            </Field>
            <div>
              <Field label="Wstaw historię z banku STAR">
                {(id) => (
                  <select id={id} value={storyId} onChange={(e) => setStoryId(e.target.value)}
                    disabled={!stories.data || stories.data.length === 0}>
                    <option value="">{stories.data && stories.data.length === 0 ? '— bank jest pusty —' : '— wybierz historię —'}</option>
                    {stories.data?.map((s) => <option key={s.id} value={s.id}>{s.title}</option>)}
                  </select>
                )}
              </Field>
              <button type="button" className="btn btn-small" onClick={insertStory} disabled={!storyId}>
                Wstaw do odpowiedzi
              </button>
            </div>
          </div>
          <Field label="Twoja odpowiedź" hint={`${words} słów · celuj w 150–300 słów (1–2 minuty mówienia)`}>
            {(id, describedBy) => (
              <textarea id={id} aria-describedby={describedBy} rows={10} value={answer} onChange={(e) => setAnswer(e.target.value)} />
            )}
          </Field>
          {error && <ErrorBox message={error} />}
          <div className="form-actions">
            <button type="submit" className="btn btn-primary" disabled={busy}>{busy ? 'Oceniam…' : 'Oceń odpowiedź'}</button>
          </div>
          <LiveMessage message={live} />
        </form>
      </Card>

      {result && <ResultView result={result} headingRef={resultRef} />}

      <Card title="Historia prób">
        {history.error && <ErrorBox message={history.error} onRetry={history.reload} />}
        {history.data && history.data.length === 0 && (
          <EmptyState title="Brak zapisanych prób">Oceń pierwszą odpowiedź — zapiszemy ją tutaj, żeby widać było postęp.</EmptyState>
        )}
        {history.data && history.data.length > 0 && (
          <ul className="list-plain">
            {history.data.map((p) => (
              <li key={p.id}>
                <div className="spread">
                  <div>
                    <Badge tone={scoreTone(p.score)}>{p.score}/100</Badge> <strong>{p.question}</strong>
                    <div className="small muted">
                      {formatDateTime(p.createdAt)} · {p.wordCount} słów · STAR:{' '}
                      {[['S', p.situation], ['T', p.task], ['A', p.action], ['R', p.result]]
                        .map(([l, ok]) => `${l}${ok ? '✓' : '✗'}`).join(' ')}
                      {p.hasNumbers ? ' · z liczbami' : ''}
                    </div>
                  </div>
                  <div className="actions">
                    <button type="button" className="btn btn-small" onClick={() => load(p)}>
                      Wczytaj<span className="sr-only"> próbę: {p.question}</span>
                    </button>
                    <button type="button" className="btn btn-small btn-danger" onClick={() => remove(p)}>
                      Usuń<span className="sr-only"> próbę: {p.question}</span>
                    </button>
                  </div>
                </div>
              </li>
            ))}
          </ul>
        )}
      </Card>
    </div>
  );
}

function ResultView({ result, headingRef }: { result: StarAssessment; headingRef: RefObject<HTMLHeadingElement | null> }) {
  const person = result.firstPersonSingularVsPlural;
  return (
    <section className="card" aria-labelledby="iv-result-title">
      <div className="card-header">
        <h2 id="iv-result-title" ref={headingRef} tabIndex={-1}>Ocena odpowiedzi</h2>
        <SourceBadge source={result.source} />
      </div>
      <div className="iv-score">
        <ScoreRing score={result.score} size={84} label="Ocena STAR" />
        <div className="small">
          <div>{result.wordCount} słów — {LENGTH_LABEL[result.length]} (ok. {String(result.speakingMinutes).replace('.', ',')} min mówienia)</div>
          <div>Wynik liczą reguły aplikacji; AI nie zmienia liczby punktów.</div>
        </div>
      </div>

      <div className="iv-star">
        {starParts(result).map((p) => (
          <div key={p.key} className="iv-star-part">
            <h3 className="row" style={{ gap: '0.35rem' }}>
              <span><span className="iv-star-letter" aria-hidden="true">{p.letter}</span>{p.label}</span>
              <Badge tone={p.tone}>{p.status}</Badge>
            </h3>
            {p.part.evidence ? (
              <blockquote className={`evidence ${p.part.confidence < 1 ? 'related' : ''}`}>
                <span className="sr-only">Fragment Twojej odpowiedzi: </span>„{p.part.evidence}”
              </blockquote>
            ) : (
              <p className="small muted" style={{ margin: 0 }}>Nie znaleziono w odpowiedzi.</p>
            )}
            {p.part.markers.length > 0 && (
              <p className="small muted" style={{ margin: '0.3rem 0 0' }}>Wskaźniki: {p.part.markers.join(', ')}</p>
            )}
          </div>
        ))}
      </div>

      <dl className="kv small iv-metrics">
        <dt>Liczby i miary</dt>
        <dd>{result.hasNumbers ? `tak — ${result.numbers.map((n) => `„${n}”`).join(', ')}` : 'brak'}</dd>
        <dt>„Ja” vs „my”</dt>
        <dd>{PERSON_LABEL[person.verdict]} („ja”: {person.singular}, „my”: {person.plural})</dd>
        <dt>Słowa-wypełniacze</dt>
        <dd>{result.fillerTotal === 0 ? 'brak' : result.fillerWords.map((f) => `„${f.word}” ×${f.count}`).join(', ')}</dd>
        {(result.offerSkillsMentioned.length > 0 || result.offerSkillsMissing.length > 0) && (
          <>
            <dt>Wymagania oferty</dt>
            <dd>
              {result.offerSkillsMentioned.length > 0 ? `nawiązujesz do: ${result.offerSkillsMentioned.join(', ')}` : 'brak nawiązań'}
              {result.offerSkillsMissing.length > 0 && ` · nie wspominasz: ${result.offerSkillsMissing.join(', ')}`}
            </dd>
          </>
        )}
      </dl>

      <h3 style={{ marginTop: '1rem' }}>Wskazówki</h3>
      <ul>{result.feedback.map((f) => <li key={f}>{f}</li>)}</ul>

      {result.aiComment ? (
        <div className="alert alert-info iv-ai" role="status">
          <div>
            <p className="label" style={{ margin: '0 0 0.3rem' }}>Komentarz trenera AI <SourceBadge source="ai" /></p>
            {result.aiComment}
          </div>
        </div>
      ) : (
        <p className="small muted">Komentarz AI niedostępny — ocena i wskazówki pochodzą z reguł offline.</p>
      )}

      <details>
        <summary className="label">Jak policzono wynik</summary>
        <div className="table-wrap">
          <table>
            <caption className="sr-only">Punkty za elementy odpowiedzi</caption>
            <thead>
              <tr><th scope="col">Element</th><th scope="col" className="right">Punkty</th><th scope="col" className="right">Maks.</th></tr>
            </thead>
            <tbody>
              {result.scoreBreakdown.map((s) => (
                <tr key={s.label}>
                  <th scope="row">{s.label}</th>
                  <td className="right">{Math.round(s.points * 10) / 10}</td>
                  <td className="right">{s.max || '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </details>
    </section>
  );
}
