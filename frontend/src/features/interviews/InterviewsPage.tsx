import { useDocumentTitle } from '../../hooks/useDocumentTitle';
import { PageHeader } from '../../components/ui';

/** Przygotowanie do rozmów (wypełnia moduł „rozmowy”). */
export function InterviewsPage() {
  useDocumentTitle('Rozmowy');
  return <PageHeader title="Rozmowy" subtitle="W przygotowaniu." />;
}
