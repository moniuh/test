import { useState } from 'react';
import { Link } from 'react-router-dom';
import { formatDate } from '../../api/labels';
import { Card, EmptyState, Field } from '../../components/ui';
import type { AskedQuestion } from './types';
import { matchesFilter, practiceUrl } from './interviewUtils';

/** #62 Bank realnie zadanych pytań z podsumowań rozmów — z przejściem do treningu. */
export function AskedQuestionsTab({ questions }: { questions: AskedQuestion[] }) {
  const [filter, setFilter] = useState('');
  const visible = questions.filter((q) =>
    matchesFilter([q.question, q.offerTitle ?? '', q.company ?? '', ...q.skills].join(' '), filter));

  return (
    <Card title="Pytania z Twoich rozmów">
      <p className="small muted">Zbieramy je z podsumowań — to najlepszy materiał do ćwiczeń przed kolejną rozmową.</p>
      <Field label="Szukaj w pytaniach" hint="Treść, firma lub umiejętność">
        {(id, describedBy) => (
          <input id={id} type="search" aria-describedby={describedBy} value={filter} onChange={(e) => setFilter(e.target.value)} />
        )}
      </Field>
      <p className="small" role="status" aria-live="polite">Pytań: {visible.length} z {questions.length}</p>
      {questions.length === 0 ? (
        <EmptyState title="Brak zapisanych pytań">Po rozmowie zapisz w podsumowaniu, jakie pytania padły.</EmptyState>
      ) : (
        <ul className="list-plain">
          {visible.map((q) => (
            <li key={`${q.interviewId}-${q.question}`}>
              <div className="spread">
                <div>
                  <strong>{q.question}</strong>
                  <div className="small muted">
                    {q.offerTitle ?? 'Rozmowa'}{q.company ? ` · ${q.company}` : ''} · {formatDate(q.date)}
                  </div>
                  {q.skills.length > 0 && (
                    <div className="iv-chips" style={{ marginTop: '0.25rem' }}>
                      {q.skills.map((s) => <span key={s} className="chip">{s}</span>)}
                    </div>
                  )}
                </div>
                <Link className="btn btn-small" to={practiceUrl(q.question, q.offerId)}>
                  Ćwicz<span className="sr-only"> odpowiedź na: {q.question}</span>
                </Link>
              </div>
            </li>
          ))}
        </ul>
      )}
    </Card>
  );
}
