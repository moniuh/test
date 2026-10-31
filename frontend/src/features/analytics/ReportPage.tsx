import { useDocumentTitle } from '../../hooks/useDocumentTitle';
import { PageHeader } from '../../components/ui';

/** Raport aktywności (wypełnia moduł „analityka”). */
export function ReportPage() {
  useDocumentTitle('Raport aktywności');
  return <PageHeader title="Raport aktywności" subtitle="W przygotowaniu." />;
}
