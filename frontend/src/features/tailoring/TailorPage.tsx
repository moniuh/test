import { useDocumentTitle } from '../../hooks/useDocumentTitle';
import { PageHeader } from '../../components/ui';

/** Generator dopasowanego CV pod ofertę `:offerId` (wypełnia moduł „tailoring”). */
export function TailorPage() {
  useDocumentTitle('Dopasuj CV');
  return <PageHeader title="Dopasuj CV do oferty" subtitle="W przygotowaniu." />;
}
