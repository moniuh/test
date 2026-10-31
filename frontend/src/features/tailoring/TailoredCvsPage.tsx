import { useDocumentTitle } from '../../hooks/useDocumentTitle';
import { PageHeader } from '../../components/ui';

/** Biblioteka dopasowanych CV (wypełnia moduł „tailoring”). */
export function TailoredCvsPage() {
  useDocumentTitle('Tailoring CV');
  return <PageHeader title="Tailoring CV" subtitle="W przygotowaniu." />;
}
