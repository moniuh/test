import { Link } from 'react-router-dom';
import { useDocumentTitle } from '../../hooks/useDocumentTitle';
import { Card, PageHeader } from '../../components/ui';

/** Pulpit (wersja bazowa; rozbudowuje moduł „analityka”). */
export function DashboardPage() {
  useDocumentTitle('Pulpit');
  return (
    <>
      <PageHeader title="Pulpit" subtitle="Twoje poszukiwania pracy w jednym miejscu." />
      <div className="grid-3">
        <Card title="Oferty"><Link to="/oferty">Przeglądaj oferty</Link></Card>
        <Card title="Aplikacje"><Link to="/aplikacje">Zobacz aplikacje</Link></Card>
        <Card title="Profil"><Link to="/profil">Uzupełnij CV</Link></Card>
      </div>
    </>
  );
}
