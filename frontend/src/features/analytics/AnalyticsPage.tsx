import { useDocumentTitle } from '../../hooks/useDocumentTitle';
import { PageHeader } from '../../components/ui';

/** Analityka (wypełnia moduł „analityka”). */
export function AnalyticsPage() {
  useDocumentTitle('Analityka');
  return <PageHeader title="Analityka" subtitle="W przygotowaniu." />;
}
