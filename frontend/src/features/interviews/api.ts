import { api } from '../../api/client';
import type { Interview } from '../../api/types';
import type {
  AssessRequest, DebriefRequest, Insights, InterviewQuestion, PracticeAnswer, StarAssessment, StarStory,
  StarStoryRequest, StorySuggestions,
} from './types';

/** Wywołania API modułu „rozmowy”. */
export const interviewApi = {
  questions: (offerId: number, cvId: number | null) =>
    api.get<InterviewQuestion[]>(`/api/offers/${offerId}/interview-questions${cvId ? `?cvId=${cvId}` : ''}`),
  assess: (req: AssessRequest) => api.post<StarAssessment>('/api/interview-practice/assess', req),
  history: () => api.get<PracticeAnswer[]>('/api/interview-practice'),
  deleteAttempt: (id: number) => api.del(`/api/interview-practice/${id}`),
  stories: () => api.get<StarStory[]>('/api/star-stories'),
  createStory: (req: StarStoryRequest) => api.post<StarStory>('/api/star-stories', req),
  updateStory: (id: number, req: StarStoryRequest) => api.put<StarStory>(`/api/star-stories/${id}`, req),
  deleteStory: (id: number) => api.del(`/api/star-stories/${id}`),
  suggestStories: (offerId: number) => api.get<StorySuggestions>(`/api/star-stories/suggest?offerId=${offerId}`),
  insights: () => api.get<Insights>('/api/interview-insights'),
  interview: (id: number) => api.get<Interview>(`/api/interviews/${id}`),
  debrief: (id: number, req: DebriefRequest) => api.put<Interview>(`/api/interviews/${id}/debrief`, req),
};
