import { useDocumentTitle } from '../../hooks/useDocumentTitle';
import { PageHeader } from '../../components/ui';

/** Agregator ofert: skrzynka nowych ofert, źródła, wyszukiwarka (wypełnia moduł „agregator”). */
export function AggregatorPage() {
  useDocumentTitle('Agregator');
  return <PageHeader title="Agregator ofert" subtitle="W przygotowaniu." />;
}
