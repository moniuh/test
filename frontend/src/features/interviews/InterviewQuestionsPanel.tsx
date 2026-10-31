import { Link } from 'react-router-dom';
import { useAsync } from '../../hooks/useAsync';
import { Badge, Card, EmptyState, ErrorBox, Loading } from '../../components/ui';
import { interviewApi } from './api';
import { CATEGORY_LABEL, CATEGORY_SHORT, CATEGORY_TONE, groupByCategory, practiceUrl } from './interviewUtils';
import './interviews.css';

/** Gniazdo modułu „rozmowy”: pytania rekrutacyjne wyprowadzone z oferty (#53) i pasujące historie STAR (#56). */
export function InterviewQuestionsPanel({ offerId, cvId }: { offerId: number; cvId: number | null }) {
  const questions = useAsync(() => interviewApi.questions(offerId, cvId), [offerId, cvId]);
  const stories = useAsync(() => interviewApi.suggestStories(offerId), [offerId]);
  const groups = questions.data ? groupByCategory(questions.data) : [];

  return (
    <Card title="Pytania, które mogą paść">
      <p className="small muted">
        Wyprowadzone z wymagań oferty i porównania z CV — najpierw luki, o które rekruter zapyta najpewniej.
        Lista jest liczona regułami, bez AI.
      </p>
      {questions.loading && !questions.data && <Loading />}
      {questions.error && <ErrorBox message={questions.error} onRetry={questions.reload} />}
      {questions.data && questions.data.length === 0 && (
        <EmptyState title="Brak pytań">Dodaj wymagania do oferty, aby przygotować pytania.</EmptyState>
      )}
      {groups.map((g) => (
        <section key={g.category} className="iv-group" aria-labelledby={`iv-q-${offerId}-${g.category}`}>
          <h3 id={`iv-q-${offerId}-${g.category}`}>
            {CATEGORY_LABEL[g.category]} <span className="muted small">({g.items.length})</span>
          </h3>
          <ul className="list-plain">
            {g.items.map((q) => (
              <li key={q.question} className="iv-question">
                <div className="spread iv-question-head">
                  <strong>{q.question}</strong>
                  <span className="row iv-question-meta">
                    <Badge tone={CATEGORY_TONE[q.category]}>{CATEGORY_SHORT[q.category]}</Badge>
                    {q.skill && <Badge>{q.skill}</Badge>}
                  </span>
                </div>
                <p className="small muted iv-why">Skąd to pytanie: {q.why}</p>
                {q.evidence && (
                  <blockquote className="evidence">
                    <span className="sr-only">Cytat z CV: </span>„{q.evidence}”
                  </blockquote>
                )}
                <div className="row iv-question-actions">
                  <details className="small">
                    <summary>Jak się przygotować</summary>
                    <p>{q.tip}</p>
                  </details>
                  <Link className="btn btn-small" to={practiceUrl(q.question, offerId)}>
                    Ćwicz odpowiedź<span className="sr-only">: {q.question}</span>
                  </Link>
                </div>
              </li>
            ))}
          </ul>
        </section>
      ))}
      {stories.data && stories.data.suggestions.length > 0 && (
        <section className="iv-group" aria-labelledby={`iv-stories-${offerId}`}>
          <h3 id={`iv-stories-${offerId}`}>Twoje historie STAR do tej oferty</h3>
          <ul className="list-plain small">
            {stories.data.suggestions.slice(0, 3).map((s) => (
              <li key={s.story.id}>
                <strong>{s.story.title}</strong> — pokazuje: {s.matchedSkills.join(', ')}
              </li>
            ))}
          </ul>
          <p className="small"><Link to="/rozmowy?tab=historie">Bank historii STAR</Link></p>
        </section>
      )}
    </Card>
  );
}
