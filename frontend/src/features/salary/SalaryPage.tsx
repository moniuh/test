import { useDocumentTitle } from '../../hooks/useDocumentTitle';
import { PageHeader } from '../../components/ui';

/** Narzędzia płacowe (wypełnia moduł „wynagrodzenia”). */
export function SalaryPage() {
  useDocumentTitle('Wynagrodzenia');
  return <PageHeader title="Wynagrodzenia" subtitle="W przygotowaniu." />;
}
