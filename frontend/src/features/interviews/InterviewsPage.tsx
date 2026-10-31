import { useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { APPLICATION_STATUS, INTERVIEW_KIND, formatDateTime } from '../../api/labels';
import { useDocumentTitle } from '../../hooks/useDocumentTitle';
import { useAsync } from '../../hooks/useAsync';
import { Alert, Badge, Card, EmptyState, ErrorBox, Loading, PageHeader } from '../../components/ui';
import { interviewApi } from './api';
import type { StarStory } from './types';
import { INTERVIEW_TABS, INTERVIEW_TAB_LABEL, composeStoryAnswer, parseInterviewTab, type InterviewTab } from './interviewUtils';
import { InterviewTabs, panelId, tabId } from './InterviewTabs';
import { PracticeTab } from './PracticeTab';
import { StoriesTab } from './StoriesTab';
import { DebriefTab } from './DebriefTab';
import { AskedQuestionsTab } from './AskedQuestionsTab';
import './interviews.css';

/** Przygotowanie do rozmów: nadchodzące rozmowy, trening STAR (#54), bank historii (#56), debriefy (#62). */
export function InterviewsPage() {
  useDocumentTitle('Rozmowy');
  const [params, setParams] = useSearchParams();
  const tab = parseInterviewTab(params.get('tab'));
  const question = params.get('question') ?? '';
  const offerParam = Number(params.get('offerId'));
  const offerId = Number.isFinite(offerParam) && offerParam > 0 ? offerParam : null;
  const [draft, setDraft] = useState<{ text: string; question: string; version: number }>({ text: '', question: '', version: 0 });
  const insights = useAsync(() => interviewApi.insights(), []);

  function select(next: InterviewTab) {
    const p = new URLSearchParams(params);
    p.set('tab', next);
    setParams(p, { replace: true });
  }

  function practiceStory(story: StarStory) {
    const q = `Opowiedz o sytuacji: ${story.title}`;
    setDraft((d) => ({ text: composeStoryAnswer(story), question: q, version: d.version + 1 }));
    setParams(new URLSearchParams({ tab: 'trening', question: q }));
  }

  const data = insights.data;
  const draftForQuestion = draft.question === question ? draft : null;

  return (
    <>
      <PageHeader title="Rozmowy" subtitle="Przygotuj się do rozmów, ćwicz odpowiedzi metodą STAR i wyciągaj wnioski po każdej rozmowie." />

      <Card title="Nadchodzące rozmowy">
        {insights.loading && !data && <Loading />}
        {insights.error && <ErrorBox message={insights.error} onRetry={insights.reload} />}
        {data && data.upcoming.length === 0 && (
          <EmptyState title="Brak zaplanowanych rozmów">Rozmowę dodasz w szczegółach aplikacji, gdy firma zaprosi Cię na spotkanie.</EmptyState>
        )}
        {data && data.upcoming.length > 0 && (
          <ul className="list-plain iv-upcoming">
            {data.upcoming.map((i) => (
              <li key={i.interviewId}>
                <div>
                  <strong>{formatDateTime(i.scheduledAt)}</strong>
                  {i.kind && <> <Badge tone="info">{INTERVIEW_KIND[i.kind]}</Badge></>}
                  <div>{i.offerTitle ?? 'Rozmowa'}{i.company ? ` · ${i.company}` : ''}</div>
                  <div className="small muted">
                    {[i.durationMinutes ? `${i.durationMinutes} min` : null, i.interviewers, i.location].filter(Boolean).join(' · ')}
                  </div>
                </div>
                <div className="actions">
                  <Link className="btn btn-small" to={`/aplikacje/${i.applicationId}`}>
                    Aplikacja{i.applicationStatus ? ` (${APPLICATION_STATUS[i.applicationStatus]})` : ''}
                    <span className="sr-only">: {i.offerTitle}</span>
                  </Link>
                  {i.offerId && (
                    <Link className="btn btn-small" to={`/oferty/${i.offerId}`}>
                      Pytania do oferty<span className="sr-only">: {i.offerTitle}</span>
                    </Link>
                  )}
                </div>
              </li>
            ))}
          </ul>
        )}
        {data && data.pendingDebriefs.length > 0 && (
          <Alert tone="warning">
            <span><span aria-hidden="true">⚠ </span>{data.pendingDebriefs.length === 1 ? 'Jedna rozmowa czeka' : `${data.pendingDebriefs.length} rozmowy czekają`} na podsumowanie.</span>
            <button type="button" className="btn btn-small" onClick={() => select('podsumowania')}>Podsumuj teraz</button>
          </Alert>
        )}
      </Card>

      <div style={{ marginTop: '1rem' }}>
        <InterviewTabs tabs={INTERVIEW_TABS} labels={INTERVIEW_TAB_LABEL} active={tab} onChange={select} prefix="iv" label="Przygotowanie do rozmów" />
        <div role="tabpanel" id={panelId('iv', tab)} aria-labelledby={tabId('iv', tab)}>
          {tab === 'trening' && (
            <PracticeTab
              key={`${question}|${offerId ?? ''}|${draftForQuestion?.version ?? 0}`}
              initialQuestion={question}
              initialOfferId={offerId}
              initialAnswer={draftForQuestion?.text ?? ''}
            />
          )}
          {tab === 'historie' && <StoriesTab onPractice={practiceStory} />}
          {tab === 'podsumowania' && (data ? <DebriefTab insights={data} onChanged={insights.reload} />
            : insights.error ? <ErrorBox message={insights.error} onRetry={insights.reload} /> : <Loading />)}
          {tab === 'pytania' && (data ? <AskedQuestionsTab questions={data.askedQuestions} />
            : insights.error ? <ErrorBox message={insights.error} onRetry={insights.reload} /> : <Loading />)}
        </div>
      </div>
    </>
  );
}
